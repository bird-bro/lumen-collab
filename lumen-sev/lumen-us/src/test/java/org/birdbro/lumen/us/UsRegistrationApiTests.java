package org.birdbro.lumen.us;

import java.sql.Timestamp;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import org.birdbro.lumen.feign.patient.PatientIdentityResponse;
import org.birdbro.lumen.feign.patient.PatientLookupResult;
import org.birdbro.lumen.feign.patient.PatientResolveOrRegisterResult;
import org.birdbro.lumen.feign.number.NumberIssueRequest;
import org.birdbro.lumen.feign.number.NumberIssueResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The registration-surface proof (us-station-registration-sev task 3.2, design D6/D8;
 * specs/us-station "Registration Surface" / "Patient Registry Adapter" / "US Registration
 * Extension" - the WHEN/THEN scenarios ARE the acceptance gate), REAL HTTP through the
 * test-server bootstrap under the REAL security chain: create-with-accession (the
 * platform number minted INSIDE the creation path through the number-issuer port, the
 * identity linked from the patient-registry port, the snapshot stored, the ext persisted
 * 1:1 through the kernel RegistrationHook seam), the state-machine refusal VERBATIM (the
 * kernel's own message, the stored status unchanged), the cancel branch split by stage
 * with reasons stored, the ext round-trip through the page query, the resolve passthrough's
 * three outcomes (exact/candidates/none, NO side effect), and the {@code us:order:*} perm
 * gates (tokenless 203, perm-less A0301, nothing written).
 *
 * <p>The counterparties are stubbed at the Feign seam ({@code UsIntegrationTestBase}'s
 * {@code @MockBean} - design D8: no patient/system service runs in this JVM, and the
 * adapters must never fabricate); the DB, the security chain, the kernel services, and
 * the hook are all REAL. Fixtures hard-DELETE on the stub band + the {@code us3}/{@code
 * US3-} markers (user ruling 2026-11-07).</p>
 *
 * @author birdbro
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsRegistrationApiTests extends UsApiTestBase {

    private static final String RESOLVE_URL = "/api/v1/us/patients/resolve";
    private static final String ORDERS_URL = "/api/v1/us/orders";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void sweepBeforeTest() {
        UsRegistrationFixtures.sweep(jdbcTemplate);
    }

    @AfterAll
    void sweepAfterClass() {
        UsRegistrationFixtures.sweep(jdbcTemplate);
    }

    // ------------------------------------ Create a booked order (+ ext via hook)

    @Test
    void createWalkInOrderIssuesAccessionLinksIdentityAndPersistsExt() throws Exception {
        // distinctive stub outcomes for this scenario: a MATCHED archive identity and a
        // platform number the kernel issues INSIDE the creation path
        int stubUid = STUB_UID_BASE + 101;
        when(patientApi.resolveOrRegister(any())).thenReturn(org.birdbro.common.core.R.ofSuccess(
                PatientResolveOrRegisterResult.builder()
                        .result(PatientResolveOrRegisterResult.RESULT_MATCHED)
                        .identity(PatientIdentityResponse.builder()
                                .patUid(stubUid)
                                .patId("US3-CREATE")
                                .name("us3建档张三")
                                .gender(2)
                                .phone("13800000101")
                                .state("RESOLVED")
                                .build())
                        .build()));
        when(numberApi.issue(any())).thenReturn(org.birdbro.common.core.R.ofSuccess(
                NumberIssueResponse.builder().number(STUB_ACCESSION_PREFIX + "ACC001").build()));

        long ts = stamp();
        String body = "{\"idType\":\"01\",\"idNo\":\"US3CREATE-" + ts + "\","
                + "\"cardNo\":\"US3CARD-" + ts + "\","
                + "\"name\":\"us3建档张三\",\"gender\":2,\"phone\":\"13800000101\","
                + "\"age\":\"30\",\"patientSource\":\"门诊\","
                + "\"examItems\":[{\"itemId\":1024,\"itemName\":\"腹部超声\"}],"
                + "\"emgFlag\":1,"
                + "\"ext\":{\"pregnancyStatus\":\"孕12周\",\"gestationalWeeks\":12,"
                + "\"note\":\"us3 ext 备注\",\"applyDept\":\"门诊内科\","
                + "\"applyDoctor\":\"李临床\",\"applyTime\":\"2026-11-24 08:30:00\","
                + "\"applyDeptId\":\"HIS-DEP-01\",\"applyDoctorId\":\"HIS-DOC-07\"}}";
        JsonNode response = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body,
                fullPermToken()));
        assertEquals("200", response.path("code").asText(),
                "the create must succeed through the real chain: " + response);
        int orderId = response.path("data").path("orderId").asInt(-1);
        assertTrue(orderId > 0, "the create returns the persisted order id: " + response);
        assertEquals(STUB_ACCESSION_PREFIX + "ACC001", response.path("data").path("accessionNo").asText(),
                "the accession rides the platform number issued INSIDE the creation path");
        // us-registration-redesign-api task 2.1 (拍板 02-B 提交即登记): a walk-in create
        // (no bookingTime) derives REGISTERED - the legacy FIXED-BOOKED entry retires
        assertEquals("REGISTERED", response.path("data").path("status").asText(),
                "the derived entry state for a walk-in is REGISTERED");
        assertTrue(response.path("data").path("bookingTime").isNull()
                        || response.path("data").path("bookingTime").isMissingNode(),
                "the walk-in result echoes bookingTime null");
        // us-prereg-as-registered: the entry category rides the response and the row -
        // the walk-in branch stamps WALK_IN (server-owned; the body cannot set it)
        assertEquals("WALK_IN", response.path("data").path("registrationType").asText(),
                "the walk-in create echoes registrationType WALK_IN");
        UsRegistrationFixtures.registerOrder(orderId);

        // the order document: the port identity linked, the snapshot stored, the
        // exam-item references + emg flag carried
        Map<String, Object> order = jdbcTemplate.queryForMap(
                "SELECT status, accession_no, patient_uid, emg_flag, "
                        + "registration_type::text AS reg_type, "
                        + "patient_snapshot->>'name' AS snap_name, "
                        + "patient_snapshot->>'patId' AS snap_pat_id, "
                        + "patient_snapshot->>'age' AS snap_age, "
                        + "patient_snapshot->>'patientSource' AS snap_source "
                        + "FROM exam_order WHERE id = ?", orderId);
        assertEquals("REGISTERED", order.get("status"),
                "the order persists with the derived REGISTERED entry");
        assertEquals("WALK_IN", order.get("reg_type"),
                "the walk-in row stamps registration_type WALK_IN");
        assertEquals(STUB_ACCESSION_PREFIX + "ACC001", order.get("accession_no"),
                "the accession_no column carries the issued number");
        assertEquals(stubUid, ((Number) order.get("patient_uid")).intValue(),
                "the order links the port result's patUid");
        assertEquals(1, ((Number) order.get("emg_flag")).intValue(),
                "the 急诊 flag persists");
        assertEquals("us3建档张三", order.get("snap_name"),
                "the snapshot carries the DELIVERED archive name");
        assertEquals("US3-CREATE", order.get("snap_pat_id"),
                "the snapshot carries the delivered pat_id");
        assertEquals("30", order.get("snap_age"),
                "the snapshot carries the exam-time age from the create body");
        assertEquals("门诊", order.get("snap_source"),
                "the snapshot carries the exam-time patient source");
        Integer itemCount = jdbcTemplate.queryForObject(
                "SELECT jsonb_array_length(exam_items) FROM exam_order WHERE id = ?",
                Integer.class, orderId);
        assertEquals(Integer.valueOf(1), itemCount,
                "the exam-item reference array persists");
        Integer itemId = jdbcTemplate.queryForObject(
                "SELECT (exam_items->0->>'itemId')::int FROM exam_order WHERE id = ?",
                Integer.class, orderId);
        assertEquals(Integer.valueOf(1024), itemId, "the item reference snapshot carries the catalog id");

        // the ext row: the HOOK persisted it 1:1 inside the registration transaction
        Map<String, Object> ext = jdbcTemplate.queryForMap(
                "SELECT pregnancy_status, gestational_weeks, note, apply_dept, apply_doctor, apply_time,"
                + " apply_dept_id, apply_doctor_id FROM us_registration_ext WHERE order_id = ?", orderId);
        assertEquals("孕12周", ext.get("pregnancy_status"), "the ext block persists 1:1");
        assertEquals(12, ((Number) ext.get("gestational_weeks")).intValue(),
                "the gestational weeks persist");
        assertEquals("us3 ext 备注", ext.get("note"), "the ext note persists");
        // us-apply-info-sev: the HIS application origin rides the ext block verbatim
        assertEquals("门诊内科", ext.get("apply_dept"), "the HIS apply department persists");
        assertEquals("李临床", ext.get("apply_doctor"), "the HIS apply doctor persists");
        assertEquals("2026-11-24 08:30:00", ext.get("apply_time"),
                "the raw-text apply-time stamp persists verbatim");
        assertEquals("HIS-DEP-01", ext.get("apply_dept_id"),
                "the reserved department id anchor persists");
        assertEquals("HIS-DOC-07", ext.get("apply_doctor_id"),
                "the reserved doctor id anchor persists");

        // the snapshot carries the body's cardNo (us-registration-redesign-api task 1.4 -
        // the additive jsonb key feeding the card-no workbench query condition)
        String cardNo = jdbcTemplate.queryForObject(
                "SELECT patient_snapshot->>'cardNo' FROM exam_order WHERE id = ?",
                String.class, orderId);
        assertEquals("US3CARD-" + ts, cardNo,
                "the create stores the body's cardNo into the snapshot");

        // the aggregate members: the kernel inserted the 1:1 execution + report members
        Integer executionRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_execution WHERE order_id = ?", Integer.class, orderId);
        Integer reportRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_report WHERE order_id = ?", Integer.class, orderId);
        assertEquals(Integer.valueOf(1), executionRows, "the execution member exists");
        assertEquals(Integer.valueOf(1), reportRows, "the report placeholder member exists");
    }

    // ---------------- Booking slot options (us-booking-slot-picker: the prototype's
    // ---------------- 满员标灰 slot dropdown - config grid × live occupancy)

    @Test
    void slotOptionsExposeTheConfiguredGridWithLiveOccupancy() throws Exception {
        String modality = uniqueModality();
        seedSlotConfig(modality, 2); // granularity 30, window 08:00-12:00 → 8 slots
        // one LIVE BOOKED order in tomorrow's 09:00-09:30 slot (the create path's
        // count predicate: BOOKED + booking_time in the window + first-item modality)
        long ts = stamp();
        int uid = STUB_UID_BASE + 801 + (int) (ts % 5000);
        jdbcTemplate.update(
                "INSERT INTO exam_order (accession_no, status, patient_uid, booking_time,"
                + " exam_items, patient_snapshot) VALUES (?, 'BOOKED', ?, ?::timestamp,"
                + " ?::jsonb, ?::jsonb)",
                "US3-SEED-" + ts, uid,
                java.time.LocalDate.now().plusDays(1).atTime(9, 17) + ":00",
                "[{\"itemId\":1024,\"itemName\":\"腹部超声\",\"modality\":\"" + modality + "\"}]",
                "{\"patId\":\"US3-SEED\",\"name\":\"us3slot余量A\"}");
        String tomorrow = java.time.LocalDate.now().plusDays(1)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        JsonNode response = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "/slot-options?date=" + tomorrow + "&modality=" + modality,
                null, fullPermToken()));
        assertEquals("200", response.path("code").asText(),
                "the slot-options read must succeed: " + response);
        assertTrue(response.path("data").path("configured").asBoolean(false),
                "the configured modality answers configured=true");
        JsonNode slots = response.path("data").path("slots");
        assertEquals(8, slots.size(),
                "the 08:00-12:00 window floors onto the 30-minute grid (8 slots)");
        assertEquals("08:00", slots.path(0).path("start").asText(), "the grid opens at the window start");
        assertEquals("09:00", slots.path(2).path("start").asText(), "the third slot starts 09:00");
        assertEquals("09:30", slots.path(2).path("end").asText(), "the third slot ends 09:30");
        assertEquals(1, slots.path(2).path("booked").asInt(-1),
                "the live BOOKED order counts against its slot (the create predicate)");
        assertEquals(2, slots.path(2).path("capacity").asInt(-1),
                "the capacity rides the config's slot_capacity");
        assertEquals(0, slots.path(0).path("booked").asInt(-1),
                "untouched slots report zero occupancy");

        // an unconfigured modality fails SOFT (configured=false) - the create path
        // stays the fail-closed half (该检查类型未开通预登记)
        JsonNode none = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "/slot-options?date=" + tomorrow + "&modality=US3NOPE-" + ts,
                null, fullPermToken()));
        assertEquals("200", none.path("code").asText(), "the soft answer is still 200");
        assertTrue(!none.path("data").path("configured").asBoolean(true),
                "an unconfigured modality answers configured=false");
        assertEquals(0, none.path("data").path("slots").size(),
                "the soft answer carries no slots");
    }

    // ---------------- Occupancy re-anchor (us-prereg-as-registered: 未取消即占位)

    @Test
    void registeredPreRegistrationOccupiesTheSlotAndCancelledFreesIt() throws Exception {
        String modality = uniqueModality();
        seedSlotConfig(modality, 1); // capacity 1 - a single live row fills the slot
        long ts = stamp();
        int uid = STUB_UID_BASE + 802 + (int) (ts % 5000);
        // a live slot-anchored REGISTERED row in tomorrow's 09:00-09:30 slot - under
        // the NEW occupancy predicate (未取消即占位) it occupies; the retired
        // BOOKED-only count ignored it entirely
        jdbcTemplate.update(
                "INSERT INTO exam_order (accession_no, status, patient_uid, booking_time,"
                + " exam_items, patient_snapshot) VALUES (?, 'REGISTERED', ?, ?::timestamp,"
                + " ?::jsonb, ?::jsonb)",
                "US3-SEED-" + ts, uid,
                java.time.LocalDate.now().plusDays(1).atTime(9, 17) + ":00",
                "[{\"itemId\":1024,\"itemName\":\"腹部超声\",\"modality\":\"" + modality + "\"}]",
                "{\"patId\":\"US3-SEED\",\"name\":\"us3占位已登记\"}");
        String tomorrow = java.time.LocalDate.now().plusDays(1)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // the picker mirrors the SAME predicate: the REGISTERED row books the 09:00 slot
        JsonNode slots = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "/slot-options?date=" + tomorrow + "&modality=" + modality,
                null, fullPermToken())).path("data").path("slots");
        assertEquals(1, slots.path(2).path("booked").asInt(-1),
                "the REGISTERED pre-registered row counts against its slot (未取消即占位)");

        // the create refuses the now-full slot
        stubMatchedIdentity(STUB_UID_BASE + 207, "us3占位受试者");
        stubNumber();
        String body = "{\"idType\":\"01\",\"idNo\":\"US3OCC-" + ts + "\","
                + "\"name\":\"us3占位受试者\",\"gender\":2,"
                + "\"bookingTime\":\"" + tomorrowAt(9, 17) + "\","
                + "\"examItems\":[{\"itemId\":1024,\"itemName\":\"占位项目\","
                + "\"modality\":\"" + modality + "\"}]}";
        JsonNode refused = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body,
                fullPermToken()));
        assertEquals("A0400", refused.path("code").asText(),
                "the REGISTERED row occupies - the slot is at capacity: " + refused);
        assertTrue(refused.path("msg").asText().contains("该时段已满"),
                "the capacity token surfaces verbatim: " + refused);

        // flipping the row to CANCELLED frees the slot - the same create now succeeds
        jdbcTemplate.update("UPDATE exam_order SET status = 'CANCELLED' WHERE accession_no = ?",
                "US3-SEED-" + ts);
        JsonNode freed = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body,
                fullPermToken()));
        assertEquals("200", freed.path("code").asText(),
                "a cancelled row no longer occupies - the slot frees: " + freed);
        UsRegistrationFixtures.registerOrder(freed.path("data").path("orderId").asInt(-1));
    }

    // ---------------- Modality reaches the issuer port (number-rule-modality-scope 2.3)

    // ------------------- Pre-registration create (us-registration-redesign-api 拍板 02-B)

    @Test
    void createPreRegistrationPersistsBookedAndOccupiesTheSlot() throws Exception {
        String modality = uniqueModality();
        seedSlotConfig(modality, 4);
        int stubUid = STUB_UID_BASE + 201;
        String stubAccession = stubNumber();
        when(patientApi.resolveOrRegister(any())).thenReturn(org.birdbro.common.core.R.ofSuccess(
                PatientResolveOrRegisterResult.builder()
                        .result(PatientResolveOrRegisterResult.RESULT_MATCHED)
                        .identity(PatientIdentityResponse.builder()
                                .patUid(stubUid)
                                .patId("US3-PRE")
                                .name("us3预登记档案")
                                .gender(2)
                                .state("RESOLVED")
                                .build())
                        .build()));

        String bookingTime = tomorrowAt(9, 17);
        String body = "{\"idType\":\"01\",\"idNo\":\"US3PRE-" + stamp() + "\","
                + "\"name\":\"us3预登记档案\",\"gender\":2,"
                + "\"bookingTime\":\"" + bookingTime + "\","
                + "\"examItems\":[{\"itemId\":1024,\"itemName\":\"预登记项目\","
                + "\"modality\":\"" + modality + "\"}]}";
        JsonNode response = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body,
                fullPermToken()));
        assertEquals("200", response.path("code").asText(),
                "the below-capacity pre-registration must succeed: " + response);
        int orderId = response.path("data").path("orderId").asInt(-1);
        assertTrue(orderId > 0, "the order persists: " + response);
        UsRegistrationFixtures.registerOrder(orderId);
        // us-prereg-as-registered (user ruling 2026-11-24 预约=预登记): a future slot
        // enters REGISTERED as well - the slot anchor records the appointment, not a
        // pending state; the branch stamps PRE_REGISTERED server-side
        assertEquals("REGISTERED", response.path("data").path("status").asText(),
                "a strictly-future booking derives REGISTERED (预登记=已登记)");
        assertEquals("PRE_REGISTERED", response.path("data").path("registrationType").asText(),
                "the pre-registration create echoes registrationType PRE_REGISTERED");
        assertEquals(bookingTime, response.path("data").path("bookingTime").asText(),
                "the result echoes the booking time");
        Map<String, Object> order = jdbcTemplate.queryForMap(
                "SELECT status, booking_time::text AS bt, registration_type::text AS reg_type"
                + " FROM exam_order WHERE id = ?", orderId);
        assertEquals("REGISTERED", order.get("status"),
                "the slot anchor persists with the REGISTERED (预登记) entry");
        assertEquals("PRE_REGISTERED", order.get("reg_type"),
                "the pre-registration row stamps registration_type PRE_REGISTERED");
        assertEquals(bookingTime, order.get("bt"), "the requested booking_time round-trips");

        // the window count grew by one - the NEW occupancy predicate (未取消即占位):
        // live not-cancelled orders in the window occupy, regardless of live stage
        Integer windowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_order WHERE deleted = 0"
                + " AND status NOT IN ('BOOKING_CANCELLED', 'CANCELLED')"
                + " AND booking_time >= ?::timestamp AND booking_time < ?::timestamp"
                + " AND exam_items->0->>'modality' = ?",
                Integer.class, tomorrowAt(9, 0), tomorrowAt(9, 30), modality);
        assertEquals(Integer.valueOf(1), windowCount,
                "the created pre-registered order occupies the resolved slot window");
        assertTrue(stubAccession != null, "the stub number was minted for this run");
    }

    @Test
    void createBodyRegistrationTypeIsIgnoredTheStampIsServerOwned() throws Exception {
        // us-prereg-as-registered task 2.4: the entry category is a SERVER-OWNED stamp -
        // a client body posting registrationType has no effect (the create DTO carries
        // no such property; Boot's mapper drops the unknown field silently)
        String modality = uniqueModality();
        seedSlotConfig(modality, 4);
        int stubUid = STUB_UID_BASE + 206;
        when(patientApi.resolveOrRegister(any())).thenReturn(org.birdbro.common.core.R.ofSuccess(
                PatientResolveOrRegisterResult.builder()
                        .result(PatientResolveOrRegisterResult.RESULT_MATCHED)
                        .identity(PatientIdentityResponse.builder()
                                .patUid(stubUid)
                                .patId("US3-FORGE")
                                .name("us3伪造类别")
                                .gender(2)
                                .state("RESOLVED")
                                .build())
                        .build()));
        stubNumber();
        // a WALK-IN body (no bookingTime) that FORGES the pre-registered category
        String body = "{\"idType\":\"01\",\"idNo\":\"US3FORGE-" + stamp() + "\","
                + "\"name\":\"us3伪造类别\",\"gender\":2,"
                + "\"registrationType\":\"PRE_REGISTERED\","
                + "\"examItems\":[{\"itemId\":1024,\"itemName\":\"伪造类别项目\","
                + "\"modality\":\"" + modality + "\"}]}";
        JsonNode response = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body,
                fullPermToken()));
        assertEquals("200", response.path("code").asText(),
                "the forged-category walk-in create still succeeds: " + response);
        int orderId = response.path("data").path("orderId").asInt(-1);
        assertTrue(orderId > 0, "the order persists: " + response);
        UsRegistrationFixtures.registerOrder(orderId);
        assertEquals("WALK_IN", response.path("data").path("registrationType").asText(),
                "the response echoes the SERVER-derived category, not the body's");
        String regType = jdbcTemplate.queryForObject(
                "SELECT registration_type::text FROM exam_order WHERE id = ?",
                String.class, orderId);
        assertEquals("WALK_IN", regType,
                "the forged registrationType never reaches the row - the branch stamps WALK_IN");
    }

    @Test
    void createRefusesPastBookingTimeAndPersistsNothing() throws Exception {
        String modality = uniqueModality();
        seedSlotConfig(modality, 4);
        stubMatchedIdentity(STUB_UID_BASE + 202, "us3过期预登记");
        String stubAccession = stubNumber();
        String past = java.time.LocalDateTime.now().minusSeconds(60)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String body = "{\"idType\":\"01\",\"idNo\":\"US3PAST-" + stamp() + "\","
                + "\"name\":\"us3过期预登记\",\"gender\":2,"
                + "\"bookingTime\":\"" + past + "\","
                + "\"examItems\":[{\"itemId\":1024,\"itemName\":\"预登记项目\","
                + "\"modality\":\"" + modality + "\"}]}";
        JsonNode response = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body,
                fullPermToken()));
        assertEquals("A0400", response.path("code").asText(),
                "the past pre-registration refuses: " + response);
        assertTrue(response.path("msg").asText().contains("预登记时间不能早于当前时间"),
                "the D5 past token surfaces verbatim: " + response);
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_order WHERE accession_no = ?",
                Integer.class, stubAccession);
        assertEquals(Integer.valueOf(0), rows, "nothing persisted on the refusal");
    }

    @Test
    void createRefusesUnconfiguredModality() throws Exception {
        String modality = uniqueModality(); // NO config row - the fail-closed default
        stubMatchedIdentity(STUB_UID_BASE + 203, "us3未开通预登记");
        String stubAccession = stubNumber();
        String body = "{\"idType\":\"01\",\"idNo\":\"US3UNCONF-" + stamp() + "\","
                + "\"name\":\"us3未开通预登记\",\"gender\":2,"
                + "\"bookingTime\":\"" + tomorrowAt(9, 17) + "\","
                + "\"examItems\":[{\"itemId\":1024,\"itemName\":\"预登记项目\","
                + "\"modality\":\"" + modality + "\"}]}";
        JsonNode response = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body,
                fullPermToken()));
        assertEquals("A0400", response.path("code").asText(),
                "the unconfigured modality refuses (opt-in per modality): " + response);
        assertTrue(response.path("msg").asText().contains("该检查类型未开通预登记"),
                "the D5 unconfigured token surfaces verbatim: " + response);
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_order WHERE accession_no = ?",
                Integer.class, stubAccession);
        assertEquals(Integer.valueOf(0), rows, "nothing persisted on the refusal");
    }

    @Test
    void createRefusesOutsideTheOpenWindows() throws Exception {
        String modality = uniqueModality();
        seedSlotConfig(modality, 4); // windows 08:00-12:00
        stubMatchedIdentity(STUB_UID_BASE + 204, "us3窗外预登记");
        stubNumber();
        String body = "{\"idType\":\"01\",\"idNo\":\"US3OUTW-" + stamp() + "\","
                + "\"name\":\"us3窗外预登记\",\"gender\":2,"
                + "\"bookingTime\":\"" + tomorrowAt(13, 30) + "\","
                + "\"examItems\":[{\"itemId\":1024,\"itemName\":\"预登记项目\","
                + "\"modality\":\"" + modality + "\"}]}";
        JsonNode response = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body,
                fullPermToken()));
        assertEquals("A0400", response.path("code").asText(),
                "the outside-window pre-registration refuses: " + response);
        assertTrue(response.path("msg").asText().contains("不在开台时间窗内"),
                "the D5 window token surfaces verbatim: " + response);
    }

    @Test
    void createRefusesWhenTheSlotIsAtCapacity() throws Exception {
        String modality = uniqueModality();
        seedSlotConfig(modality, 4);
        long ts = stamp();
        int stubUid = STUB_UID_BASE + 205;
        // seed the slot to capacity with REAL live BOOKED rows (the service's own count
        // predicate reads them - the caller count is not hand-fed)
        for (int i = 0; i < 4; i++) {
            jdbcTemplate.update(
                    "INSERT INTO exam_order (accession_no, status, patient_uid, booking_time,"
                    + " exam_items) VALUES (?, 'BOOKED', ?, ?::timestamp, ?::jsonb)",
                    "US3-CAP-" + ts + "-" + i, stubUid, tomorrowAt(9, 17),
                    "[{\"itemId\":1024,\"itemName\":\"容量种子\",\"modality\":\"" + modality + "\"}]");
        }
        stubMatchedIdentity(stubUid, "us3满时段预登记");
        stubNumber();
        String body = "{\"idType\":\"01\",\"idNo\":\"US3CAP-" + ts + "\","
                + "\"name\":\"us3满时段预登记\",\"gender\":2,"
                + "\"bookingTime\":\"" + tomorrowAt(9, 17) + "\","
                + "\"examItems\":[{\"itemId\":1024,\"itemName\":\"预登记项目\","
                + "\"modality\":\"" + modality + "\"}]}";
        JsonNode response = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body,
                fullPermToken()));
        assertEquals("A0400", response.path("code").asText(),
                "the at-capacity pre-registration refuses: " + response);
        assertTrue(response.path("msg").asText().contains("该时段已满"),
                "the D5 capacity token surfaces verbatim: " + response);
        Integer windowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_order WHERE status = 'BOOKED' AND deleted = 0"
                + " AND booking_time >= ?::timestamp AND booking_time < ?::timestamp"
                + " AND exam_items->0->>'modality' = ?",
                Integer.class, tomorrowAt(9, 0), tomorrowAt(9, 30), modality);
        assertEquals(Integer.valueOf(4), windowCount,
                "the refused create left no row - the count stays at capacity");
    }

    @Test
    void registerOnRegisteredOrderRefusesVerbatim() throws Exception {
        // the transitional break (design D9): a walk-in create IS registered now - the
        // old page's 登记 button A0400s on new orders until proposal ③ lands
        int orderId = createOrder("us3重复登记" + stamp());
        JsonNode response = parse(exchangeJson(HttpMethod.POST,
                ORDERS_URL + "/" + orderId + "/register", null, fullPermToken()));
        assertEquals("A0400", response.path("code").asText(),
                "REGISTERED -> REGISTERED is illegal: " + response);
        assertTrue(response.path("msg").asText()
                        .contains("非法状态迁移: REGISTERED → REGISTERED"),
                "the kernel refusal surfaces verbatim: " + response);
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM exam_order WHERE id = ?", String.class, orderId);
        assertEquals("REGISTERED", status, "the stored status stays unchanged");
    }

    // ------------------- Workbench query surface (us-registration-redesign-api 拍板 03-B)

    @Test
    void statusMultiSelectComposesWithWideConditions() throws Exception {
        int registeredA = createOrder("us3多选在册甲" + stamp());
        int registeredB = createOrder("us3多选在册乙" + stamp());
        int bookedId = seedBookedOrder("us3多选预登记" + stamp());

        JsonNode multi = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "?pageNum=1&pageSize=100&status=REGISTERED,CANCELLED",
                null, fullPermToken()));
        assertEquals("200", multi.path("code").asText(), "the multi-select must succeed");
        assertNotNull(findRecordByOrderId(multi, registeredA), "the first REGISTERED matches");
        assertNotNull(findRecordByOrderId(multi, registeredB), "the second REGISTERED matches");
        assertNull(findRecordByOrderId(multi, bookedId),
                "the BOOKED order is OUTSIDE the multi-select set");

        JsonNode union = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "?pageNum=1&pageSize=100&status=BOOKED,REGISTERED",
                null, fullPermToken()));
        assertNotNull(findRecordByOrderId(union, registeredA), "the union keeps the walk-ins");
        assertNotNull(findRecordByOrderId(union, bookedId), "the union keeps the pre-registration");
    }

    @Test
    void snapshotConditionsHitTheJsonbDocument() throws Exception {
        long ts = stamp();
        int stubUid = STUB_UID_BASE + 301;
        stubMatchedIdentity(stubUid, "us3快照条件" + ts);
        stubNumber();
        String cardNo = "US3CARDQ-" + ts;
        String body = "{\"idType\":\"01\",\"idNo\":\"US3SNAP-" + ts + "\","
                + "\"cardNo\":\"" + cardNo + "\","
                + "\"name\":\"us3快照条件" + ts + "\",\"gender\":1,"
                + "\"patientSource\":\"体检\","
                + "\"examItems\":[{\"itemId\":1024,\"itemName\":\"超声检查\"}]}";
        JsonNode create = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body,
                fullPermToken()));
        assertEquals("200", create.path("code").asText(), "the create must succeed: " + create);
        int orderId = create.path("data").path("orderId").asInt(-1);
        UsRegistrationFixtures.registerOrder(orderId);

        JsonNode byCard = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "?pageNum=1&pageSize=100&cardNo=" + cardNo, null, fullPermToken()));
        assertNotNull(findRecordByOrderId(byCard, orderId),
                "the card-no condition reads the snapshot jsonb (exact)");
        JsonNode byCardMiss = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "?pageNum=1&pageSize=100&cardNo=US3NOPE-" + ts, null, fullPermToken()));
        assertEquals(0, byCardMiss.path("data").path("records").size(),
                "a card-no matching nothing narrows to an EMPTY page (the condition is real)");
        JsonNode byPatId = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "?pageNum=1&pageSize=100&patientId=US3-PREREG", null, fullPermToken()));
        assertNotNull(findRecordByOrderId(byPatId, orderId),
                "the patient-id condition reads the snapshot patId (exact)");
        JsonNode byPatIdMiss = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "?pageNum=1&pageSize=100&patientId=US3-NOPE", null, fullPermToken()));
        assertEquals(0, byPatIdMiss.path("data").path("records").size(),
                "a patient-id matching nothing narrows to an EMPTY page");
        JsonNode bySource = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "?pageNum=1&pageSize=100&patientSource=体检", null, fullPermToken()));
        assertNotNull(findRecordByOrderId(bySource, orderId),
                "the patient-source condition reads the snapshot (exact)");
        JsonNode bySourceMiss = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "?pageNum=1&pageSize=100&patientSource=不存在的来源" + ts,
                null, fullPermToken()));
        assertEquals(0, bySourceMiss.path("data").path("records").size(),
                "a patient-source matching nothing narrows to an EMPTY page");
    }

    @Test
    void examItemNameMatchesFuzzilyAcrossTheJsonbArray() throws Exception {
        long ts = stamp();
        stubMatchedIdentity(STUB_UID_BASE + 302, "us3项目模糊" + ts);
        stubNumber();
        String body = "{\"idType\":\"01\",\"idNo\":\"US3ITEM-" + ts + "\","
                + "\"name\":\"us3项目模糊" + ts + "\",\"gender\":1,"
                + "\"examItems\":[{\"itemId\":1024,\"itemName\":\"胆囊超声检查\","
                + "\"modality\":\"UR3GALLX\"}]}";
        JsonNode create = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body,
                fullPermToken()));
        assertEquals("200", create.path("code").asText(), "the create must succeed: " + create);
        int orderId = create.path("data").path("orderId").asInt(-1);
        UsRegistrationFixtures.registerOrder(orderId);

        JsonNode byItem = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "?pageNum=1&pageSize=100&examItemName=胆囊", null, fullPermToken()));
        assertNotNull(findRecordByOrderId(byItem, orderId),
                "the item-name fragment matches per-element (itemName contains)");

        JsonNode byFalsePositive = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "?pageNum=1&pageSize=100&examItemName=GALLX", null, fullPermToken()));
        assertNull(findRecordByOrderId(byFalsePositive, orderId),
                "a fragment that only exists in ANOTHER jsonb key (modality) must NOT match");
    }

    @Test
    void applyTimeRangeBoundsTheRegistrationMoment() throws Exception {
        long ts = stamp();
        int orderId = createOrder("us3申请时间" + ts);
        String yesterday = java.time.LocalDate.now().minusDays(1)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String tomorrow = java.time.LocalDate.now().plusDays(1)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        JsonNode inside = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "?pageNum=1&pageSize=100&applyTimeStart=" + yesterday
                        + " 00:00:00&applyTimeEnd=" + tomorrow + " 00:00:00",
                null, fullPermToken()));
        assertNotNull(findRecordByOrderId(inside, orderId),
                "the order created NOW falls inside the range (apply-time = create_time)");

        JsonNode future = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "?pageNum=1&pageSize=100&applyTimeStart=" + tomorrow
                        + " 00:00:00",
                null, fullPermToken()));
        assertNull(findRecordByOrderId(future, orderId),
                "the order is OUTSIDE a future-only range");
    }

    @Test
    void statusCountsRespectTheFilters() throws Exception {
        String marker = "us3计数" + stamp();
        // the snapshot name is the DELIVERED archive name - the name-filtered half
        // needs archived-name creates (the pageQueryFiltersByStatusAndPatientName lesson)
        createOrderWithArchivedName(marker + "甲");
        createOrderWithArchivedName(marker + "乙");
        int bookedId = seedBookedOrder(marker + "预");

        JsonNode counts = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "/status-counts", null, fullPermToken()));
        assertEquals("200", counts.path("code").asText(),
                "the status-count endpoint must exist: " + counts);
        assertTrue(counts.path("data").path("BOOKED").asLong(0) >= 1,
                "the seeded pre-registration counts under BOOKED");
        assertTrue(counts.path("data").path("REGISTERED").asLong(0) >= 2,
                "the walk-ins count under REGISTERED");
        for (String status : new String[]{"IN_PROGRESS", "COMPLETED",
                "BOOKING_CANCELLED", "CANCELLED"}) {
            assertTrue(counts.path("data").has(status),
                    "the meter zero-fills every state: missing " + status);
        }

        JsonNode filtered = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "/status-counts?patientName=" + marker, null, fullPermToken()));
        assertEquals(2, filtered.path("data").path("REGISTERED").asLong(-1),
                "the counts respect the remaining filters (the name marker narrows to 2)");
        assertEquals(1, filtered.path("data").path("BOOKED").asLong(-1),
                "the seeded pre-registration counts under the same filter");
    }

    // ------------------- red-registration stub helpers (us-registration-redesign-api)

    /** Stubs the patient seam to a MATCHED archive identity (the per-test default). */
    private void stubMatchedIdentity(int stubUid, String name) {
        when(patientApi.resolveOrRegister(any())).thenReturn(org.birdbro.common.core.R.ofSuccess(
                PatientResolveOrRegisterResult.builder()
                        .result(PatientResolveOrRegisterResult.RESULT_MATCHED)
                        .identity(PatientIdentityResponse.builder()
                                .patUid(stubUid)
                                .patId("US3-PREREG")
                                .name(name)
                                .gender(2)
                                .state("RESOLVED")
                                .build())
                        .build()));
    }

    /** Stubs the number seam to one run-unique accession and returns it. */
    private String stubNumber() {
        String accession = STUB_ACCESSION_PREFIX + "PREREG-" + stamp();
        when(numberApi.issue(any())).thenReturn(org.birdbro.common.core.R.ofSuccess(
                NumberIssueResponse.builder().number(accession).build()));
        return accession;
    }

    /** Live-unique UR3- modality token (the slot-config sweep family). */
    private String uniqueModality() {
        return "UR3" + java.util.UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8);
    }

    /** Seeds one live slot config (granularity 30, morning window 08:00-12:00). */
    private int seedSlotConfig(String modality, int capacity) {
        Integer id = jdbcTemplate.queryForObject(
                "INSERT INTO exam_slot_config (modality, granularity_minutes, slot_capacity,"
                + " open_windows) VALUES (?, 30, ?, ?::jsonb) RETURNING id",
                Integer.class, modality, capacity,
                "[{\"start\":\"08:00\",\"end\":\"12:00\"}]");
        UsRegistrationFixtures.registerSlotConfig(id);
        return id;
    }

    /** Seeds one raw BOOKED order (the 登记/取消 action targets - a pre-registered row
     *  bypasses the create path; the US3- prefix + stub band carry the sweep). */
    private int seedBookedOrder(String name) {
        long ts = stamp();
        int uid = STUB_UID_BASE + 701 + (int) (ts % 5000);
        jdbcTemplate.update(
                "INSERT INTO exam_order (accession_no, status, patient_uid, booking_time,"
                + " patient_snapshot) VALUES (?, 'BOOKED', ?, ?::timestamp, ?::jsonb)",
                "US3-SEED-" + ts, uid,
                java.time.LocalDate.now().plusDays(1).atTime(9, 17) + ":00",
                "{\"patId\":\"US3-SEED\",\"name\":\"" + name + "\"}");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM exam_order WHERE accession_no = ?",
                Integer.class, "US3-SEED-" + ts);
    }

    /** A fixed-future wall clock tomorrow morning (inside the seeded window). */
    private static String tomorrowAt(int hour, int minute) {
        return java.time.LocalDate.now().plusDays(1).atTime(hour, minute, 0)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Test
    void modalityReachesTheIssuerPortAndPersistsInTheExamItemsJsonb() throws Exception {
        // spec us-station "Modality reaches the issuer port": a create body whose first
        // item reference carries modality US issues the 登记号 family WITH that token
        // through the number contract AND stores the item refs with the modality intact
        int stubUid = STUB_UID_BASE + 601;
        when(patientApi.resolveOrRegister(any())).thenReturn(org.birdbro.common.core.R.ofSuccess(
                PatientResolveOrRegisterResult.builder()
                        .result(PatientResolveOrRegisterResult.RESULT_MATCHED)
                        .identity(PatientIdentityResponse.builder()
                                .patUid(stubUid)
                                .patId("US3-MOD")
                                .name("us3模态档案")
                                .gender(2)
                                .state("RESOLVED")
                                .build())
                        .build()));
        java.util.concurrent.atomic.AtomicReference<NumberIssueRequest> issued =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(numberApi.issue(any())).thenAnswer(inv -> {
            issued.set(inv.getArgument(0));
            return org.birdbro.common.core.R.ofSuccess(NumberIssueResponse.builder()
                    .number(STUB_ACCESSION_PREFIX + "ACCUS1").build());
        });

        long ts = stamp();
        String body = "{\"idType\":\"01\",\"idNo\":\"US3MOD-" + ts + "\","
                + "\"name\":\"us3模态档案\",\"gender\":2,"
                + "\"examItems\":[{\"itemId\":1024,\"itemName\":\"腹部超声\","
                + "\"modality\":\"US\"}]}";
        JsonNode response = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body,
                fullPermToken()));
        assertEquals("200", response.path("code").asText(),
                "the modality-carrying create must succeed through the real chain: "
                        + response);
        int orderId = response.path("data").path("orderId").asInt(-1);
        assertTrue(orderId > 0, "the order persists: " + response);
        UsRegistrationFixtures.registerOrder(orderId);
        assertEquals(STUB_ACCESSION_PREFIX + "ACCUS1",
                response.path("data").path("accessionNo").asText(),
                "the accession rides the number issued INSIDE the creation path");

        // the port received the family + the first item's token TOGETHER
        NumberIssueRequest request = issued.get();
        assertNotNull(request, "the create path called the number-issue contract");
        assertEquals("登记号", request.getRuleType(),
                "the kernel picks the accession family (the adapter never does)");
        assertEquals("US", request.getModality(),
                "the first item's modality token reaches the issuer port verbatim");

        // the exam_items jsonb carries the modality intact
        String stored = jdbcTemplate.queryForObject(
                "SELECT exam_items->0->>'modality' FROM exam_order WHERE id = ?",
                String.class, orderId);
        assertEquals("US", stored,
                "the item reference persists in the exam_items jsonb WITH its modality");

        // the read side renders the token back through the page view (the refs pass
        // through the view untouched)
        JsonNode page = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "?pageNum=1&pageSize=50", null, fullPermToken()));
        JsonNode record = findRecordByOrderId(page, orderId);
        assertNotNull(record, "the order renders in the page query");
        assertEquals("US", record.path("examItems").get(0).path("modality").asText(),
                "the page view renders the persisted item modality");
    }

    // --------------------------- Incomplete port result refused (adapter scenarios)

    @Test
    void patUidLessPortResultRefusesAndPersistsNothing() throws Exception {
        when(patientApi.resolveOrRegister(any())).thenReturn(org.birdbro.common.core.R.ofSuccess(
                PatientResolveOrRegisterResult.builder()
                        .result(PatientResolveOrRegisterResult.RESULT_MATCHED)
                        .identity(PatientIdentityResponse.builder()
                                .patId("US3-NOUNID")
                                .build())
                        .build()));
        String name = "us3拒绝无uid" + stamp();
        String body = "{\"idType\":\"01\",\"idNo\":\"US3NOUNID-" + stamp() + "\","
                + "\"name\":\"" + name + "\"}";
        JsonNode response = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body,
                fullPermToken()));
        assertEquals("A0400", response.path("code").asText(),
                "the incomplete port result refuses with the house A0400: " + response);
        assertTrue(response.path("msg").asText().contains("pat_uid"),
                "the refusal names the missing linkage key: " + response);
        Long rows = countOrdersByName(name);
        assertEquals(Long.valueOf(0), rows,
                "order creation is refused and NOTHING persists (the acceptance scenario)");
    }

    @Test
    void ambiguousCandidatesRefuseCreationAndPersistNothing() throws Exception {
        when(patientApi.resolveOrRegister(any())).thenReturn(org.birdbro.common.core.R.ofSuccess(
                PatientResolveOrRegisterResult.builder()
                        .result(PatientResolveOrRegisterResult.RESULT_CANDIDATES)
                        .candidates(java.util.Collections.singletonList(
                                PatientIdentityResponse.builder().patUid(1).build()))
                        .build()));
        String name = "us3拒绝候选" + stamp();
        String body = "{\"name\":\"" + name + "\",\"phone\":\"13900000999\"}";
        JsonNode response = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body,
                fullPermToken()));
        assertEquals("A0400", response.path("code").asText(),
                "an ambiguous candidate list is never an order linkage: " + response);
        assertEquals(Long.valueOf(0), countOrdersByName(name),
                "nothing persists on the refusal path");
    }

    // --------------------------------------- transitions (register / cancel)

    @Test
    void registerTransitionsBookedToRegistered() throws Exception {
        // us-registration-redesign-api: the 登记 action's real target is a PRE-REGISTERED
        // (BOOKED) order - a raw-seeded row (the create path now derives REGISTERED)
        int orderId = seedBookedOrder("us3登记流转" + stamp());
        JsonNode response = parse(exchangeJson(HttpMethod.POST,
                ORDERS_URL + "/" + orderId + "/register", null, fullPermToken()));
        assertEquals("200", response.path("code").asText(),
                "the 登记 transition must succeed: " + response);
        assertEquals("REGISTERED", response.path("data").asText(),
                "BOOKED -> REGISTERED is the legal 登记 edge (arrival confirmation)");
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM exam_order WHERE id = ?", String.class, orderId);
        assertEquals("REGISTERED", status, "the transition persists");
    }

    @Test
    void illegalTransitionRefusalSurfacesKernelMessageVerbatim() throws Exception {
        int orderId = seedBookedOrder("us3非法迁移" + stamp());
        // BOOKED -> BOOKING_CANCELLED first (legal), then 登记 on the cancelled order
        // - the state machine's exact refusal shape the spec pins verbatim
        JsonNode cancel = parse(exchangeJson(HttpMethod.POST,
                ORDERS_URL + "/" + orderId + "/cancel",
                "{\"reason\":\"us3先取消\"}", fullPermToken()));
        assertEquals("BOOKING_CANCELLED", cancel.path("data").asText(),
                "the cancel branch from BOOKED: " + cancel);

        JsonNode response = parse(exchangeJson(HttpMethod.POST,
                ORDERS_URL + "/" + orderId + "/register", null, fullPermToken()));
        assertEquals("A0400", response.path("code").asText(),
                "the kernel refusal must surface verbatim: " + response);
        // the house BusinessException envelope prefixes the enum text (the B2 A0715
        // discipline): the kernel's own message rides VERBATIM inside it - no masking,
        // no rewriting
        assertTrue(response.path("msg").asText()
                        .contains("非法状态迁移: BOOKING_CANCELLED → REGISTERED"),
                "the refusal message is the KERNEL's own, verbatim inside the house "
                        + "envelope shape: " + response);
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM exam_order WHERE id = ?", String.class, orderId);
        assertEquals("BOOKING_CANCELLED", status,
                "the stored status stays UNCHANGED after the refusal");
    }

    @Test
    void cancelBranchSplitsByStageWithTheReasonStored() throws Exception {
        // BOOKED -> BOOKING_CANCELLED with the reason stored (a raw-seeded
        // pre-registered row - the create path derives REGISTERED now)
        int bookedId = seedBookedOrder("us3取消预约" + stamp());
        JsonNode bookedCancel = parse(exchangeJson(HttpMethod.POST,
                ORDERS_URL + "/" + bookedId + "/cancel",
                "{\"reason\":\"us3取消原因-预约\"}", fullPermToken()));
        assertEquals("BOOKING_CANCELLED", bookedCancel.path("data").asText(),
                "the cancel branch from BOOKED lands BOOKING_CANCELLED: " + bookedCancel);
        String bookedReason = jdbcTemplate.queryForObject(
                "SELECT cancel_reason FROM exam_order WHERE id = ?", String.class, bookedId);
        assertEquals("us3取消原因-预约", bookedReason, "the reason is stored on the branch");

        // REGISTERED -> CANCELLED with the reason stored (the walk-in create IS
        // registered now - no 登记 step)
        int registeredId = createOrder("us3取消在册" + stamp());
        JsonNode registeredCancel = parse(exchangeJson(HttpMethod.POST,
                ORDERS_URL + "/" + registeredId + "/cancel",
                "{\"reason\":\"us3取消原因-在册\"}", fullPermToken()));
        assertEquals("CANCELLED", registeredCancel.path("data").asText(),
                "the cancel branch from REGISTERED lands CANCELLED: " + registeredCancel);
        String registeredReason = jdbcTemplate.queryForObject(
                "SELECT cancel_reason FROM exam_order WHERE id = ?", String.class, registeredId);
        assertEquals("us3取消原因-在册", registeredReason, "the reason is stored on the branch");

        // the PRE-REGISTERED order (PRE_REGISTERED stamp) cancels -> CANCELLED too - the
        // US flow no longer produces BOOKED rows, so the machine's BOOKING_CANCELLED
        // branch is unreachable from the API surface (us-prereg-as-registered; the
        // BOOKED branch above stays as the raw-seeded machine characterization)
        String preRegModality = uniqueModality();
        seedSlotConfig(preRegModality, 4);
        stubNumber();
        long cancelTs = stamp();
        String preRegBody = "{\"idType\":\"01\",\"idNo\":\"US3PREREGCANCEL-" + cancelTs + "\","
                + "\"name\":\"us3取消预登记" + cancelTs + "\",\"gender\":1,"
                + "\"bookingTime\":\"" + tomorrowAt(10, 30) + "\","
                + "\"examItems\":[{\"itemId\":1024,\"itemName\":\"预登记取消项目\","
                + "\"modality\":\"" + preRegModality + "\"}]}";
        JsonNode preRegCreate = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, preRegBody,
                fullPermToken()));
        assertEquals("200", preRegCreate.path("code").asText(),
                "the pre-registration create must succeed: " + preRegCreate);
        int preRegId = preRegCreate.path("data").path("orderId").asInt(-1);
        assertTrue(preRegId > 0, "the pre-registration persists");
        assertEquals("REGISTERED", preRegCreate.path("data").path("status").asText(),
                "the pre-registration enters REGISTERED (预登记=已登记)");
        UsRegistrationFixtures.registerOrder(preRegId);
        JsonNode preRegCancel = parse(exchangeJson(HttpMethod.POST,
                ORDERS_URL + "/" + preRegId + "/cancel",
                "{\"reason\":\"us3取消原因-预登记\"}", fullPermToken()));
        assertEquals("CANCELLED", preRegCancel.path("data").asText(),
                "cancelling the pre-registered order lands CANCELLED (no BOOKED in the flow): "
                        + preRegCancel);
        String preRegReason = jdbcTemplate.queryForObject(
                "SELECT cancel_reason FROM exam_order WHERE id = ?", String.class, preRegId);
        assertEquals("us3取消原因-预登记", preRegReason, "the reason is stored on the branch");
    }

    // --------------------------------- ext round-trip through the page query

    @Test
    void extRoundTripsThroughThePageQuery() throws Exception {
        // a FIXED archive identity so the snapshot-name assertion is deterministic (the
        // DELIVERED archive fields are the snapshot's base - the create body's name
        // only fills a gap)
        when(patientApi.resolveOrRegister(any())).thenReturn(org.birdbro.common.core.R.ofSuccess(
                PatientResolveOrRegisterResult.builder()
                        .result(PatientResolveOrRegisterResult.RESULT_MATCHED)
                        .identity(PatientIdentityResponse.builder()
                                .patUid(STUB_UID_BASE + 401)
                                .patId("US3-EXT")
                                .name("us3ext档案")
                                .gender(2)
                                .state("RESOLVED")
                                .build())
                        .build()));
        int withExtId = createOrderWithExt("us3ext档案",
                "孕14周", 14, "us3 ext 往返");
        int withoutExtId = createOrder("us3无ext" + stamp());

        JsonNode response = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "?pageNum=1&pageSize=50", null, fullPermToken()));
        assertEquals("200", response.path("code").asText(),
                "the page query must succeed: " + response);
        assertTrue(response.path("data").path("total").asInt(0) >= 2,
                "both created orders are in the page");

        JsonNode withExt = findRecordByOrderId(response, withExtId);
        assertNotNull(withExt, "the ext-carrying order renders in the page");
        assertEquals("孕14周", withExt.path("pregnancyStatus").asText(),
                "the query path returns the persisted pregnancy status");
        assertEquals(14, withExt.path("gestationalWeeks").asInt(-1),
                "the query path returns the persisted gestational weeks");
        assertEquals("us3 ext 往返", withExt.path("note").asText(),
                "the query path returns the persisted note");
        // us-apply-info-sev: the HIS origin trio flattens onto the view like the rest of ext
        assertEquals("门诊内科", withExt.path("applyDept").asText(),
                "the query path returns the persisted apply department");
        assertEquals("李临床", withExt.path("applyDoctor").asText(),
                "the query path returns the persisted apply doctor");
        assertEquals("2026-11-24 08:30:00", withExt.path("applyTime").asText(),
                "the query path returns the raw-text apply-time stamp");
        assertEquals("HIS-DEP-01", withExt.path("applyDeptId").asText(),
                "the query path returns the reserved department id anchor");
        assertEquals("HIS-DOC-07", withExt.path("applyDoctorId").asText(),
                "the query path returns the reserved doctor id anchor");
        assertEquals("us3ext档案", withExt.path("patientSnapshot").path("name").asText(),
                "the page carries the patient identity from the jsonb snapshot (the "
                        + "delivered archive fields win)");
        assertEquals("US3-EXT", withExt.path("patientSnapshot").path("patId").asText(),
                "the snapshot carries the delivered pat_id");
        assertTrue(withExt.path("accessionNo").asText("").startsWith(STUB_ACCESSION_PREFIX),
                "the page carries the accession");
        // us-prereg-as-registered: the page rows carry the operator stamps (登记时间/
        // 登记人 = the create stamps for BOTH kinds) and the server-stamped category
        assertTrue(withExt.path("createBy").asInt(-1) > 0,
                "the page row carries createBy (登记人 = the create operator)");
        assertFalse(withExt.path("createTime").isNull(), "the page row carries createTime");
        assertEquals("WALK_IN", withExt.path("registrationType").asText(),
                "the walk-in page row stamps registrationType WALK_IN");

        JsonNode withoutExt = findRecordByOrderId(response, withoutExtId);
        assertNotNull(withoutExt, "the ext-less order renders in the page");
        assertTrue(withoutExt.path("pregnancyStatus").isNull(),
                "an order without an ext row carries null ext fields (the 1:1 table "
                        + "exists only for orders that carry US fields)");
        assertTrue(withoutExt.path("applyDept").isNull(),
                "the ext-less order also carries null apply-origin fields");
        assertEquals("WALK_IN", withoutExt.path("registrationType").asText(),
                "the category stamp rides the document, ext row or not");
    }

    @Test
    void pageQueryFiltersByStatusAndPatientName() throws Exception {
        // the snapshot name is the DELIVERED archive name, so each create re-stubs the
        // archive identity to the name the order is meant to carry (the jsonb filter
        // then matches what the list renders)
        int orderId = createOrderWithArchivedName("us3过滤目标" + stamp());
        createOrderWithArchivedName("us3过滤无关" + stamp());

        JsonNode byName = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "?pageNum=1&pageSize=50&patientName=" + "us3过滤目标",
                null, fullPermToken()));
        assertEquals("200", byName.path("code").asText(), "the name filter must succeed");
        assertEquals(1, byName.path("data").path("records").size(),
                "the jsonb name filter narrows to the target order: " + byName);
        assertEquals(orderId, byName.path("data").path("records").get(0).path("orderId").asInt(-1),
                "the filtered record is the target order");

        JsonNode byStatus = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "?pageNum=1&pageSize=50&status=REGISTERED", null, fullPermToken()));
        assertEquals("200", byStatus.path("code").asText(), "the status filter must succeed");
        for (JsonNode record : byStatus.path("data").path("records")) {
            assertEquals("REGISTERED", record.path("status").asText(),
                    "every filtered record is REGISTERED (the derived walk-in entry)");
        }
    }

    @Test
    void extInsertFailureRollsTheWholeRegistrationBack() throws Exception {
        // the hook contract's defining property (kernel task 7.1): ANY hook exception
        // propagates INSIDE the registration transaction - the whole registration rolls
        // back, so no order row can survive a failed extension insert (no partial
        // order). pregnancy_status is varchar(16): 17+ chars dies at the DB level.
        String name = "us3回滚" + stamp();
        String body = "{\"idType\":\"01\",\"idNo\":\"US3ROLL-" + stamp() + "\","
                + "\"name\":\"" + name + "\",\"gender\":2,"
                + "\"examItems\":[{\"itemId\":1024,\"itemName\":\"超声产检\"}],"
                + "\"ext\":{\"pregnancyStatus\":\"孕17周溢出长度测试ABCDEFJKLMNOPQR\","
                + "\"gestationalWeeks\":17}}";
        JsonNode response = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body,
                fullPermToken()));
        assertEquals("500", response.path("code").asText(),
                "the DB-level ext failure surfaces as the house 500 envelope: " + response);
        assertEquals(Long.valueOf(0), countOrdersByName(name),
                "NO order row may survive the failed ext insert (the hook runs INSIDE "
                        + "the registration transaction)");
        Integer extRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM us_registration_ext WHERE order_id IN ("
                        + "SELECT id FROM exam_order WHERE patient_snapshot->>'name' = ?)",
                Integer.class, name);
        assertEquals(Integer.valueOf(0), extRows,
                "no ext row survives either (the order never persisted)");
    }

    // --------------------- B3-review Minor-1 rider: ext audit fields are server-owned

    @Test
    void extAuditFieldsAreServerOwnedNeverClientBindable() throws Exception {
        // the ext block binds the client body 1:1 (kept by design) - but the audit
        // columns are SERVER-OWNED: a client-posted deleted=1 must never persist (the
        // row would arrive soft-deleted), and create/update stamps belong to the
        // meta-fill + DB defaults, never the body
        when(patientApi.resolveOrRegister(any())).thenReturn(org.birdbro.common.core.R.ofSuccess(
                PatientResolveOrRegisterResult.builder()
                        .result(PatientResolveOrRegisterResult.RESULT_MATCHED)
                        .identity(PatientIdentityResponse.builder()
                                .patUid(STUB_UID_BASE + 701)
                                .patId("US3-EXTAUDIT")
                                .name("us3ext审计档案")
                                .gender(2)
                                .state("RESOLVED")
                                .build())
                        .build()));
        String body = "{\"idType\":\"01\",\"idNo\":\"US3EXTAUDIT-" + stamp() + "\","
                + "\"name\":\"us3ext审计档案\",\"gender\":2,"
                + "\"examItems\":[{\"itemId\":1024,\"itemName\":\"超声产检\"}],"
                + "\"ext\":{\"pregnancyStatus\":\"孕8周\",\"gestationalWeeks\":8,"
                + "\"note\":\"us3 ext 审计\","
                + "\"deleted\":1,\"createBy\":999,\"updateBy\":999,"
                + "\"createTime\":\"2001-01-01 00:00:00\","
                + "\"updateTime\":\"2001-01-01 00:00:00\"}}";
        JsonNode response = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body,
                fullPermToken()));
        assertEquals("200", response.path("code").asText(),
                "the create must succeed through the real chain: " + response);
        int orderId = response.path("data").path("orderId").asInt(-1);
        assertTrue(orderId > 0, "the order persists: " + response);
        UsRegistrationFixtures.registerOrder(orderId);

        Map<String, Object> ext = jdbcTemplate.queryForMap(
                "SELECT deleted, create_by, update_by, create_time FROM us_registration_ext "
                        + "WHERE order_id = ?", orderId);
        assertEquals(0, ((Number) ext.get("deleted")).intValue(),
                "a client-posted deleted=1 must NOT persist - deleted is server-owned");
        assertEquals(runUserId().intValue(), ((Number) ext.get("create_by")).intValue(),
                "create_by is the meta-fill's acting user, never the body's 999");
        assertNull(ext.get("update_by"),
                "update_by stays unset on INSERT (the house insertFill fills only "
                        + "create_by; the body's 999 must never persist)");
        assertNotEquals(Timestamp.valueOf("2001-01-01 00:00:00"), ext.get("create_time"),
                "create_time is the DB default (now), never the client body's stamp");
    }

    // ------------------------------- resolve passthrough (three outcomes)

    @Test
    void resolvePassthroughSurfacesExactCandidatesAndNoneWithoutSideEffect() throws Exception {
        long ts = stamp();
        int stubUid = STUB_UID_BASE + 201;

        // exact: the exact-match identity returns with no side effect
        when(patientApi.lookup(any())).thenReturn(org.birdbro.common.core.R.ofSuccess(
                PatientLookupResult.builder()
                        .result(PatientLookupResult.RESULT_MATCHED)
                        .identity(PatientIdentityResponse.builder()
                                .patUid(stubUid)
                                .patId("US3-RESOLVE")
                                .name("us3解析精确" + ts)
                                .idType("01")
                                .idNo("US3RES-" + ts)
                                .build())
                        .build()));
        JsonNode exact = parse(exchangeJson(HttpMethod.POST, RESOLVE_URL,
                "{\"idType\":\"01\",\"idNo\":\"US3RES-" + ts + "\"}", fullPermToken()));
        assertEquals("200", exact.path("code").asText(),
                "the passthrough must succeed: " + exact);
        assertEquals("MATCHED", exact.path("data").path("result").asText(),
                "the exact-match identity returns: " + exact);
        assertEquals(stubUid, exact.path("data").path("identity").path("patUid").asInt(-1),
                "the identity carries the archive's pat_uid");

        // candidates: the soft-match candidate list returns, never an error
        when(patientApi.lookup(any())).thenReturn(org.birdbro.common.core.R.ofSuccess(
                PatientLookupResult.builder()
                        .result(PatientLookupResult.RESULT_CANDIDATES)
                        .candidates(java.util.Arrays.asList(
                                PatientIdentityResponse.builder().patUid(stubUid + 1).build(),
                                PatientIdentityResponse.builder().patUid(stubUid + 2).build()))
                        .build()));
        JsonNode candidates = parse(exchangeJson(HttpMethod.POST, RESOLVE_URL,
                "{\"name\":\"us3解析候选\",\"phone\":\"13900000201\"}", fullPermToken()));
        assertEquals("CANDIDATES", candidates.path("data").path("result").asText(),
                "the soft-match candidate list returns: " + candidates);
        assertEquals(2, candidates.path("data").path("candidates").size(),
                "both candidates ride the list");

        // none: the empty result returns - and NOTHING was created anywhere
        when(patientApi.lookup(any())).thenReturn(org.birdbro.common.core.R.ofSuccess(
                PatientLookupResult.builder()
                        .result(PatientLookupResult.RESULT_NONE)
                        .build()));
        JsonNode none = parse(exchangeJson(HttpMethod.POST, RESOLVE_URL,
                "{\"idType\":\"01\",\"idNo\":\"US3NOHIT-" + ts + "\"}", fullPermToken()));
        assertEquals("NONE", none.path("data").path("result").asText(),
                "the empty result returns as NONE: " + none);
        assertTrue(none.path("data").path("identity").isNull(),
                "NONE carries no identity");

        // the passthrough NEVER writes: the stub band holds no orders after three calls
        Long bandOrders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_order WHERE patient_uid >= ? AND patient_uid < ?",
                Long.class, STUB_UID_BASE + 201, STUB_UID_BASE + 210);
        assertEquals(Long.valueOf(0), bandOrders,
                "the resolve passthrough must have NO side effect on any outcome");
    }

    // --------------------------------------------- perm gates (us:order:*)

    @Test
    void permGatesRefuseTokenlessAndPermlessCallers() throws Exception {
        String name = "us3权限拒绝" + stamp();
        String body = "{\"name\":\"" + name + "\"}";

        // tokenless -> the entry point's 203 envelope
        JsonNode tokenlessCreate = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body, null));
        assertEquals("203", tokenlessCreate.path("code").asText(),
                "a tokenless create answers the entry point's 203: " + tokenlessCreate);
        JsonNode tokenlessPage = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "?pageNum=1&pageSize=10", null, null));
        assertEquals("203", tokenlessPage.path("code").asText(),
                "a tokenless page query answers the entry point's 203");

        // authenticated but perm-less -> the A0301 deny envelope, nothing written
        JsonNode denied = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body, noPermToken()));
        assertEquals("A0301", denied.path("code").asText(),
                "an authenticated perm-less caller is denied A0301: " + denied);
        assertEquals(Long.valueOf(0), countOrdersByName(name),
                "the denied create must have written nothing");

        // the resolve passthrough rides us:order:create (the spec's gate) - perm-less refused
        JsonNode resolveDenied = parse(exchangeJson(HttpMethod.POST, RESOLVE_URL,
                "{\"name\":\"us3解析拒权\"}", noPermToken()));
        assertEquals("A0301", resolveDenied.path("code").asText(),
                "the resolve passthrough is gated us:order:create: " + resolveDenied);
    }

    // ---------------------------------------------------------------- helpers

    /** Creates one BOOKED order whose archive identity carries the given name. */
    private int createOrderWithArchivedName(String name) throws Exception {
        when(patientApi.resolveOrRegister(any())).thenReturn(org.birdbro.common.core.R.ofSuccess(
                PatientResolveOrRegisterResult.builder()
                        .result(PatientResolveOrRegisterResult.RESULT_MATCHED)
                        .identity(PatientIdentityResponse.builder()
                                .patUid(STUB_UID_BASE + 501 + Math.abs(name.hashCode()) % 500)
                                .patId("US3-FILTER")
                                .name(name)
                                .gender(1)
                                .state("RESOLVED")
                                .build())
                        .build()));
        return createOrder(name);
    }

    /** Creates one BOOKED order through the real route (default seam stubs). */
    private int createOrder(String name) throws Exception {
        long ts = stamp();
        String body = "{\"idType\":\"01\",\"idNo\":\"US3ORDER-" + ts + "\","
                + "\"name\":\"" + name + "\",\"gender\":1,\"phone\":\"139" + (ts % 1000000000)
                + "\",\"examItems\":[{\"itemId\":1024,\"itemName\":\"腹部超声\"}]}";
        JsonNode response = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body,
                fullPermToken()));
        assertEquals("200", response.path("code").asText(),
                "the create helper must succeed: " + response);
        int orderId = response.path("data").path("orderId").asInt(-1);
        assertTrue(orderId > 0, "the create helper returns the order id: " + response);
        UsRegistrationFixtures.registerOrder(orderId);
        return orderId;
    }

    /** Creates one BOOKED order with a full US ext block through the real route. */
    private int createOrderWithExt(String name, String pregnancyStatus,
            int gestationalWeeks, String note) throws Exception {
        long ts = stamp();
        String body = "{\"idType\":\"01\",\"idNo\":\"US3EXT-" + ts + "\","
                + "\"name\":\"" + name + "\",\"gender\":2,"
                + "\"examItems\":[{\"itemId\":1024,\"itemName\":\"超声产检\"}],"
                + "\"ext\":{\"pregnancyStatus\":\"" + pregnancyStatus + "\","
                + "\"gestationalWeeks\":" + gestationalWeeks + ",\"note\":\"" + note + "\","
                + "\"applyDept\":\"门诊内科\",\"applyDoctor\":\"李临床\","
                + "\"applyTime\":\"2026-11-24 08:30:00\","
                + "\"applyDeptId\":\"HIS-DEP-01\",\"applyDoctorId\":\"HIS-DOC-07\"}}";
        JsonNode response = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body,
                fullPermToken()));
        assertEquals("200", response.path("code").asText(),
                "the ext create helper must succeed: " + response);
        int orderId = response.path("data").path("orderId").asInt(-1);
        assertTrue(orderId > 0, "the ext create helper returns the order id");
        UsRegistrationFixtures.registerOrder(orderId);
        return orderId;
    }

    /** The page record with the given order id, or null when absent. */
    private JsonNode findRecordByOrderId(JsonNode pageResponse, int orderId) {
        for (JsonNode record : pageResponse.path("data").path("records")) {
            if (record.path("orderId").asInt(-1) == orderId) {
                return record;
            }
        }
        return null;
    }

    private Long countOrdersByName(String name) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_order WHERE patient_snapshot->>'name' = ?",
                Long.class, name);
    }

    private long stamp() {
        return System.currentTimeMillis();
    }

    private ResponseEntity<String> exchangeJson(HttpMethod method, String url, String json,
            String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set("Authorization", "Bearer " + token);
        }
        return restTemplate.exchange(url, method, new HttpEntity<>(json, headers),
                String.class);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }
}
