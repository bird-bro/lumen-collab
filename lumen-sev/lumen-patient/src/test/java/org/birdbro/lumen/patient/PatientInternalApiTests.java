package org.birdbro.lumen.patient;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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

import org.birdbro.lumen.feign.patient.PatientResolveOrRegisterRequest;
import org.birdbro.lumen.patient.dto.PatientArchiveSaveRequest;
import org.birdbro.lumen.patient.entity.PatientMaster;
import org.birdbro.lumen.patient.service.PatientArchiveService;
import org.birdbro.lumen.patient.service.PatientEventOutboxService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Patient INTERNAL service surface proof (us-station-registration-sev tasks 2.1/2.2;
 * spec patient-domain "Internal Service Surface" - the four WHEN/THEN scenarios are the
 * acceptance gate): the internal-key guard refuses foreign callers BEFORE the service
 * layer (scenario 4), the composite resolve-or-register resolves an exact match without
 * a new row (scenario 1) and registers a new archive - PROVISIONAL placeholders
 * included, {@code pat_id} issued - with a conflicting {@code id_no} surfacing the
 * existing {@code A0715} unchanged (scenario 2), and the outbox pull/ack hands out
 * events exactly once (pulled entries stay visible until acked; ack is an idempotent
 * no-op for non-PENDING rows) (scenario 3).
 *
 * <p>House {@code PatientApiTests} mechanics over {@link PatientIntegrationTestBase}:
 * REAL HTTP through the test-server bootstrap, but NO user token - the internal surface
 * is internal-key-guarded only (the system {@code /issue} precedent; never behind user
 * perms), so every call carries {@code X-Internal-Key} instead of an Authorization
 * header. Fixtures follow the {@code PatientUsB2Fixtures} marker + hard-DELETE sweep
 * (user ruling 2026-11-07).</p>
 *
 * <p>The {@code A0715} conflict reaches the composite DETERMINISTICALLY through the
 * creation path's partial-unique-index backstop: a competing row is inserted on a
 * SEPARATE connection and held UNCOMMITTED while the composite runs - the resolution
 * chain's lookups (READ COMMITTED) cannot see it, the composite falls through to
 * archive creation, the master INSERT blocks on the uncommitted index entry, and the
 * competing row is committed mid-flight - so the backstop fires and names the duplicate
 * exactly as the plain create route always has (never a rewritten code).</p>
 *
 * @author birdbro
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatientInternalApiTests extends PatientIntegrationTestBase {

    private static final String RESOLVE_OR_REGISTER_URL =
            "/api/v1/patient/internal/patients/resolve-or-register";
    private static final String OUTBOX_PULL_URL = "/api/v1/patient/internal/events/outbox";
    private static final String OUTBOX_ACK_URL = "/api/v1/patient/internal/events/outbox/";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PatientArchiveService patientArchiveService;

    @Autowired
    private PatientEventOutboxService patientEventOutboxService;

    @BeforeEach
    void sweepBeforeTest() {
        PatientUsB2Fixtures.sweep(jdbcTemplate);
    }

    @AfterAll
    void sweepAfterClass() {
        PatientUsB2Fixtures.sweep(jdbcTemplate);
    }

    // ------------------------------------------- scenario 4: internal-key refusal

    @Test
    void internalKeyRefusalBlocksEveryInternalEndpointBeforeTheServiceLayer() throws Exception {
        long ts = stamp();
        // resolve-or-register without the key: HTTP 401 envelope, no archive created
        ResponseEntity<String> refusedEntity = exchangeJson(HttpMethod.POST,
                RESOLVE_OR_REGISTER_URL,
                "{\"name\":\"usb2拒钥" + ts + "\",\"provisional\":true}", null);
        assertEquals(401, refusedEntity.getStatusCodeValue(),
                "a keyless call must be refused with HTTP 401");
        JsonNode refused = parse(refusedEntity);
        assertEquals("401", refused.path("code").asText(), "the refusal envelope codes 401");
        Long created = countArchivesByName("usb2拒钥" + ts);
        assertEquals(Long.valueOf(0), created,
                "the refusal must happen BEFORE the service layer - no archive row");

        // outbox pull without the key: same refusal
        JsonNode pullRefused = parse(exchangeJson(HttpMethod.GET,
                OUTBOX_PULL_URL + "?limit=10", null, null));
        assertEquals("401", pullRefused.path("code").asText(),
                "the outbox pull is guarded by the same internal key");

        // outbox ack without the key: same refusal
        JsonNode ackRefused = parse(exchangeJson(HttpMethod.POST,
                OUTBOX_ACK_URL + "999999/ack", null, null));
        assertEquals("401", ackRefused.path("code").asText(),
                "the outbox ack is guarded by the same internal key");

        // a WRONG key is equally refused (fail-closed guard, not a presence check)
        JsonNode wrongKey = parse(exchangeJson(HttpMethod.POST, RESOLVE_OR_REGISTER_URL,
                "{\"name\":\"usb2错钥" + ts + "\"}", "not-the-shared-key"));
        assertEquals("401", wrongKey.path("code").asText(),
                "a wrong internal key must be refused exactly like a missing one");
    }

    // --------------------------------- scenario 1: exact match, no new row

    @Test
    void resolveOrRegisterExactMatchReturnsTheExistingIdentityWithoutANewRow() throws Exception {
        long ts = stamp();
        String idNo = "USB2ID-" + ts;
        PatientMaster seeded = patientArchiveService.create(saveRequest(
                PatientUsB2Fixtures.NAME_MARKER + "精确命中" + ts, "01", idNo));
        PatientUsB2Fixtures.registerArchive(seeded.getId());

        JsonNode response = parse(exchangeJson(HttpMethod.POST, RESOLVE_OR_REGISTER_URL,
                resolveBody(seeded.getIdType(), idNo, null, null, null, null),
                TEST_INTERNAL_KEY));
        assertEquals("200", response.path("code").asText(),
                "the composite must succeed through the internal key: " + response);
        assertEquals("MATCHED", response.path("data").path("result").asText(),
                "the exact identity pair resolves decisively: " + response);
        assertEquals(seeded.getId(),
                response.path("data").path("identity").path("patUid").asInt(-1),
                "the identity carries the existing archive's pat_uid");
        assertEquals(seeded.getPatId(),
                response.path("data").path("identity").path("patId").asText(),
                "the identity carries the archive's pat_id");
        assertEquals("RESOLVED",
                response.path("data").path("identity").path("state").asText(),
                "the identity carries the archive state");
        assertTrue(response.path("data").path("candidates").isNull(),
                "no candidate list rides a decisive match");

        Long rows = countArchivesByIdNo(idNo);
        assertEquals(Long.valueOf(1), rows,
                "the exact match must return the existing identity WITHOUT a new row");
    }

    // ---------------- the card projection (patient-identity-card-projection task 1.1)

    @Test
    void resolvedIdentityProjectsTheArchivesLatestCard() throws Exception {
        long ts = stamp();
        String idNo = "USB2ID-" + ts;
        String firstCard = "CARDA-" + ts;
        String secondCard = "CARDB-" + ts;
        // archive one: created WITH a card (the resolve-created path persists it)
        PatientArchiveSaveRequest withCard = saveRequest(
                PatientUsB2Fixtures.NAME_MARKER + "卡投影" + ts, "01", idNo);
        withCard.setCardNo(firstCard);
        PatientMaster seeded = patientArchiveService.create(withCard);
        PatientUsB2Fixtures.registerArchive(seeded.getId());
        // the archive accumulates a SECOND card (a later registration) - direct SQL
        // (higher id = the latest, the server-owned rule under test)
        jdbcTemplate.update(
                "INSERT INTO patient_identifier (patient_id, id_type, id_no) "
                + "VALUES (?, 'card', ?)", seeded.getId(), secondCard);

        // archive two: NO card identifier at all - the null band
        PatientMaster bare = patientArchiveService.create(saveRequest(
                PatientUsB2Fixtures.NAME_MARKER + "无卡投影" + ts, "01", "USB2IDB-" + ts));
        PatientUsB2Fixtures.registerArchive(bare.getId());

        JsonNode withCardResponse = parse(exchangeJson(HttpMethod.POST, RESOLVE_OR_REGISTER_URL,
                resolveBody("01", idNo, null, null, null, null), TEST_INTERNAL_KEY));
        assertEquals("MATCHED", withCardResponse.path("data").path("result").asText());
        // the projection reads the LATEST card identifier (the current visit card),
        // never the first
        assertEquals(secondCard,
                withCardResponse.path("data").path("identity").path("cardNo").asText(null),
                "the identity projects the archive's LATEST card identifier");

        JsonNode bareResponse = parse(exchangeJson(HttpMethod.POST, RESOLVE_OR_REGISTER_URL,
                resolveBody("01", "USB2IDB-" + ts, null, null, null, null), TEST_INTERNAL_KEY));
        assertEquals("MATCHED", bareResponse.path("data").path("result").asText());
        assertTrue(bareResponse.path("data").path("identity").path("cardNo").isNull(),
                "a card-less archive projects a NULL cardNo - never a fabricated number");
    }

    // ----------------------- scenario 2: register-new (placeholder + A0715)

    @Test
    void resolveOrRegisterCreatesAPlaceholderArchiveWithAnIssuedPatId() throws Exception {
        long ts = stamp();
        JsonNode response = parse(exchangeJson(HttpMethod.POST, RESOLVE_OR_REGISTER_URL,
                "{\"name\":\"usb2占位新建" + ts + "\",\"phone\":\"138" + ts + "\","
                        + "\"provisional\":true}", TEST_INTERNAL_KEY));
        assertEquals("200", response.path("code").asText(),
                "no acceptable match must fall back to archive creation: " + response);
        assertEquals("CREATED", response.path("data").path("result").asText(),
                "the no-match branch reports CREATED: " + response);
        int patUid = response.path("data").path("identity").path("patUid").asInt(-1);
        assertTrue(patUid > 0, "the created identity carries a pat_uid: " + response);
        assertFalse(response.path("data").path("identity").path("patId").asText("").isEmpty(),
                "the created archive's pat_id is issued by the existing creation path");
        assertEquals("PROVISIONAL",
                response.path("data").path("identity").path("state").asText(),
                "the provisional flag creates a PROVISIONAL placeholder archive: " + response);
        PatientUsB2Fixtures.registerArchive(patUid);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT archive_status, pat_id FROM patient_master WHERE id = ?", patUid);
        assertEquals("PROVISIONAL", row.get("archive_status"),
                "the placeholder archive persists with the PROVISIONAL state");
        assertNotNull(row.get("pat_id"), "the placeholder row carries the issued pat_id");
    }

    @Test
    void conflictingIdNoSurfacesA0715UnchangedThroughTheCreationBackstop() throws Exception {
        long ts = stamp();
        String idNo = "USB2CFL-" + ts;
        String conflictPatId = "USB2-CONFLICT-" + ts;
        // The competing row lives on a SEPARATE connection, held UNCOMMITTED through the
        // composite's resolution phase (READ COMMITTED cannot see it), and commits
        // mid-flight so the master INSERT blocks on the index entry and then fails
        // through the EXISTING backstop -> A0715, never a rewritten code.
        ExecutorService caller = Executors.newSingleThreadExecutor();
        Connection injector = openTestServerConnection();
        try {
            injector.setAutoCommit(false);
            insertConflictRow(injector, idNo, conflictPatId,
                    PatientUsB2Fixtures.NAME_MARKER + "冲突竞争" + ts);

            Future<ResponseEntity<String>> call = caller.submit(() -> exchangeJson(
                    HttpMethod.POST, RESOLVE_OR_REGISTER_URL,
                    resolveBody("01", idNo, null,
                            PatientUsB2Fixtures.NAME_MARKER + "冲突发起" + ts, null, null),
                    TEST_INTERNAL_KEY));
            Thread.sleep(3000); // the composite is now blocked inside the master INSERT
            injector.commit();  // -> unique-index violation -> backstop lookup -> A0715

            JsonNode response = parse(call.get(30, TimeUnit.SECONDS));
            assertEquals("A0715", response.path("code").asText(),
                    "a conflicting id_no must surface the existing A0715 unchanged: "
                            + response);
            assertTrue(response.path("msg").asText().contains("pat_id=" + conflictPatId),
                    "the conflict payload must name the competing archive's pat_id: "
                            + response);
            Long rows = countArchivesByIdNo(idNo);
            assertEquals(Long.valueOf(1), rows,
                    "the conflicted composite must not have written a second archive row");
        } finally {
            caller.shutdownNow();
            injector.close(); // uncommitted leftover (a failed branch) rolls back on close
        }
        PatientUsB2Fixtures.sweep(jdbcTemplate); // remove the committed competing row
    }

    @Test
    void duplicateIdentityResolvesToTheExistingArchiveInsteadOfAConflict() throws Exception {
        long ts = stamp();
        String idNo = "USB2DUP-" + ts;
        JsonNode first = parse(exchangeJson(HttpMethod.POST, RESOLVE_OR_REGISTER_URL,
                resolveBody("01", idNo, null,
                        PatientUsB2Fixtures.NAME_MARKER + "重复收敛" + ts, null, null),
                TEST_INTERNAL_KEY));
        assertEquals("CREATED", first.path("data").path("result").asText(),
                "the first call registers the archive: " + first);
        int patUid = first.path("data").path("identity").path("patUid").asInt(-1);
        assertTrue(patUid > 0, "the registered identity carries a pat_uid");
        PatientUsB2Fixtures.registerArchive(patUid);

        JsonNode second = parse(exchangeJson(HttpMethod.POST, RESOLVE_OR_REGISTER_URL,
                resolveBody("01", idNo, null,
                        PatientUsB2Fixtures.NAME_MARKER + "重复收敛" + ts, null, null),
                TEST_INTERNAL_KEY));
        assertEquals("MATCHED", second.path("data").path("result").asText(),
                "the exact step must resolve the existing archive (the composite never "
                        + "invents a conflict where the chain resolves): " + second);
        assertEquals(patUid, second.path("data").path("identity").path("patUid").asInt(-1),
                "the second call returns the SAME identity");
        Long rows = countArchivesByIdNo(idNo);
        assertEquals(Long.valueOf(1), rows, "still exactly one archive row");
    }

    // --------------------------------- supporting: branch fidelity of the composite

    @Test
    void softAmbiguousDemographicsReturnCandidatesWithoutAnIdentity() throws Exception {
        long ts = stamp();
        String sharedName = PatientUsB2Fixtures.NAME_MARKER + "软候选" + ts;
        String sharedPhone = "135" + ts;
        PatientMaster candA = patientArchiveService.create(seedWithPhone(sharedName, sharedPhone));
        PatientMaster candB = patientArchiveService.create(seedWithPhone(sharedName, sharedPhone));
        PatientUsB2Fixtures.registerArchive(candA.getId());
        PatientUsB2Fixtures.registerArchive(candB.getId());

        JsonNode response = parse(exchangeJson(HttpMethod.POST, RESOLVE_OR_REGISTER_URL,
                "{\"name\":\"" + sharedName + "\",\"phone\":\"" + sharedPhone + "\"}",
                TEST_INTERNAL_KEY));
        assertEquals("CANDIDATES", response.path("data").path("result").asText(),
                "an ambiguous soft match returns the candidate list, never an error: "
                        + response);
        assertEquals(2, response.path("data").path("candidates").size(),
                "both soft hits are listed");
        assertTrue(response.path("data").path("identity").isNull(),
                "no single identity rides an ambiguous result (the consumer refuses)");
        Long rows = countArchivesByName(sharedName);
        assertEquals(Long.valueOf(2), rows,
                "the candidates branch must not create a duplicate archive");
    }

    @Test
    void typelessIdNoSurfacesA0410Verbatim() throws Exception {
        long ts = stamp();
        JsonNode response = parse(exchangeJson(HttpMethod.POST, RESOLVE_OR_REGISTER_URL,
                "{\"idNo\":\"USB2TYPE-" + ts + "\",\"name\":\"usb2无类型" + ts + "\"}",
                TEST_INTERNAL_KEY));
        assertEquals("A0410", response.path("code").asText(),
                "the creation path's A0410 guard must surface VERBATIM through the "
                        + "internal endpoint (no masking): " + response);
    }

    // ----------------------------- scenario 3: outbox pull/ack exactly once

    @Test
    void outboxPullKeepsEntriesVisibleUntilAckedAndRemovesThemAfter() throws Exception {
        long ts = stamp();
        PatientMaster carrier = patientArchiveService.create(saveRequest(
                PatientUsB2Fixtures.NAME_MARKER + "outbox载体" + ts, null, null));
        PatientUsB2Fixtures.registerArchive(carrier.getId());
        int seeded = 3;
        for (int i = 0; i < seeded; i++) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("usb2Seq", i);
            patientEventOutboxService.append(
                    PatientUsB2Fixtures.EVENT_MARKER + "PULL", carrier.getId(), payload);
        }

        // pull #1: the marker entries are handed out, ordered by id
        JsonNode firstPull = parse(exchangeJson(HttpMethod.GET,
                OUTBOX_PULL_URL + "?limit=200", null, TEST_INTERNAL_KEY));
        assertEquals("200", firstPull.path("code").asText(),
                "the pull must succeed through the internal key: " + firstPull);
        List<Integer> firstIds = markerEntryIds(firstPull);
        assertEquals(seeded, firstIds.size(),
                "every seeded PENDING marker entry is handed out: " + firstPull);
        assertEquals(firstIds.stream().sorted().collect(Collectors.toList()), firstIds,
                "the batch is ordered by id ascending");

        // no ack yet: a re-pull hands the SAME entries out again (exactly once PER ACK)
        JsonNode rePull = parse(exchangeJson(HttpMethod.GET,
                OUTBOX_PULL_URL + "?limit=200", null, TEST_INTERNAL_KEY));
        assertEquals(firstIds, markerEntryIds(rePull),
                "entries remain visible to re-pull until acked");

        // a failed dispatch that skips the ack = exactly this state: still PENDING
        Integer statuses = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient_event_outbox WHERE event_type = ? "
                        + "AND status = 'PENDING'", Integer.class,
                PatientUsB2Fixtures.EVENT_MARKER + "PULL");
        assertEquals(Integer.valueOf(seeded), statuses,
                "a pull alone must not consume anything");

        // ack ONE entry: it stops appearing; the others stay
        int ackedId = firstIds.get(0);
        JsonNode ack = parse(exchangeJson(HttpMethod.POST,
                OUTBOX_ACK_URL + ackedId + "/ack", null, TEST_INTERNAL_KEY));
        assertTrue(ack.path("code").asText().matches("20[04]"),
                "the ack answers with the success envelope: " + ack);
        JsonNode afterAck = parse(exchangeJson(HttpMethod.GET,
                OUTBOX_PULL_URL + "?limit=200", null, TEST_INTERNAL_KEY));
        List<Integer> remaining = markerEntryIds(afterAck);
        assertEquals(seeded - 1, remaining.size(),
                "the acked entry stops appearing in subsequent pulls");
        assertFalse(remaining.contains(ackedId), "the acked id is gone from the pull");

        // the row is KEPT for audit - consumed, not deleted
        String consumedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM patient_event_outbox WHERE id = ?", String.class, ackedId);
        assertEquals("CONSUMED", consumedStatus,
                "the ack flips the status to CONSUMED and keeps the row for audit");
    }

    @Test
    void outboxAckIsAnIdempotentNoOpForNonPendingRowsAndPullCapsTheLimit() throws Exception {
        long ts = stamp();
        PatientMaster carrier = patientArchiveService.create(saveRequest(
                PatientUsB2Fixtures.NAME_MARKER + "outbox上限" + ts, null, null));
        PatientUsB2Fixtures.registerArchive(carrier.getId());
        for (int i = 0; i < 2; i++) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("usb2Seq", i);
            patientEventOutboxService.append(
                    PatientUsB2Fixtures.EVENT_MARKER + "LIMIT", carrier.getId(), payload);
        }

        // pull with limit=1: the batch is CAPPED at exactly one entry
        JsonNode capped = parse(exchangeJson(HttpMethod.GET,
                OUTBOX_PULL_URL + "?limit=1", null, TEST_INTERNAL_KEY));
        assertEquals(1, capped.path("data").size(),
                "the pull must cap the batch at the requested limit: " + capped);
        JsonNode entry = capped.path("data").get(0);
        assertTrue(entry.path("id").asInt(-1) > 0, "the entry carries its id");
        assertEquals(PatientUsB2Fixtures.EVENT_MARKER + "LIMIT",
                entry.path("eventType").asText(), "the entry carries the event type");
        assertTrue(entry.path("payload").isTextual(),
                "the payload rides as the raw jsonb document string");
        assertTrue(entry.path("payload").asText().contains("usb2Seq"),
                "the payload string carries the jsonb object's keys: " + entry);
        assertTrue(entry.path("createTime").asText("").length() > 0,
                "the entry carries the audit timestamps");

        // ack a row that is NOT PENDING (already consumed) - a no-op SUCCESS
        int entryId = entry.path("id").asInt();
        JsonNode firstAck = parse(exchangeJson(HttpMethod.POST,
                OUTBOX_ACK_URL + entryId + "/ack", null, TEST_INTERNAL_KEY));
        assertTrue(firstAck.path("code").asText().matches("20[04]"),
                "the first ack consumes the row: " + firstAck);
        JsonNode replayAck = parse(exchangeJson(HttpMethod.POST,
                OUTBOX_ACK_URL + entryId + "/ack", null, TEST_INTERNAL_KEY));
        assertTrue(replayAck.path("code").asText().matches("20[04]"),
                "acking a non-PENDING row is an idempotent no-op success: " + replayAck);

        // ack a row that does not exist at all - equally a no-op success
        JsonNode missingAck = parse(exchangeJson(HttpMethod.POST,
                OUTBOX_ACK_URL + "999999999/ack", null, TEST_INTERNAL_KEY));
        assertTrue(missingAck.path("code").asText().matches("20[04]"),
                "acking a missing row is an idempotent no-op success: " + missingAck);
    }

    // ---------------------------------------------------------------- helpers

    /** Per-call timestamp suffix for the fixture namespaces. */
    private long stamp() {
        return System.currentTimeMillis();
    }

    /** Builds the composite request body JSON from its resolve-chain keys. */
    private String resolveBody(String idType, String idNo, String cardNo, String name,
            Integer gender, String phone) throws Exception {
        PatientResolveOrRegisterRequest request = new PatientResolveOrRegisterRequest();
        request.setIdType(idType);
        request.setIdNo(idNo);
        request.setCardNo(cardNo);
        request.setName(name);
        request.setGender(gender);
        request.setPhone(phone);
        return objectMapper.writeValueAsString(request);
    }

    /** The service-side save request the seeding helpers use. */
    private PatientArchiveSaveRequest saveRequest(String name, String idType, String idNo) {
        PatientArchiveSaveRequest request = new PatientArchiveSaveRequest();
        request.setName(name);
        request.setIdType(idType);
        request.setIdNo(idNo);
        return request;
    }

    /** The soft-candidate seed: same name AND phone on both archives (the soft key). */
    private PatientArchiveSaveRequest seedWithPhone(String name, String phone) {
        PatientArchiveSaveRequest request = saveRequest(name, null, null);
        request.setPhone(phone);
        return request;
    }

    /** The ids of THIS suite's marker entries in a pull response, in payload order. */
    private List<Integer> markerEntryIds(JsonNode pullResponse) {
        return StreamSupport.stream(pullResponse.path("data").spliterator(), false)
                .filter(node -> node.path("eventType").asText()
                        .startsWith(PatientUsB2Fixtures.EVENT_MARKER))
                .map(node -> node.path("id").asInt())
                .collect(Collectors.toList());
    }

    private Long countArchivesByIdNo(String idNo) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient_master WHERE id_no = ? AND deleted = 0",
                Long.class, idNo);
    }

    private Long countArchivesByName(String name) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient_master WHERE name = ? AND deleted = 0",
                Long.class, name);
    }

    /**
     * Plain-JDBC connection to the team test server (the {@code PatientApiTestBase}
     * resolution: {@code currentSchema=patient,public}, explicit qualification) - used
     * by the A0715 backstop test to hold an UNCOMMITTED competing identity row.
     */
    private Connection openTestServerConnection() throws SQLException {
        PatientTestEnv env = PatientTestEnv.load();
        Map<String, String> required = env.require(
                PatientTestEnv.PG_HOST, PatientTestEnv.PG_PORT,
                PatientTestEnv.PG_USER, PatientTestEnv.PG_PASSWORD);
        String database = env.optional(PatientTestEnv.DB, PatientTestEnv.DEFAULT_DB);
        String url = "jdbc:postgresql://" + required.get(PatientTestEnv.PG_HOST) + ":"
                + required.get(PatientTestEnv.PG_PORT) + "/" + database
                + "?currentSchema=patient,public";
        return DriverManager.getConnection(url, required.get(PatientTestEnv.PG_USER),
                required.get(PatientTestEnv.PG_PASSWORD));
    }

    /** Inserts one competing identity row (id_type 01) WITHOUT committing. */
    private void insertConflictRow(Connection connection, String idNo, String patId,
            String name) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO patient_master (id_type, id_no, name, pat_id, archive_status) "
                        + "VALUES ('01', ?, ?, ?, 'RESOLVED')")) {
            insert.setString(1, idNo);
            insert.setString(2, name);
            insert.setString(3, patId);
            insert.executeUpdate();
        }
    }

    private ResponseEntity<String> exchangeJson(HttpMethod method, String url, String json,
            String internalKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalKey != null) {
            headers.set("X-Internal-Key", internalKey);
        }
        return restTemplate.exchange(url, method, new HttpEntity<>(json, headers),
                String.class);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }
}
