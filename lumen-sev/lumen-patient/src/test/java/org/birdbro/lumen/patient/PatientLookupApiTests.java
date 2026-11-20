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

import org.birdbro.lumen.patient.dto.PatientArchiveSaveRequest;
import org.birdbro.lumen.patient.entity.PatientMaster;
import org.birdbro.lumen.patient.service.PatientArchiveService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The LOOKUP-ONLY resolve endpoint proof (us-station-registration-sev task 3.1, the B2
 * review gap closure; spec patient-domain "Internal Service Surface" family + us-station
 * "Patient Registry Adapter / US-side resolve passthrough" backend): the internal
 * {@code /patients/lookup} endpoint returns the exact identity (scenario: exact match),
 * the soft-match candidate list (scenario: candidates), or an EMPTY {@code NONE} result
 * (scenario: empty) - and NO archive row is ever written on any branch (the no-side-effect
 * contract that plain {@code resolve-or-register} cannot honor, since its no-match branch
 * CREATES).
 *
 * <p>House {@code PatientInternalApiTests} mechanics over {@link PatientIntegrationTestBase}:
 * REAL HTTP, internal-key-guarded (no user token), fixtures on the {@code usb2} marker
 * family + hard-DELETE sweep (user ruling 2026-11-07).</p>
 *
 * @author birdbro
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatientLookupApiTests extends PatientIntegrationTestBase {

    private static final String LOOKUP_URL = "/api/v1/patient/internal/patients/lookup";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PatientArchiveService patientArchiveService;

    @BeforeEach
    void sweepBeforeTest() {
        PatientUsB2Fixtures.sweep(jdbcTemplate);
    }

    @AfterAll
    void sweepAfterClass() {
        PatientUsB2Fixtures.sweep(jdbcTemplate);
    }

    @Test
    void exactIdPairReturnsTheIdentityWithoutANewRow() throws Exception {
        long ts = stamp();
        String idNo = "USB2LOOK-" + ts;
        PatientMaster seeded = patientArchiveService.create(saveRequest(
                PatientUsB2Fixtures.NAME_MARKER + "lookup精确" + ts, "01", idNo));
        PatientUsB2Fixtures.registerArchive(seeded.getId());

        JsonNode response = parse(exchangeJson(HttpMethod.POST, LOOKUP_URL,
                "{\"idType\":\"01\",\"idNo\":\"" + idNo + "\"}", TEST_INTERNAL_KEY));
        assertEquals("200", response.path("code").asText(),
                "the lookup must succeed through the internal key: " + response);
        assertEquals("MATCHED", response.path("data").path("result").asText(),
                "the exact identity pair resolves decisively: " + response);
        assertEquals(seeded.getId(), response.path("data").path("identity").path("patUid").asInt(-1),
                "the identity carries the existing archive's pat_uid");
        assertEquals(seeded.getPatId(), response.path("data").path("identity").path("patId").asText(),
                "the identity carries the archive's pat_id");
        assertTrue(response.path("data").path("candidates").isNull(),
                "no candidate list rides a decisive match");

        Long rows = countArchivesByIdNo(idNo);
        assertEquals(Long.valueOf(1), rows,
                "the exact lookup must return the existing identity WITHOUT a new row");
    }

    @Test
    void softAmbiguousDemographicsReturnCandidatesWithoutAnIdentity() throws Exception {
        long ts = stamp();
        String sharedName = PatientUsB2Fixtures.NAME_MARKER + "lookup软候选" + ts;
        String sharedPhone = "136" + ts;
        PatientMaster candA = patientArchiveService.create(seedWithPhone(sharedName, sharedPhone));
        PatientMaster candB = patientArchiveService.create(seedWithPhone(sharedName, sharedPhone));
        PatientUsB2Fixtures.registerArchive(candA.getId());
        PatientUsB2Fixtures.registerArchive(candB.getId());

        JsonNode response = parse(exchangeJson(HttpMethod.POST, LOOKUP_URL,
                "{\"name\":\"" + sharedName + "\",\"phone\":\"" + sharedPhone + "\"}",
                TEST_INTERNAL_KEY));
        assertEquals("200", response.path("code").asText(),
                "the lookup must succeed: " + response);
        assertEquals("CANDIDATES", response.path("data").path("result").asText(),
                "an ambiguous soft match returns the candidate list, never an error: "
                        + response);
        assertEquals(2, response.path("data").path("candidates").size(),
                "both soft hits are listed");
        assertTrue(response.path("data").path("identity").isNull(),
                "no single identity rides an ambiguous result");
        Long rows = countArchivesByName(sharedName);
        assertEquals(Long.valueOf(2), rows,
                "the candidates branch must not create a duplicate archive");
    }

    @Test
    void nothingMatchedReturnsNoneAndWritesNothing() throws Exception {
        long ts = stamp();
        String name = PatientUsB2Fixtures.NAME_MARKER + "lookup空" + ts;
        String phone = "137" + ts;
        long archivesBefore = countArchivesByName(name);

        JsonNode response = parse(exchangeJson(HttpMethod.POST, LOOKUP_URL,
                "{\"idType\":\"01\",\"idNo\":\"USB2NOHIT-" + ts + "\",\"name\":\"" + name
                        + "\",\"phone\":\"" + phone + "\"}", TEST_INTERNAL_KEY));
        assertEquals("200", response.path("code").asText(),
                "an empty lookup is a SUCCESS (a NONE result, never an error): " + response);
        assertEquals("NONE", response.path("data").path("result").asText(),
                "the no-match branch reports the empty NONE token: " + response);
        assertTrue(response.path("data").path("identity").isNull(),
                "NONE carries no identity");
        assertTrue(response.path("data").path("candidates").isNull(),
                "NONE carries no candidates");

        assertEquals(Long.valueOf(archivesBefore), countArchivesByName(name),
                "the NONE branch must write NO archive row (the lookup-only contract)");
        Long keyed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient_master WHERE id_no = ? AND deleted = 0",
                Long.class, "USB2NOHIT-" + ts);
        assertEquals(Long.valueOf(0), keyed,
                "no archive may exist for the unmatched id_no after a lookup");
    }

    @Test
    void internalKeyRefusalGuardsTheLookupEndpoint() throws Exception {
        long ts = stamp();
        ResponseEntity<String> refused = exchangeJson(HttpMethod.POST, LOOKUP_URL,
                "{\"name\":\"usb2lookup拒钥" + ts + "\"}", null);
        assertEquals(401, refused.getStatusCodeValue(),
                "a keyless call must be refused with HTTP 401");
        assertEquals("401", parse(refused).path("code").asText(),
                "the refusal envelope codes 401 (the internal-key guard)");
    }

    // ---------------------------------------------------------------- helpers

    private long stamp() {
        return System.currentTimeMillis();
    }

    private PatientArchiveSaveRequest saveRequest(String name, String idType, String idNo) {
        PatientArchiveSaveRequest request = new PatientArchiveSaveRequest();
        request.setName(name);
        request.setIdType(idType);
        request.setIdNo(idNo);
        return request;
    }

    private PatientArchiveSaveRequest seedWithPhone(String name, String phone) {
        PatientArchiveSaveRequest request = saveRequest(name, null, null);
        request.setPhone(phone);
        return request;
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
