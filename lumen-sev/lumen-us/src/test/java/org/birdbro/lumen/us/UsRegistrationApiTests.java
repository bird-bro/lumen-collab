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
import org.birdbro.lumen.feign.number.NumberIssueResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void createBookedOrderIssuesAccessionLinksIdentityAndPersistsExt() throws Exception {
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
                + "\"name\":\"us3建档张三\",\"gender\":2,\"phone\":\"13800000101\","
                + "\"age\":\"30\",\"patientSource\":\"门诊\","
                + "\"examItems\":[{\"itemId\":1024,\"itemName\":\"腹部超声\"}],"
                + "\"emgFlag\":1,"
                + "\"ext\":{\"pregnancyStatus\":\"孕12周\",\"gestationalWeeks\":12,"
                + "\"note\":\"us3 ext 备注\"}}";
        JsonNode response = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body,
                fullPermToken()));
        assertEquals("200", response.path("code").asText(),
                "the create must succeed through the real chain: " + response);
        int orderId = response.path("data").path("orderId").asInt(-1);
        assertTrue(orderId > 0, "the create returns the persisted order id: " + response);
        assertEquals(STUB_ACCESSION_PREFIX + "ACC001", response.path("data").path("accessionNo").asText(),
                "the accession rides the platform number issued INSIDE the creation path");
        assertEquals("BOOKED", response.path("data").path("status").asText(),
                "the entry state is BOOKED (the booking surface)");
        UsRegistrationFixtures.registerOrder(orderId);

        // the order document: the port identity linked, the snapshot stored, the
        // exam-item references + emg flag carried
        Map<String, Object> order = jdbcTemplate.queryForMap(
                "SELECT status, accession_no, patient_uid, emg_flag, "
                        + "patient_snapshot->>'name' AS snap_name, "
                        + "patient_snapshot->>'patId' AS snap_pat_id, "
                        + "patient_snapshot->>'age' AS snap_age, "
                        + "patient_snapshot->>'patientSource' AS snap_source "
                        + "FROM exam_order WHERE id = ?", orderId);
        assertEquals("BOOKED", order.get("status"), "the order persists with status BOOKED");
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
                "SELECT pregnancy_status, gestational_weeks, note FROM us_registration_ext "
                        + "WHERE order_id = ?", orderId);
        assertEquals("孕12周", ext.get("pregnancy_status"), "the ext block persists 1:1");
        assertEquals(12, ((Number) ext.get("gestational_weeks")).intValue(),
                "the gestational weeks persist");
        assertEquals("us3 ext 备注", ext.get("note"), "the ext note persists");

        // the aggregate members: the kernel inserted the 1:1 execution + report members
        Integer executionRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_execution WHERE order_id = ?", Integer.class, orderId);
        Integer reportRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_report WHERE order_id = ?", Integer.class, orderId);
        assertEquals(Integer.valueOf(1), executionRows, "the execution member exists");
        assertEquals(Integer.valueOf(1), reportRows, "the report placeholder member exists");
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
        int orderId = createOrder("us3登记流转" + stamp());
        JsonNode response = parse(exchangeJson(HttpMethod.POST,
                ORDERS_URL + "/" + orderId + "/register", null, fullPermToken()));
        assertEquals("200", response.path("code").asText(),
                "the 登记 transition must succeed: " + response);
        assertEquals("REGISTERED", response.path("data").asText(),
                "BOOKED -> REGISTERED is the legal 登记 edge");
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM exam_order WHERE id = ?", String.class, orderId);
        assertEquals("REGISTERED", status, "the transition persists");
    }

    @Test
    void illegalTransitionRefusalSurfacesKernelMessageVerbatim() throws Exception {
        int orderId = createOrder("us3非法迁移" + stamp());
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
        // BOOKED -> BOOKING_CANCELLED with the reason stored
        int bookedId = createOrder("us3取消预约" + stamp());
        JsonNode bookedCancel = parse(exchangeJson(HttpMethod.POST,
                ORDERS_URL + "/" + bookedId + "/cancel",
                "{\"reason\":\"us3取消原因-预约\"}", fullPermToken()));
        assertEquals("BOOKING_CANCELLED", bookedCancel.path("data").asText(),
                "the cancel branch from BOOKED lands BOOKING_CANCELLED: " + bookedCancel);
        String bookedReason = jdbcTemplate.queryForObject(
                "SELECT cancel_reason FROM exam_order WHERE id = ?", String.class, bookedId);
        assertEquals("us3取消原因-预约", bookedReason, "the reason is stored on the branch");

        // REGISTERED -> CANCELLED with the reason stored
        int registeredId = createOrder("us3取消在册" + stamp());
        exchangeJson(HttpMethod.POST, ORDERS_URL + "/" + registeredId + "/register",
                null, fullPermToken());
        JsonNode registeredCancel = parse(exchangeJson(HttpMethod.POST,
                ORDERS_URL + "/" + registeredId + "/cancel",
                "{\"reason\":\"us3取消原因-在册\"}", fullPermToken()));
        assertEquals("CANCELLED", registeredCancel.path("data").asText(),
                "the cancel branch from REGISTERED lands CANCELLED: " + registeredCancel);
        String registeredReason = jdbcTemplate.queryForObject(
                "SELECT cancel_reason FROM exam_order WHERE id = ?", String.class, registeredId);
        assertEquals("us3取消原因-在册", registeredReason, "the reason is stored on the branch");
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
        assertEquals("us3ext档案", withExt.path("patientSnapshot").path("name").asText(),
                "the page carries the patient identity from the jsonb snapshot (the "
                        + "delivered archive fields win)");
        assertEquals("US3-EXT", withExt.path("patientSnapshot").path("patId").asText(),
                "the snapshot carries the delivered pat_id");
        assertTrue(withExt.path("accessionNo").asText("").startsWith(STUB_ACCESSION_PREFIX),
                "the page carries the accession");

        JsonNode withoutExt = findRecordByOrderId(response, withoutExtId);
        assertNotNull(withoutExt, "the ext-less order renders in the page");
        assertTrue(withoutExt.path("pregnancyStatus").isNull(),
                "an order without an ext row carries null ext fields (the 1:1 table "
                        + "exists only for orders that carry US fields)");
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
                ORDERS_URL + "?pageNum=1&pageSize=50&status=BOOKED", null, fullPermToken()));
        assertEquals("200", byStatus.path("code").asText(), "the status filter must succeed");
        for (JsonNode record : byStatus.path("data").path("records")) {
            assertEquals("BOOKED", record.path("status").asText(),
                    "every filtered record is BOOKED");
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
                + "\"gestationalWeeks\":" + gestationalWeeks + ",\"note\":\"" + note + "\"}}";
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
