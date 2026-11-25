package org.birdbro.lumen.core;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.core.entity.ExamSlotConfig;
import org.birdbro.lumen.core.entity.SlotWindow;
import org.birdbro.lumen.core.entity.SlotWindowBounds;
import org.birdbro.lumen.core.mapper.ExamSlotConfigMapper;
import org.birdbro.lumen.core.service.ExamSlotCapacityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slot-capacity validation (kernel-pre-registration tasks 2.3-2.5; spec
 * exam-workflow-kernel "Pre-Registration Support" - the refusal scenarios).
 * {@code resolveSlotWindow} is pure grid math on an in-memory config; {@code
 * validatePreregistration} loads the modality's live config row (FOR UPDATE,
 * design D7) and rides the house A0400 refusal pattern (design D5). Config rows
 * ride unique test tokens + {@code @Transactional} rollback (the shared-server
 * fixture contract - hard-delete never needed for rolled-back rows).
 *
 * @author birdbro
 */
class ExamSlotCapacityServiceTests extends KernelIntegrationTestBase {

    @Autowired
    private ExamSlotCapacityService capacityService;

    @Autowired
    private ExamSlotConfigMapper examSlotConfigMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---------- helpers ----------

    /**
     * A wall-clock-independent future day: TOMORROW, fixed once per JVM (a
     * day-rollover mid-run cannot mix dates). The original hardcoded 2026-11-24 broke
     * every run after 09:17 local (the past guard refuses the morning window) - the
     * class-level discovery during us-prereg-as-registered-sev task 1.2.
     */
    private static final java.time.LocalDate FUTURE_DAY = java.time.LocalDate.now().plusDays(1);

    /** A strictly-future wall clock: tomorrow at the given time (always future). */
    private static Date at(int hour, int minute) {
        LocalDateTime booking = LocalDateTime.of(FUTURE_DAY, java.time.LocalTime.of(hour, minute));
        return Date.from(booking.atZone(ZoneId.systemDefault()).toInstant());
    }

    private static Date expectedBound(int hour, int minute) {
        LocalDateTime bound = LocalDateTime.of(FUTURE_DAY, java.time.LocalTime.of(hour, minute));
        return Date.from(bound.atZone(ZoneId.systemDefault()).toInstant());
    }

    private static ExamSlotConfig config(String modality, Integer granularity, Integer capacity,
            List<SlotWindow> windows) {
        ExamSlotConfig config = ExamSlotConfig.builder()
                .modality(modality)
                .granularityMinutes(granularity)
                .slotCapacity(capacity)
                .openWindows(windows)
                .build();
        config.setDeleted(0);
        return config;
    }

    /** Live-unique test modality token (varchar 16 - "TST" + 9 digits). */
    private String uniqueModality() {
        String digits = Long.toString(System.nanoTime());
        return "TST" + digits.substring(digits.length() - 9);
    }

    /** The DB-server wall clock (design D4 - never an app clock). */
    private Date dbNow() {
        return jdbcTemplate.queryForObject("SELECT now()::timestamp", Date.class);
    }

    private static final List<SlotWindow> MORNING =
            Collections.singletonList(SlotWindow.builder().start("08:00").end("12:00").build());

    // ---------- task 2.3 - resolveSlotWindow (pure grid math) ----------

    @Test
    void floorsBookingTimeOntoTheGranularityGridInsideAWindow() {
        SlotWindowBounds bounds = capacityService.resolveSlotWindow(
                config(uniqueModality(), 30, 4, MORNING), at(9, 17));
        assertEquals(expectedBound(9, 0), bounds.getWindowStart(), "09:17 floors to 09:00");
        assertEquals(expectedBound(9, 30), bounds.getWindowEnd(), "slot end = floor + granularity");
    }

    @Test
    void refusesOutsideEveryOpenWindow() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> capacityService.resolveSlotWindow(
                        config(uniqueModality(), 30, 4, MORNING), at(13, 30)),
                "13:30 is outside the only open window 08:00-12:00");
        assertTrue(ex.getMsg().contains("不在开台时间窗内"), "the D5 window token: " + ex.getMsg());
    }

    @Test
    void windowEndIsExclusive() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> capacityService.resolveSlotWindow(
                        config(uniqueModality(), 30, 4, MORNING), at(12, 0)),
                "a booking at exactly the window end belongs to no window");
        assertTrue(ex.getMsg().contains("不在开台时间窗内"), "the D5 window token: " + ex.getMsg());
    }

    @Test
    void slotStartAtWindowStartIsInside() {
        SlotWindowBounds bounds = capacityService.resolveSlotWindow(
                config(uniqueModality(), 30, 4, MORNING), at(8, 5));
        assertEquals(expectedBound(8, 0), bounds.getWindowStart(), "08:05 floors to the window start");
        assertEquals(expectedBound(8, 30), bounds.getWindowEnd(), "slot end = floor + granularity");
    }

    @Test
    void gapBetweenWindowsFloorsIntoTheAfternoonWindow() {
        List<SlotWindow> two = List.of(
                SlotWindow.builder().start("08:00").end("12:00").build(),
                SlotWindow.builder().start("14:00").end("17:00").build());
        SlotWindowBounds bounds = capacityService.resolveSlotWindow(
                config(uniqueModality(), 30, 4, two), at(14, 10));
        assertEquals(expectedBound(14, 0), bounds.getWindowStart(), "14:10 floors into the 14:00 window");
        assertEquals(expectedBound(14, 30), bounds.getWindowEnd(), "slot end = floor + granularity");
    }

    @Test
    void emptyWindowsFailClosed() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> capacityService.resolveSlotWindow(
                        config(uniqueModality(), 30, 4, Collections.emptyList()), at(9, 17)),
                "an empty open_windows set = the modality has not opened pre-registration");
        assertTrue(ex.getMsg().contains("未设置开台时间窗"), "the fail-closed token: " + ex.getMsg());
    }

    @Test
    void invalidWindowEntriesFailClosed() {
        List<SlotWindow> malformed = Collections.singletonList(
                SlotWindow.builder().start("8:0").end("12:00").build());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> capacityService.resolveSlotWindow(
                        config(uniqueModality(), 30, 4, malformed), at(9, 17)),
                "a non-HH:mm window entry must fail CLOSED, never pass");
        assertTrue(ex.getMsg().contains("配置无效"), "the fail-closed token: " + ex.getMsg());
    }

    @Test
    void nonPositiveGranularityFailsClosed() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> capacityService.resolveSlotWindow(
                        config(uniqueModality(), 0, 4, MORNING), at(9, 17)),
                "a zero/negative granularity cannot define a grid");
        assertTrue(ex.getMsg().contains("配置无效"), "the fail-closed token: " + ex.getMsg());
    }

    // ---------- task 2.4 - validatePreregistration (DB-backed) ----------

    @Test
    @Transactional
    void unconfiguredModalityRefusesPreregistration() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> capacityService.validatePreregistration(uniqueModality(), at(9, 17), 0),
                "a modality with no config row refuses pre-registration (opt-in, fail-closed)");
        assertEquals("A0400", ex.getCode(), "the house business-refusal code");
        assertTrue(ex.getMsg().contains("未开通预登记"), "the D5 unconfigured token: " + ex.getMsg());
    }

    @Test
    void unconfiguredConfigRefusesAtTheResolveSurfaceToo() {
        // us-registration-redesign-api batch-2 incident: the composed caller flow reads
        // the config itself and hands it to resolveSlotWindow BEFORE validate runs - a
        // null config must fail CLOSED at the resolve surface with the same D5 token,
        // never an NPE/500 (the ① "Unconfigured modality refuses" scenario applied to
        // both public surfaces)
        BusinessException ex = assertThrows(BusinessException.class,
                () -> capacityService.resolveSlotWindow(null, at(9, 17)),
                "resolve on a missing config refuses fail-closed, never NPE");
        assertEquals("A0400", ex.getCode());
        assertTrue(ex.getMsg().contains("未开通预登记"), "the D5 unconfigured token: " + ex.getMsg());
    }

    @Test
    @Transactional
    void pastBookingTimeRefuses() {
        String modality = uniqueModality();
        examSlotConfigMapper.insert(config(modality, 30, 4, MORNING));
        Date past = new Date(dbNow().getTime() - 60_000L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> capacityService.validatePreregistration(modality, past, 0),
                "a past booking_time is not a future slot");
        assertEquals("A0400", ex.getCode());
        assertTrue(ex.getMsg().contains("预登记时间不能早于当前时间"), "the D5 past token: " + ex.getMsg());
    }

    @Test
    @Transactional
    void bookingAtDbNowRefuses() {
        String modality = uniqueModality();
        examSlotConfigMapper.insert(config(modality, 30, 4, MORNING));
        // boundary: exactly at the DB-server now is NOT a future slot (the derivation
        // sends at-now creates to REGISTERED; validate refuses them fail-closed)
        BusinessException ex = assertThrows(BusinessException.class,
                () -> capacityService.validatePreregistration(modality, dbNow(), 0));
        assertTrue(ex.getMsg().contains("预登记时间不能早于当前时间"), "the D5 past token: " + ex.getMsg());
    }

    @Test
    @Transactional
    void fullSlotRefuses() {
        String modality = uniqueModality();
        examSlotConfigMapper.insert(config(modality, 30, 4, MORNING));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> capacityService.validatePreregistration(modality, at(9, 17), 4),
                "occupancy at capacity refuses the slot");
        assertEquals("A0400", ex.getCode());
        assertTrue(ex.getMsg().contains("该时段已满"), "the D5 capacity token: " + ex.getMsg());
    }

    @Test
    @Transactional
    void belowCapacityPassesWithResolvedBounds() {
        String modality = uniqueModality();
        examSlotConfigMapper.insert(config(modality, 30, 4, MORNING));
        SlotWindowBounds bounds = capacityService.validatePreregistration(modality, at(9, 17), 3);
        assertEquals(expectedBound(9, 0), bounds.getWindowStart(), "the caller counts [start, end)");
        assertEquals(expectedBound(9, 30), bounds.getWindowEnd(), "the caller counts [start, end)");
    }

    @Test
    @Transactional
    void outsideWindowRefusesThroughValidate() {
        String modality = uniqueModality();
        examSlotConfigMapper.insert(config(modality, 30, 4, MORNING));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> capacityService.validatePreregistration(modality, at(13, 30), 0),
                "validate delegates the window rule to resolveSlotWindow");
        assertTrue(ex.getMsg().contains("不在开台时间窗内"), "the D5 window token: " + ex.getMsg());
    }
}
