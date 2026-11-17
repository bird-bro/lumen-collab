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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Patient permission-enforcement matrix (patient-domain task 4.2; the
 * {@code AuthPermEnforcementTests} shape): the rule CRUD family denies a perm-less
 * authenticated caller on ALL FOUR verbs with the {@code A0301} envelope BEFORE any
 * controller logic runs (nothing written), passes with the perms, and both families
 * keep the entry-point precedence (tokenless answers "203", never "A0301"). The
 * archive endpoints are AUTHENTICATION-ONLY in v1 (coordinator decision 4 - the
 * auth-rbac b4 precedent, no archive perm strings invented): tokenless is 203, ANY
 * authenticated user passes.
 *
 * <p>Tokens are minted (no lumen-system running), so the perm-less case is the real
 * session shape: a valid JWT whose {@code UserLogin} carries an EMPTY authority set -
 * the same shape a perm-less user's login session produces.</p>
 *
 * @author birdbro
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatientPermTests extends PatientApiTestBase {

    private static final String PATIENTS_URL = "/api/v1/patient/patients";
    private static final String RULES_URL = "/api/v1/patient/patient-number-rules";

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

    @Test
    void ruleFamilyDeniesAllFourVerbsWithoutThePerm() throws Exception {
        String noPerm = noPermToken();
        long ts = System.currentTimeMillis();
        String ruleType = PatientB4Fixtures.RULE_TYPE_MARKER + "perm" + ts;

        // GET (the class gate itself)
        assertEquals("A0301", parse(exchangeJson(HttpMethod.GET,
                RULES_URL + "?pageNum=1&pageSize=10", null, noPerm)).path("code").asText(),
                "a perm-less list read must be denied A0301");

        // POST: the denial fires BEFORE the controller - no rule row may appear
        JsonNode create = parse(exchangeJson(HttpMethod.POST, RULES_URL,
                "{\"ruleType\":\"" + ruleType + "\",\"serialLength\":4,\"isStamp\":0,\"active\":0}",
                noPerm));
        assertEquals("A0301", create.path("code").asText(),
                "a perm-less rule create must be denied A0301: " + create);
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient_number_rule WHERE rule_type = ?", Integer.class,
                ruleType);
        assertEquals(Integer.valueOf(0), rows,
                "the denied create must not reach the controller (no rule row)");

        // PUT + DELETE against a rule created WITH the perms (a valid target id - the
        // denial must fire before any controller logic regardless)
        int target = createRuleFixture();
        assertEquals("A0301", parse(exchangeJson(HttpMethod.PUT, RULES_URL + "/" + target,
                "{\"ruleType\":\"" + PatientB4Fixtures.RULE_TYPE_MARKER + "pt" + ts
                        + "\",\"serialLength\":4,\"isStamp\":0,\"active\":0}", noPerm))
                .path("code").asText(), "a perm-less rule update must be denied A0301");
        assertEquals("A0301", parse(exchangeJson(HttpMethod.DELETE, RULES_URL + "/" + target,
                null, noPerm)).path("code").asText(),
                "a perm-less rule delete must be denied A0301");
        Integer deletedFlag = jdbcTemplate.queryForObject(
                "SELECT deleted FROM patient_number_rule WHERE id = ?", Integer.class, target);
        assertEquals(Integer.valueOf(0), deletedFlag,
                "the denied delete must not reach the controller (the row stays live)");
    }

    @Test
    void ruleFamilyPassesWithThePerms() throws Exception {
        String full = fullPermToken();
        long ts = System.currentTimeMillis();
        String ruleType = PatientB4Fixtures.RULE_TYPE_MARKER + "pass" + ts;

        assertEquals("200", parse(exchangeJson(HttpMethod.GET,
                RULES_URL + "?pageNum=1&pageSize=10", null, full)).path("code").asText(),
                "the granted list read must pass");
        JsonNode create = parse(exchangeJson(HttpMethod.POST, RULES_URL,
                "{\"ruleType\":\"" + ruleType + "\",\"serialLength\":4,\"isStamp\":0,\"active\":0}",
                full));
        assertEquals("200", create.path("code").asText(),
                "the granted rule create must pass: " + create);
        int id = create.path("data").asInt(-1);
        assertTrue(id > 0, "the granted create must return the rule id");
        PatientB4Fixtures.registerRule(id);
        assertEquals("200", parse(exchangeJson(HttpMethod.PUT, RULES_URL + "/" + id,
                "{\"ruleType\":\"" + ruleType + "\",\"serialLength\":5,\"isStamp\":0,\"active\":0}",
                full)).path("code").asText(), "the granted rule update must pass");
        assertEquals("200", parse(exchangeJson(HttpMethod.DELETE, RULES_URL + "/" + id,
                null, full)).path("code").asText(),
                "the granted rule delete must pass (the rule is inactive)");
    }

    @Test
    void archiveEndpointsTokenlessAnswer203() throws Exception {
        // entry-point precedence: tokenless is "203" on every archive route
        assertEquals("203", parse(exchangeJson(HttpMethod.GET,
                PATIENTS_URL + "?pageNum=1&pageSize=10", null, null)).path("code").asText(),
                "a tokenless archive page must answer 203");
        assertEquals("203", parse(exchangeJson(HttpMethod.GET, PATIENTS_URL + "/1", null, null))
                .path("code").asText(), "a tokenless fetch must answer 203");
        assertEquals("203", parse(exchangeJson(HttpMethod.POST, PATIENTS_URL,
                "{\"name\":\"b4无票\"}", null)).path("code").asText(),
                "a tokenless create must answer 203");
        assertEquals("203", parse(exchangeJson(HttpMethod.POST, PATIENTS_URL + "/resolve",
                "{}", null)).path("code").asText(), "a tokenless resolve must answer 203");
    }

    @Test
    void ruleEndpointsTokenlessAnswer203NotA0301() throws Exception {
        // entry-point precedence: tokenless on a GATED family is still "203"
        assertEquals("203", parse(exchangeJson(HttpMethod.GET,
                RULES_URL + "?pageNum=1&pageSize=10", null, null)).path("code").asText(),
                "a tokenless rule page must answer 203 (the entry point precedes the gate)");
        assertEquals("203", parse(exchangeJson(HttpMethod.POST, RULES_URL,
                "{\"ruleType\":\"b4_x\"}", null)).path("code").asText(),
                "a tokenless rule create must answer 203");
    }

    @Test
    void archiveEndpointsPassForAnyAuthenticatedUser() throws Exception {
        // authentication-only in v1 (decision 4): a session with NO permissions at all
        // still creates an archive - no archive perm string exists to check
        JsonNode created = parse(exchangeJson(HttpMethod.POST, PATIENTS_URL,
                "{\"name\":\"b4无权建档" + System.currentTimeMillis() + "\",\"provisional\":true}",
                noPermToken()));
        assertEquals("200", created.path("code").asText(),
                "an authenticated perm-less user must pass on the archive surface: " + created);
        int id = created.path("data").path("id").asInt(-1);
        assertTrue(id > 0, "the archive create must persist");
        PatientB4Fixtures.registerArchive(id);
    }

    // ------------------------------------------------------- fixtures

    /** Creates one INACTIVE rule with the full-perm token (the PUT/DELETE deny target). */
    private int createRuleFixture() throws Exception {
        JsonNode created = parse(exchangeJson(HttpMethod.POST, RULES_URL,
                "{\"ruleType\":\"" + PatientB4Fixtures.RULE_TYPE_MARKER
                        + System.nanoTime() + "\",\"serialLength\":4,\"isStamp\":0,\"active\":0}",
                fullPermToken()));
        assertEquals("200", created.path("code").asText(),
                "the rule fixture create must succeed: " + created);
        int id = created.path("data").asInt(-1);
        assertTrue(id > 0, "the rule fixture must return its id");
        PatientB4Fixtures.registerRule(id);
        return id;
    }

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
