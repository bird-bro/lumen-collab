package org.birdbro.lumen.core;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.core.entity.ExamExecution;
import org.birdbro.lumen.core.entity.ExamOrder;
import org.birdbro.lumen.core.entity.ExamOrderStatus;
import org.birdbro.lumen.core.entity.ExamReport;
import org.birdbro.lumen.core.entity.ExamSlotConfig;
import org.birdbro.lumen.core.entity.PatientSnapshot;
import org.birdbro.lumen.core.entity.SlotWindow;
import org.birdbro.lumen.core.entity.SlotWindowBounds;
import org.birdbro.lumen.core.mapper.ExamOrderMapper;
import org.birdbro.lumen.core.mapper.ExamSlotConfigMapper;
import org.birdbro.lumen.core.patient.PatientIdentity;
import org.birdbro.lumen.core.service.ExamOrderRegistrationService;
import org.birdbro.lumen.core.service.ExamSlotCapacityService;
import org.birdbro.lumen.core.service.support.PreRegistrationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The composed pre-registration create flow (kernel-pre-registration task 2.6; spec
 * exam-workflow-kernel "Pre-Registration Support" - the occupied-slot, live-count,
 * and past-refusal scenarios; design D1/D2/D4/D7). {@code composedCreate} is the
 * REFERENCE composition proposal ② builds: DB-server now → walk-in derive (NULL) /
 * config row FOR UPDATE → resolveSlotWindow → caller-side live count →
 * validatePreregistration (locked re-check) → derive BOOKED → registerOrder - all
 * inside the caller's ONE transaction.
 *
 * <p>Each behavior was RED-first at its component task (1.2 schema, 2.1 policy,
 * 2.3/2.4 capacity refusals); this suite is the integration credential, plus the
 * D7 lock-order probe the components could not demonstrate: while the create
 * transaction holds the config row lock, a second connection's FOR UPDATE times
 * out - same-slot creates serialize behind the ONE cheap lock.</p>
 *
 * <p>Fixture contract: {@code @Transactional} rollback for the create tests; the
 * lock probe needs a COMMITTED config row (a second connection must see it), so it
 * seeds outside the transaction and HARD-DELETES in finally (the shared-server
 * sweep ruling - DELETE, never soft). Counts are scoped by the run-unique
 * {@code patient_uid} so lingering rows can never pollute them.</p>
 *
 * @author birdbro
 */
class PreRegistrationFlowTests extends KernelIntegrationTestBase {

    @Autowired
    private PreRegistrationPolicy policy;

    @Autowired
    private ExamSlotCapacityService capacityService;

    @Autowired
    private ExamOrderRegistrationService registrationService;

    @Autowired
    private ExamOrderMapper examOrderMapper;

    @Autowired
    private ExamSlotConfigMapper examSlotConfigMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    // ---------- helpers ----------

    /** A fixed-future wall clock: 2026-11-24 (tomorrow at the time of writing). */
    private static Date at(int hour, int minute) {
        return Date.from(java.time.LocalDateTime.of(2026, 11, 24, hour, minute)
                .atZone(java.time.ZoneId.systemDefault()).toInstant());
    }

    private static Date expectedBound(int hour, int minute) {
        return at(hour, minute);
    }

    /** Live-unique test modality token (varchar 16 - "TST" + 9 digits). */
    private String uniqueModality() {
        String digits = Long.toString(System.nanoTime());
        return "TST" + digits.substring(digits.length() - 9);
    }

    /** Run-unique patient reference - scopes every count assertion. */
    private int uniquePatientUid() {
        return 990_000 + (int) (Math.abs(System.nanoTime()) % 100_000);
    }

    private ExamSlotConfig seedConfig(String modality, int capacity) {
        ExamSlotConfig config = ExamSlotConfig.builder()
                .modality(modality)
                .granularityMinutes(30)
                .slotCapacity(capacity)
                .openWindows(Collections.singletonList(
                        SlotWindow.builder().start("08:00").end("12:00").build()))
                .build();
        config.setDeleted(0);
        examSlotConfigMapper.insert(config);
        return config;
    }

    /** The caller's live count (design D2 - the department-side SELECT count(*) stand-in). */
    private int countBooked(SlotWindowBounds bounds, int patientUid) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM exam_order WHERE status = 'BOOKED'"
                + " AND booking_time >= ? AND booking_time < ? AND deleted = 0"
                + " AND patient_uid = ?",
                Integer.class, bounds.getWindowStart(), bounds.getWindowEnd(), patientUid);
    }

    private PatientIdentity identity(int patientUid) {
        return PatientIdentity.builder()
                .patUid(patientUid)
                .patId("P" + patientUid)
                .snapshot(PatientSnapshot.builder()
                        .patId("P" + patientUid).name("预登记受试者").build())
                .build();
    }

    private ExamOrder draftOrder(int patientUid, Date bookingTime) {
        ExamOrder order = new ExamOrder();
        order.setPatientUid(patientUid);
        order.setBookingTime(bookingTime);
        return order;
    }

    /**
     * The composed pre-registration create (the reference composition - see class
     * javadoc): ONE transaction, walk-in derive for NULL, locked config read +
     * resolve + caller count + validate + derive BOOKED otherwise, then the
     * aggregate registration.
     */
    private Integer composedCreate(String modality, ExamOrder order) {
        Date dbNow = examSlotConfigMapper.selectDbNow();
        if (order.getBookingTime() == null) {
            order.setStatus(policy.deriveEntryStatus(null, dbNow));
        } else {
            // D4: refuse the past BEFORE the resolve/count dance (the 12:20-past
            // incident: a past time outside the windows would otherwise die on the
            // WINDOW refusal, not the PAST refusal - the guard runs first)
            policy.requireFutureBooking(order.getBookingTime(), dbNow);
            ExamSlotConfig config = examSlotConfigMapper.selectOne(
                    new LambdaQueryWrapper<ExamSlotConfig>()
                            .eq(ExamSlotConfig::getModality, modality)
                            .last("FOR UPDATE"));
            SlotWindowBounds bounds = capacityService.resolveSlotWindow(config,
                    order.getBookingTime());
            int occupancy = countBooked(bounds, order.getPatientUid());
            capacityService.validatePreregistration(modality, order.getBookingTime(), occupancy);
            order.setStatus(policy.deriveEntryStatus(order.getBookingTime(), dbNow));
        }
        return registrationService.registerOrder(identity(order.getPatientUid()), order,
                ExamExecution.builder().build(), ExamReport.builder().build());
    }

    // ---------- task 2.6 - the composed flow ----------

    @Test
    @Transactional
    void composedPreRegistrationCreatePersistsBookedAndOccupiesTheSlot() {
        String modality = uniqueModality();
        int patientUid = uniquePatientUid();
        seedConfig(modality, 4);

        ExamOrder order = draftOrder(patientUid, at(9, 17));
        Integer orderId = composedCreate(modality, order);

        assertNotNull(orderId, "the composed create persists the aggregate");
        ExamOrder persisted = examOrderMapper.selectById(orderId);
        assertEquals(ExamOrderStatus.BOOKED, persisted.getStatus(),
                "a strictly-future booking_time enters BOOKED (预登记态)");
        assertEquals(at(9, 17), persisted.getBookingTime(),
                "the requested slot anchor round-trips (autoResultMap timestamp)");

        // "Pre-registration below capacity occupies the slot" - the window count grew by one
        SlotWindowBounds bounds = SlotWindowBounds.builder()
                .windowStart(expectedBound(9, 0)).windowEnd(expectedBound(9, 30)).build();
        assertEquals(1, countBooked(bounds, patientUid),
                "the created BOOKED order occupies the resolved slot window");
    }

    @Test
    @Transactional
    void slotCountingUsesLivePreRegisteredOrdersOnly() {
        String modality = uniqueModality();
        int patientUid = uniquePatientUid();
        seedConfig(modality, 4);
        Date bookingTime = at(9, 17);

        // seed the slot to capacity-1 with REAL live BOOKED orders, plus the two
        // shapes that must NOT count: an arrived REGISTERED order carrying the same
        // booking_time, and a walk-in BOOKED order with NULL booking_time
        for (int i = 0; i < 3; i++) {
            ExamOrder booked = draftOrder(patientUid, bookingTime);
            booked.setStatus(ExamOrderStatus.BOOKED);
            booked.setAccessionNo("AC-SEED-" + Long.toUnsignedString(System.nanoTime())
                    + "-" + i);
            examOrderMapper.insert(booked);
        }
        ExamOrder arrived = draftOrder(patientUid, bookingTime);
        arrived.setStatus(ExamOrderStatus.REGISTERED);
        arrived.setAccessionNo("AC-SEED-" + Long.toUnsignedString(System.nanoTime()) + "-R");
        examOrderMapper.insert(arrived);
        ExamOrder walkIn = draftOrder(patientUid, null);
        walkIn.setStatus(ExamOrderStatus.BOOKED);
        walkIn.setAccessionNo("AC-SEED-" + Long.toUnsignedString(System.nanoTime()) + "-W");
        examOrderMapper.insert(walkIn);

        SlotWindowBounds bounds = SlotWindowBounds.builder()
                .windowStart(expectedBound(9, 0)).windowEnd(expectedBound(9, 30)).build();
        assertEquals(3, countBooked(bounds, patientUid),
                "only live BOOKED orders with the booking_time in window count - "
                + "REGISTERED and NULL-booking orders never occupy capacity");

        // below capacity: the composed create passes and occupies the last seat
        Integer orderId = composedCreate(modality, draftOrder(patientUid, bookingTime));
        assertNotNull(orderId);
        assertEquals(4, countBooked(bounds, patientUid), "the create took the final seat");

        // "Pre-registration refused when the slot is at capacity" - a further create
        // sees the live count at capacity and is refused with nothing persisted
        BusinessException ex = assertThrows(BusinessException.class,
                () -> composedCreate(modality, draftOrder(patientUid, bookingTime)),
                "the slot at capacity refuses further pre-registrations");
        assertEquals("A0400", ex.getCode(), "the house business-refusal code");
        assertTrue(ex.getMsg().contains("该时段已满"), "the D5 capacity token: " + ex.getMsg());
        assertEquals(4, countBooked(bounds, patientUid), "the refused create left no row");
    }

    @Test
    @Transactional
    void pastPreRegistrationCreateRefusesAndPersistsNothing() {
        String modality = uniqueModality();
        int patientUid = uniquePatientUid();
        seedConfig(modality, 4);
        Date past = new Date(examSlotConfigMapper.selectDbNow().getTime() - 60_000L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> composedCreate(modality, draftOrder(patientUid, past)),
                "a pre-registration attempt with a past time is refused (D4 - before any insert)");
        assertEquals("A0400", ex.getCode());
        assertTrue(ex.getMsg().contains("预登记时间不能早于当前时间"), "the D5 past token: " + ex.getMsg());

        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM exam_order WHERE patient_uid = ?",
                Integer.class, patientUid);
        assertEquals(0, total, "the refused create persisted NOTHING");
    }

    @Test
    @Transactional
    void walkInCreateIsUnaffectedByMissingSlotConfiguration() {
        // spec "Unconfigured modality refuses pre-registration" AND-clause: creates
        // WITHOUT booking_time never touch the config machinery at all; doubles as
        // the aggregate NULL booking_time round-trip (task 3.2, autoResultMap)
        int patientUid = uniquePatientUid();
        Integer orderId = composedCreate(uniqueModality(), draftOrder(patientUid, null));

        assertNotNull(orderId, "the walk-in create persists the aggregate");
        ExamOrder persisted = examOrderMapper.selectById(orderId);
        assertEquals(ExamOrderStatus.REGISTERED, persisted.getStatus(),
                "a NULL booking_time derives REGISTERED (现场单)");
        org.junit.jupiter.api.Assertions.assertNull(persisted.getBookingTime(),
                "the NULL slot anchor round-trips as NULL - the order occupies no slot");
    }

    /**
     * D7 lock-order proof: validatePreregistration runs inside the caller's create
     * transaction and HOLDS the config row lock - a second physical connection's
     * FOR UPDATE on the same row times out (55P03). The config row is seeded
     * COMMITTED (the probe must see it) and hard-deleted in finally.
     */
    @Test
    void validateHoldsTheConfigRowLockInsideTheCreateTransaction() throws SQLException {
        String modality = uniqueModality();
        ExamSlotConfig seeded = seedConfig(modality, 4);
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                // the create transaction's locked read - the D7 serialization point
                capacityService.validatePreregistration(modality, at(9, 17), 0);

                try (Connection probe = dataSource.getConnection()) {
                    probe.setAutoCommit(false);
                    try (Statement statement = probe.createStatement()) {
                        statement.execute("SET lock_timeout = '500ms'");
                        try {
                            statement.execute("SELECT id FROM exam_slot_config"
                                    + " WHERE modality = '" + modality + "' FOR UPDATE");
                            fail("the probe must NOT acquire the lock the create tx holds");
                        } catch (SQLException blocked) {
                            assertEquals("55P03", blocked.getSQLState(),
                                    "lock_not_available - the row was locked: " + blocked.getMessage());
                        }
                    } finally {
                        probe.rollback();
                    }
                } catch (SQLException e) {
                    fail("probe connection failed: " + e.getMessage(), e);
                }
                return null;
            });
        } finally {
            // the seeded row is COMMITTED - sweep it HARD (shared-server ruling)
            jdbcTemplate.update("DELETE FROM exam_slot_config WHERE id = ?", seeded.getId());
        }
    }
}
