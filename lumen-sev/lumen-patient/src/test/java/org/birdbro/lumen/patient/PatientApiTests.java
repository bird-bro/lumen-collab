package org.birdbro.lumen.patient;

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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Patient API-surface proof (patient-domain tasks 4.1 + the coordinator's oid
 * amendment): every route's happy path over the REAL security chain - archive
 * create (returned id + pat_id + the principal's issuer org into {@code
 * patient_master.oid}), by-id fetch, demographics update, the paged multi-number +
 * name/pinyin search, the resolve-chain branches, the idempotent resolve-or-merge,
 * the A0715 duplicate-identity conflict payload naming the duplicate - and the
 * number-rule CRUD round-trip with its decision-5 semantics (stamp default, runtime
 * {@code serial_cur} untouched by CRUD, A0714 active-rule conflict, active-rule
 * delete rejection, field-level A0400 validation).
 *
 * <p>House {@code AuthDeviceApiTests} mechanics: minted run-identity tokens (the
 * {@code PatientApiTestBase} pattern), timestamped fixtures, hard-deleted afterwards
 * (user ruling 2026-11-07). Archives consume real {@code pat_id} serials (accepted
 * residue - gap-tolerant).</p>
 *
 * @author birdbro
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatientApiTests extends PatientApiTestBase {

    private static final String PATIENTS_URL = "/api/v1/patient/patients";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void sweepBeforeTest() {
        PatientB4Fixtures.sweep(jdbcTemplate);
    }

    @AfterAll
    void sweepAfterClass() {
        PatientB4Fixtures.sweep(jdbcTemplate);
    }

    // ------------------------------------------------------- archive routes

    @Test
    void createArchiveReturnsIdPatIdAndIssuerOid() throws Exception {
        long ts = stamp();
        String body = "{\"idType\":\"01\",\"idNo\":\"B4ID-" + ts + "\",\"name\":\"b4建档张三"
                + ts + "\",\"gender\":1,\"phone\":\"138" + ts + "\"}";
        JsonNode created = parse(exchangeJson(HttpMethod.POST, PATIENTS_URL, body, fullPermToken()));
        assertEquals("200", created.path("code").asText(), "the create must succeed: " + created);
        int id = created.path("data").path("id").asInt(-1);
        assertTrue(id > 0, "the create must return the new archive id: " + created);
        assertFalse(created.path("data").path("patId").asText("").isEmpty(),
                "the create must return the issued pat_id");
        assertEquals("RESOLVED", created.path("data").path("archiveStatus").asText(),
                "a create without the provisional flag is RESOLVED");
        PatientB4Fixtures.registerArchive(id);

        // b4 amendment 1: the issuer org rides the JWT principal (UserSign.entry.oid),
        // never the request body - the row carries the run identity's distinctive oid.
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT oid FROM patient_master WHERE id = ?", id);
        assertEquals(PatientApiTestBase.RUN_OID, ((Number) row.get("oid")).intValue(),
                "patient_master.oid must carry the principal's entry.oid");
    }

    @Test
    void getByIdFetchesTheArchiveByRoute() throws Exception {
        int id = createArchive("b4取档" + stamp(), null, null);
        JsonNode fetched = parse(exchangeJson(HttpMethod.GET, PATIENTS_URL + "/" + id,
                null, fullPermToken()));
        assertEquals("200", fetched.path("code").asText(), "the fetch must succeed: " + fetched);
        assertEquals(id, fetched.path("data").path("id").asInt(-1),
                "the fetched archive must be the created one");
        assertFalse(fetched.path("data").path("patId").asText("").isEmpty(),
                "the fetch carries the pat_id");
    }

    @Test
    void updateChangesDemographicsThroughTheRoute() throws Exception {
        long ts = stamp();
        int id = createArchive("b4改前" + ts, "01", "B4UP-" + ts);
        String body = "{\"idType\":\"01\",\"idNo\":\"B4UP-" + ts + "\",\"name\":\"b4改后"
                + ts + "\",\"phone\":\"139" + ts + "\"}";
        JsonNode updated = parse(exchangeJson(HttpMethod.PUT, PATIENTS_URL + "/" + id, body,
                fullPermToken()));
        assertEquals("200", updated.path("code").asText(), "the update must succeed: " + updated);
        assertEquals("b4改后" + ts, updated.path("data").path("name").asText(),
                "the route returns the UPDATED archive");
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT name FROM patient_master WHERE id = ?", id);
        assertEquals("b4改后" + ts, row.get("name"), "the update must persist");
    }

    @Test
    void archivePageSearchesByPinyinPatIdAndName() throws Exception {
        long ts = stamp();
        int id = createArchive("b4查张三" + ts, null, null);
        String patId = jdbcTemplate.queryForObject(
                "SELECT pat_id FROM patient_master WHERE id = ?", String.class, id);
        String storedPinyin = jdbcTemplate.queryForObject(
                "SELECT pinyin FROM patient_master WHERE id = ?", String.class, id);
        assertNotNull(storedPinyin, "the server generates the pinyin");
        // decision 11: the stored pinyin is NORMALIZED space-stripped lowercase.
        assertFalse(storedPinyin.contains(" "), "the stored pinyin must be space-stripped: "
                + storedPinyin);
        assertEquals(storedPinyin, storedPinyin.toLowerCase(),
                "the stored pinyin must be lowercase: " + storedPinyin);

        // name contains
        JsonNode byName = parse(exchangeJson(HttpMethod.GET,
                PATIENTS_URL + "?pageNum=1&pageSize=50&name=b4查张三" + ts, null, fullPermToken()));
        assertEquals("200", byName.path("code").asText(), "the page must succeed: " + byName);
        assertEquals(1, byName.path("data").path("total").asLong(-1),
                "the name filter must isolate the archive: " + byName);

        // pinyin prefix on the normalized column (decision 11 - plain LIKE 'x%')
        JsonNode byPinyin = parse(exchangeJson(HttpMethod.GET,
                PATIENTS_URL + "?pageNum=1&pageSize=50&pinyin=" + storedPinyin, null,
                fullPermToken()));
        assertEquals(1, byPinyin.path("data").path("total").asLong(-1),
                "the normalized pinyin prefix must find the archive: " + byPinyin);

        // pat_id exact
        JsonNode byPatId = parse(exchangeJson(HttpMethod.GET,
                PATIENTS_URL + "?pageNum=1&pageSize=50&patId=" + patId, null, fullPermToken()));
        assertEquals(id, byPatId.path("data").path("records").get(0).path("id").asInt(-1),
                "the pat_id exact filter must return the archive");
    }

    @Test
    void resolveChainRunsThroughTheRoute() throws Exception {
        long ts = stamp();
        // exact (id_type, id_no) -> MATCHED
        int exactId = createArchive("b4解析命中" + ts, "01", "B4RE-" + ts);
        JsonNode matched = parse(exchangeJson(HttpMethod.POST, PATIENTS_URL + "/resolve",
                "{\"idType\":\"01\",\"idNo\":\"B4RE-" + ts + "\"}", fullPermToken()));
        assertEquals("200", matched.path("code").asText(), "resolve must succeed: " + matched);
        assertEquals("MATCHED", matched.path("data").path("result").asText(),
                "the exact identity resolves decisively: " + matched);
        assertEquals(exactId, matched.path("data").path("patient").path("id").asInt(-1),
                "the matched archive must be the created one");

        // soft (name + phone) -> CANDIDATES (a list, never an error)
        String sharedName = "b4候选" + ts;
        String sharedPhone = "135" + ts;
        int candA = createArchiveWithPhone(sharedName, sharedPhone);
        int candB = createArchiveWithPhone(sharedName, sharedPhone);
        JsonNode candidates = parse(exchangeJson(HttpMethod.POST, PATIENTS_URL + "/resolve",
                "{\"name\":\"" + sharedName + "\",\"phone\":\"" + sharedPhone + "\"}",
                fullPermToken()));
        assertEquals("200", candidates.path("code").asText(),
                "the soft branch is HTTP-200 envelope semantics: " + candidates);
        assertEquals("CANDIDATES", candidates.path("data").path("result").asText(),
                "two same name+phone archives demote to candidates: " + candidates);
        assertEquals(2, candidates.path("data").path("candidates").size(),
                "both soft hits must be listed");

        // nothing matches -> CREATED per the provisional flag
        JsonNode created = parse(exchangeJson(HttpMethod.POST, PATIENTS_URL + "/resolve",
                "{\"name\":\"b4新建" + ts + "\",\"phone\":\"136" + ts + "\",\"provisional\":false}",
                fullPermToken()));
        assertEquals("CREATED", created.path("data").path("result").asText(),
                "nothing matched must create: " + created);
        int createdId = created.path("data").path("patient").path("id").asInt(-1);
        assertTrue(createdId > 0, "the created branch returns the new archive");
        PatientB4Fixtures.registerArchive(createdId);
        PatientB4Fixtures.registerArchive(exactId);
        PatientB4Fixtures.registerArchive(candA);
        PatientB4Fixtures.registerArchive(candB);
    }

    @Test
    void resolveOrMergeMergesAndReplaysIdempotently() throws Exception {
        long ts = stamp();
        int survivor = createArchive("b4幸存" + ts, "01", "B4SV-" + ts);
        int placeholderId = createPlaceholder();
        PatientB4Fixtures.registerArchive(survivor);
        PatientB4Fixtures.registerArchive(placeholderId);

        JsonNode merged = parse(exchangeJson(HttpMethod.POST,
                PATIENTS_URL + "/" + placeholderId + "/resolve-or-merge",
                "{\"survivorId\":" + survivor + ",\"reason\":\"b4 合并\"}", fullPermToken()));
        assertEquals("200", merged.path("code").asText(), "the merge must succeed: " + merged);
        assertEquals(survivor, merged.path("data").path("id").asInt(-1),
                "the merge returns the survivor: " + merged);

        Integer logRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient_merge_log WHERE merged_id = ? AND survivor_id = ?",
                Integer.class, placeholderId, survivor);
        assertEquals(Integer.valueOf(1), logRows, "the merge writes exactly one log row");
        Integer outboxRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient_event_outbox WHERE patient_id = ? "
                        + "AND event_type = 'PATIENT_MERGED'",
                Integer.class, placeholderId);
        assertEquals(Integer.valueOf(1), outboxRows, "the merge writes exactly one outbox row");

        // idempotent replay: the EXISTING survivor comes back, nothing new is written
        JsonNode replay = parse(exchangeJson(HttpMethod.POST,
                PATIENTS_URL + "/" + placeholderId + "/resolve-or-merge",
                "{\"survivorId\":" + survivor + "}", fullPermToken()));
        assertEquals("200", replay.path("code").asText(), "the replay must succeed: " + replay);
        assertEquals(survivor, replay.path("data").path("id").asInt(-1),
                "the replay returns the existing survivor");
        logRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient_merge_log WHERE merged_id = ? AND survivor_id = ?",
                Integer.class, placeholderId, survivor);
        assertEquals(Integer.valueOf(1), logRows, "the replay must write nothing");
    }

    @Test
    void duplicateIdNoConflictNamesTheDuplicate() throws Exception {
        long ts = stamp();
        int original = createArchive("b4冲突源" + ts, "01", "B4DUP-" + ts);
        String originalPatId = jdbcTemplate.queryForObject(
                "SELECT pat_id FROM patient_master WHERE id = ?", String.class, original);

        JsonNode duplicate = parse(exchangeJson(HttpMethod.POST, PATIENTS_URL,
                "{\"idType\":\"01\",\"idNo\":\"B4DUP-" + ts + "\",\"name\":\"b4冲突重复\"}",
                fullPermToken()));
        assertEquals("A0715", duplicate.path("code").asText(),
                "a duplicate live identity must reject A0715: " + duplicate);
        assertTrue(duplicate.path("msg").asText().contains("pat_id=" + originalPatId),
                "the conflict payload must name the duplicate archive's pat_id: " + duplicate);

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient_master WHERE id_no = ? AND deleted = 0",
                Integer.class, "B4DUP-" + ts);
        assertEquals(Integer.valueOf(1), rows, "no second archive row may be written");
        PatientB4Fixtures.registerArchive(original);
    }


    /** Creates an unnamed PROVISIONAL placeholder (the merge absorbed row). */
    /** Per-call timestamp suffix for the fixture namespaces. */
    private long stamp() {
        return System.currentTimeMillis();
    }

    /** Creates one archive through the route and registers it for the sweep. */
    private int createArchive(String name, String idType, String idNo) throws Exception {
        StringBuilder body = new StringBuilder("{\"name\":\"" + name + "\"");
        if (idType != null) {
            body.append(",\"idType\":\"").append(idType).append("\"");
        }
        if (idNo != null) {
            body.append(",\"idNo\":\"").append(idNo).append("\"");
        }
        body.append("}");
        JsonNode created = parse(exchangeJson(HttpMethod.POST, PATIENTS_URL, body.toString(),
                fullPermToken()));
        assertEquals("200", created.path("code").asText(),
                "the archive fixture create must succeed: " + created);
        int id = created.path("data").path("id").asInt(-1);
        assertTrue(id > 0, "the archive fixture must return its id");
        PatientB4Fixtures.registerArchive(id);
        return id;
    }


    private int createPlaceholder() throws Exception {
        JsonNode created = parse(exchangeJson(HttpMethod.POST, PATIENTS_URL,
                "{\"name\":\"b4占位" + stamp() + "\",\"provisional\":true}", fullPermToken()));
        assertEquals("200", created.path("code").asText(),
                "the placeholder fixture create must succeed: " + created);
        int id = created.path("data").path("id").asInt(-1);
        assertTrue(id > 0, "the placeholder fixture must return its id");
        assertEquals("PROVISIONAL", created.path("data").path("archiveStatus").asText(),
                "the placeholder must be PROVISIONAL");
        PatientB4Fixtures.registerArchive(id);
        return id;
    }

    /** Creates one archive with an explicit phone (the soft-candidate fixture). */
    private int createArchiveWithPhone(String name, String phone) throws Exception {
        JsonNode created = parse(exchangeJson(HttpMethod.POST, PATIENTS_URL,
                "{\"name\":\"" + name + "\",\"phone\":\"" + phone + "\"}", fullPermToken()));
        assertEquals("200", created.path("code").asText(),
                "the candidate fixture create must succeed: " + created);
        int id = created.path("data").path("id").asInt(-1);
        assertTrue(id > 0, "the candidate fixture must return its id");
        PatientB4Fixtures.registerArchive(id);
        return id;
    }

    /** Creates one ACTIVE rule through the route and registers it for the sweep. */


    private ResponseEntity<String> exchangeJson(HttpMethod method, String url, String json,
            String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set("Authorization", "Bearer " + token);
        }
        return restTemplate.exchange(url, method, new HttpEntity<>(json, headers), String.class);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }
}
