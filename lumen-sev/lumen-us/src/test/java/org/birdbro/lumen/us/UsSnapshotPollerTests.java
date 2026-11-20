package org.birdbro.lumen.us;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import org.birdbro.common.core.R;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.feign.patient.PatientApi;
import org.birdbro.lumen.feign.patient.PatientIdentityResponse;
import org.birdbro.lumen.feign.patient.PatientOutboxEntry;
import org.birdbro.lumen.feign.patient.PatientResolveOrRegisterResult;
import org.birdbro.lumen.core.service.ExamOrderAggregateService;
import org.birdbro.lumen.core.mapper.ExamOrderMapper;
import org.birdbro.lumen.us.poller.PatientSnapshotPoller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The snapshot-poller proof (us-station-registration-sev task 4.1, design D5/D8;
 * specs/us-station "Snapshot Refresh Wiring" - the two WHEN/THEN scenarios ARE the
 * acceptance gate). The outbox pull/ack rides the Feign seam stubs (the suite base's
 * {@code @MockBean} - no patient service runs in this JVM; the patient-side mechanism is
 * proven by the patient module's own task-2.2 tests) and the entries are SEEDED as
 * crafted raw-jsonb payload strings - the same shapes the patient outbox writer emits
 * ({@code BeanUtil.beanToMap} of the updated archive for PATIENT_CHANGED, the
 * {@code {survivorId, mergedId}} pair for PATIENT_MERGED). The kernel side is REAL:
 * orders persist in the shared test server's us schema (created through the REAL HTTP
 * registration surface), the snapshot refresh runs the kernel aggregate service, the
 * merge repoint runs the us-side mapper update.
 *
 * <p>Polling is driven DIRECTLY through {@code pollOnce()} - the scheduled wrapper never
 * fires inside a suite (the shared base pins the poll delay to one hour), so every
 * assertion is deterministic. Failure disciplines proven: poisoned-entry isolation (the
 * batch continues, the poisoned row never acks), transport doubt (no acks, replay next
 * pass), the kernel ISSUED-guard refusal (deterministic - delivered but not applicable -
 * the entry still acks), and the ack-transport failure (the dispatch stands, replay
 * re-applies idempotently).</p>
 *
 * @author birdbro
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsSnapshotPollerTests extends UsApiTestBase {

    private static final String ORDERS_URL = "/api/v1/us/orders";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PatientSnapshotPoller poller;

    @BeforeEach
    void sweepBeforeTest() {
        UsRegistrationFixtures.sweep(jdbcTemplate);
    }

    @AfterAll
    void sweepAfterClass() {
        UsRegistrationFixtures.sweep(jdbcTemplate);
    }

    // ------------------- Scenario: Patient change refreshes open orders (+ ack)

    @Test
    void changedEntryRefreshesLiveOrderSnapshotAndAcks() throws Exception {
        int uid = STUB_UID_BASE + 601;
        int orderId = createOrder(uid, "us3轮询档案");

        when(patientApi.pullOutbox(anyInt())).thenReturn(R.ofSuccess(Collections.singletonList(
                entry(9001, "PATIENT_CHANGED", changedPayload(uid)))));
        poller.pollOnce();

        verify(patientApi).ackOutbox(9001);
        Map<String, Object> snapshot = jdbcTemplate.queryForMap(
                "SELECT patient_snapshot->>'name' AS snap_name, "
                        + "patient_snapshot->>'patId' AS snap_pat_id, "
                        + "patient_snapshot->>'gender' AS snap_gender, "
                        + "patient_snapshot->>'phone' AS snap_phone, "
                        + "patient_snapshot->>'age' AS snap_age, "
                        + "patient_snapshot->>'patientSource' AS snap_source "
                        + "FROM exam_order WHERE id = ?", orderId);
        assertEquals("us3轮询改名", snapshot.get("snap_name"),
                "the delivered archive name refreshed the jsonb snapshot");
        assertEquals("US3-POLL", snapshot.get("snap_pat_id"),
                "the delivered pat_id refreshed the snapshot");
        assertEquals("2", snapshot.get("snap_gender"),
                "the delivered gender refreshed the snapshot");
        assertEquals("13800000601", snapshot.get("snap_phone"),
                "the delivered phone refreshed the snapshot");
        assertEquals("33", snapshot.get("snap_age"),
                "the exam-time age survives the refresh (patient decision #4: the poller "
                        + "never touches exam-time demographics)");
        assertEquals("门诊", snapshot.get("snap_source"),
                "the exam-time patient source survives the refresh");
    }

    // ------------------- Scenario: Merge repoints live orders (+ ack after both steps)

    @Test
    void mergedEntryRepointsLiveOrdersKeepsCancelledHistoryAndAcks() throws Exception {
        int mergedUid = STUB_UID_BASE + 611;
        int survivorUid = STUB_UID_BASE + 612;
        int liveId = createOrder(mergedUid, "us3合并活单");
        int cancelledId = createOrder(mergedUid, "us3合并废单");
        // the cancel branch through the REAL surface: BOOKED -> BOOKING_CANCELLED
        JsonNode cancelled = parse(exchangeJson(HttpMethod.POST,
                ORDERS_URL + "/" + cancelledId + "/cancel",
                "{\"reason\":\"us3合并前取消\"}", fullPermToken()));
        assertEquals("BOOKING_CANCELLED", cancelled.path("data").asText(),
                "the cancelled order sits in the cancel state before the merge: " + cancelled);

        when(patientApi.pullOutbox(anyInt())).thenReturn(R.ofSuccess(Collections.singletonList(
                entry(9002, "PATIENT_MERGED",
                        "{\"survivorId\":" + survivorUid + ",\"mergedId\":" + mergedUid + "}"))));
        poller.pollOnce();

        verify(patientApi).ackOutbox(9002);
        assertEquals(survivorUid, uidOf(liveId),
                "the live order repointed to the surviving uid");
        assertEquals(mergedUid, uidOf(cancelledId),
                "the historical cancelled order keeps the merged-away uid (the live-set "
                        + "predicate excludes the cancel states)");
        assertEquals("us3合并活单", snapshotName(liveId),
                "the merge payload delivers no archive fields today (survivorId/mergedId "
                        + "only) - the refresh overlay is empty and the snapshot content "
                        + "stays; the uniform dispatch path applies the survivor fields "
                        + "the day the payload carries them");
    }

    // --------------------------------- poisoned-entry isolation (stays PENDING)

    @Test
    void poisonedEntryIsolatedHealthyBatchStillDispatches() throws Exception {
        int uid = STUB_UID_BASE + 621;
        int orderId = createOrder(uid, "us3毒隔离档案");

        when(patientApi.pullOutbox(anyInt())).thenReturn(R.ofSuccess(java.util.Arrays.asList(
                entry(9003, "PATIENT_CHANGED", changedPayload(uid)),
                entry(9004, "PATIENT_CHANGED", "not-a-json-object{{{"))));
        poller.pollOnce();

        verify(patientApi).ackOutbox(9003);
        verify(patientApi, never()).ackOutbox(9004);
        assertEquals("us3轮询改名", snapshotName(orderId),
                "the healthy entry dispatched despite the poisoned one (one poisoned entry "
                        + "must not block the batch)");
    }

    @Test
    void unknownEventTypeStaysPendingNeverAcked() {
        when(patientApi.pullOutbox(anyInt())).thenReturn(R.ofSuccess(Collections.singletonList(
                entry(9005, "PATIENT_ARCHIVED_FUTURE", "{\"id\":" + (STUB_UID_BASE + 622) + "}"))));
        poller.pollOnce();

        verify(patientApi, never()).ackOutbox(any());
    }

    // ------------------------------- transport doubt (skip batch / replay safely)

    @Test
    void pullTransportFailureSkipsTheBatchWithoutAnyAck() {
        when(patientApi.pullOutbox(anyInt())).thenThrow(new RuntimeException("connection reset"));
        assertDoesNotThrow(() -> poller.pollOnce(),
                "a transport failure must never escape the poller");
        verify(patientApi, never()).ackOutbox(any());
    }

    @Test
    void sentinelAssertionErrorFallbackDoesNotEscapeThePoller() {
        // the Sentinel feign handler (spring-cloud-alibaba 2021.1) wraps the fail-fast
        // fallback in an AssertionError - an Error, NOT an Exception - so a plain
        // catch (Exception) lets it escape to the scheduler's error handler (live
        // finding 2026-11-20: patient down -> scheduler ERROR spam every poll while
        // nothing was wrongly acked). The transport-doubt net must catch it too.
        when(patientApi.pullOutbox(anyInt())).thenThrow(new AssertionError(
                new BusinessException("C0001",
                        "调用第三方服务出错 - 患者服务不可达，outbox 拉取失败（fail-fast，绝不伪造投递）")));
        assertDoesNotThrow(() -> poller.pollOnce(),
                "the Sentinel AssertionError wrapper is transport doubt - never escapes");
        verify(patientApi, never()).ackOutbox(any());
    }

    @Test
    void ackTransportFailureKeepsTheDispatchAndReplaysSafely() throws Exception {
        int uid = STUB_UID_BASE + 631;
        int orderId = createOrder(uid, "us3ack失败档案");

        when(patientApi.pullOutbox(anyInt())).thenReturn(R.ofSuccess(Collections.singletonList(
                entry(9006, "PATIENT_CHANGED", changedPayload(uid)))));
        when(patientApi.ackOutbox(9006)).thenThrow(new RuntimeException("ack connection reset"));
        assertDoesNotThrow(() -> poller.pollOnce());

        verify(patientApi).ackOutbox(9006);
        assertEquals("us3轮询改名", snapshotName(orderId),
                "the dispatch stands (the refresh is an idempotent overwrite - the replay "
                        + "next pass re-applies safely, the row stays PENDING)");
    }

    // --------------- kernel ISSUED-guard refusal: delivered but not applicable -> ack

    @Test
    void kernelIssuedReportRefusalStillAcksTheDeterministicEntry() throws Exception {
        int uid = STUB_UID_BASE + 641;
        int orderId = createOrder(uid, "us3已出报告档案");
        jdbcTemplate.update("UPDATE exam_report SET status = 'ISSUED' WHERE order_id = ?",
                orderId);

        when(patientApi.pullOutbox(anyInt())).thenReturn(R.ofSuccess(Collections.singletonList(
                entry(9007, "PATIENT_CHANGED", changedPayload(uid)))));
        poller.pollOnce();

        verify(patientApi).ackOutbox(9007);
        assertEquals("us3已出报告档案", snapshotName(orderId),
                "the kernel refused the refresh (医疗文书不静默改) - the jsonb stays "
                        + "untouched, and the DETERMINISTIC refusal still acks the entry "
                        + "(re-pulling forever cannot change the report state)");
    }

    // ------------------------------- limit clamping (pure constructor, no context)

    @Test
    void limitClampsNonPositiveToTheBatchDefault() {
        PatientApi api = Mockito.mock(PatientApi.class);
        ExamOrderMapper mapper = Mockito.mock(ExamOrderMapper.class);
        ExamOrderAggregateService aggregate = Mockito.mock(ExamOrderAggregateService.class);

        new PatientSnapshotPoller(api, mapper, aggregate, 0).pollOnce();
        verify(api).pullOutbox(50);
        new PatientSnapshotPoller(api, mapper, aggregate, -3).pollOnce();
        verify(api, Mockito.times(2)).pullOutbox(50);
        new PatientSnapshotPoller(api, mapper, aggregate, 7).pollOnce();
        verify(api).pullOutbox(7);
    }

    // ---------------------------------------------------------------- helpers

    /**
     * One seeded outbox entry - the raw-jsonb payload string is EXACTLY what the pull
     * contract hands over (the consumer parses it with its own mapper).
     */
    private static PatientOutboxEntry entry(int id, String eventType, String payload) {
        return PatientOutboxEntry.builder().id(id).eventType(eventType).payload(payload).build();
    }

    /**
     * The PATIENT_CHANGED payload shape the patient outbox writer emits:
     * {@code BeanUtil.beanToMap} of the UPDATED archive - the linkage key rides
     * {@code id}, the snapshot's archive fields ride patId/name/gender/phone, and
     * irrelevant archive keys ride along (the parse ignores them).
     */
    private static String changedPayload(int uid) {
        return "{\"id\":" + uid + ",\"patId\":\"US3-POLL\",\"name\":\"us3轮询改名\","
                + "\"gender\":2,\"phone\":\"13800000601\","
                + "\"idType\":\"01\",\"idNo\":\"US3POLL-NOISE\",\"pinyin\":\"us3lunxun\","
                + "\"archiveStatus\":\"RESOLVED\",\"birthDate\":\"1999-09-09\","
                + "\"nation\":\"01\",\"address\":\"us3 noise\"}";
    }

    /** Creates one BOOKED order linked to the given stub uid through the real route. */
    private int createOrder(int uid, String name) throws Exception {
        when(patientApi.resolveOrRegister(any())).thenReturn(R.ofSuccess(
                PatientResolveOrRegisterResult.builder()
                        .result(PatientResolveOrRegisterResult.RESULT_MATCHED)
                        .identity(PatientIdentityResponse.builder()
                                .patUid(uid)
                                .patId("US3-POLL")
                                .name(name)
                                .gender(1)
                                .phone("13800000777")
                                .state("RESOLVED")
                                .build())
                        .build()));
        String body = "{\"idType\":\"01\",\"idNo\":\"US3POLLORDER-" + stamp() + "\","
                + "\"name\":\"" + name + "\",\"gender\":1,\"phone\":\"13800000777\","
                + "\"age\":\"33\",\"patientSource\":\"门诊\","
                + "\"examItems\":[{\"itemId\":1024,\"itemName\":\"腹部超声\"}]}";
        JsonNode response = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body,
                fullPermToken()));
        assertEquals("200", response.path("code").asText(),
                "the create helper must succeed: " + response);
        int orderId = response.path("data").path("orderId").asInt(-1);
        assertTrue(orderId > 0, "the create helper returns the order id: " + response);
        UsRegistrationFixtures.registerOrder(orderId);
        return orderId;
    }

    private Integer uidOf(int orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT patient_uid FROM exam_order WHERE id = ?", Integer.class, orderId);
    }

    private String snapshotName(int orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT patient_snapshot->>'name' FROM exam_order WHERE id = ?",
                String.class, orderId);
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
