package org.birdbro.lumen.manage;

import java.lang.reflect.Method;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterAll;
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
import org.springframework.security.access.prepost.PreAuthorize;

import org.birdbro.lumen.manage.controller.NumberRuleController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Number-rule API proof (number-rule-unification-sev task 2.2; spec number-rule
 * "Number Rule Directory" + "Internal Service Issuance Contract"): the perm-pin
 * CONTRACT (reflection, the AuthRoomPermPinTests pattern), the duplicate-active
 * behavioral rejection (run account), and the internal-key guard behavior on the
 * machine endpoint (wrong/missing key issues nothing; the right key issues from a
 * dedicated {@code nr_} fixture family - the seeded family rows are NEVER consumed).
 *
 * @author birdbro
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NumberRuleApiTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/system/login";
    private static final String RULES_URL = "/api/v1/system/number-rules";
    private static final String ISSUE_URL = RULES_URL + "/issue";
    private static final String TEST_INTERNAL_KEY = "nr-tests-internal-key";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    @AfterAll
    void sweepFixtureFamily() {
        jdbcTemplate.update("DELETE FROM number_rule WHERE rule_type LIKE 'nr\\_%'");
    }

    // ===================== perm-pin contract =====================

    @Test
    void numberRuleControllerPinsItsButtonPerms() {
        PreAuthorize classGate = NumberRuleController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(classGate, "the controller must carry the class-level page gate");
        assertEquals("hasAuthority('system:number:list')", classGate.value(),
                "the number-rule page gate must be system:number:list");
        assertMethodPin("create", "hasAuthority('system:number:create')");
        assertMethodPin("update", "hasAuthority('system:number:update')");
        assertMethodPin("setActive", "hasAuthority('system:number:update')");
        assertMethodPin("delete", "hasAuthority('system:number:delete')");
    }

    private void assertMethodPin(String methodName, String expectedPin) {
        for (Method method : NumberRuleController.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                PreAuthorize pin = method.getAnnotation(PreAuthorize.class);
                assertNotNull(pin, "the " + methodName + " method must pin its button perm");
                assertEquals(expectedPin, pin.value(),
                        "the " + methodName + " perm pin must be " + expectedPin);
                return;
            }
        }
        throw new AssertionError("the " + methodName + " method must exist on NumberRuleController");
    }

    // ===================== duplicate-active behavioral =====================

    @Test
    void duplicateActiveRuleRejected() throws Exception {
        String token = runToken();
        insertFixtureRule("nr_dup", true);

        // a SECOND active rule for the same type: rejected, no row written
        ResponseEntity<String> second = exchangeJson(HttpMethod.POST, RULES_URL, token,
                Map.of("ruleType", "nr_dup", "prefix", "X-", "suffix", "",
                        "serialLength", 4, "isStamp", 1, "stamp", "yyMMdd", "active", 1));
        JsonNode body = mapper.readTree(second.getBody());
        assertEquals("A0714", body.path("code").asText(),
                "the duplicate-active rejection is a data-duplication error: " + body);
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM number_rule WHERE rule_type = 'nr_dup' AND deleted = 0",
                Integer.class);
        assertEquals(1, rows, "the second active row never lands");
    }

    @Test
    void createCarriesRemarkAndEchoesItBack() throws Exception {
        String token = runToken();
        jdbcTemplate.update("DELETE FROM number_rule WHERE rule_type = 'nr_remark'");

        ResponseEntity<String> created = exchangeJson(HttpMethod.POST, RULES_URL, token,
                Map.of("ruleType", "nr_remark", "prefix", "NR-", "suffix", "",
                        "serialLength", 4, "isStamp", 1, "stamp", "yyMMdd", "active", 1,
                        "remark", "患者归档专用族"));
        assertEquals("200", mapper.readTree(created.getBody()).path("code").asText(),
                "the remark-bearing create must succeed: " + created.getBody());
        String remark = jdbcTemplate.queryForObject(
                "SELECT remark FROM number_rule WHERE rule_type = 'nr_remark'", String.class);
        assertEquals("患者归档专用族", remark, "the remark lands verbatim (V46 column)");
    }

    // ===================== active-toggle behavioral =====================

    @Test
    void activeToggleCommandFlipsAndGuardsTheFamily() throws Exception {
        String token = runToken();
        jdbcTemplate.update("DELETE FROM number_rule WHERE rule_type = 'nr_toggle'");
        jdbcTemplate.update(
                "INSERT INTO number_rule (rule_type, prefix, suffix, serial_length, serial_cur,"
                + " is_stamp, stamp, active) VALUES ('nr_toggle', 'NRT-', '', 4, 0, 1, 'yyMMdd', 1)");
        Integer liveId = jdbcTemplate.queryForObject(
                "SELECT id FROM number_rule WHERE rule_type = 'nr_toggle' AND active = 1", Integer.class);
        jdbcTemplate.update(
                "INSERT INTO number_rule (rule_type, prefix, suffix, serial_length, serial_cur,"
                + " is_stamp, stamp, active) VALUES ('nr_toggle', 'NRT2-', '', 4, 0, 1, 'yyMMdd', 0)");
        Integer dormantId = jdbcTemplate.queryForObject(
                "SELECT id FROM number_rule WHERE rule_type = 'nr_toggle' AND active = 0", Integer.class);

        // activating the dormant sibling while the family's live rule exists: A0714
        ResponseEntity<String> refused = exchangeJson(
                HttpMethod.PUT, RULES_URL + "/" + dormantId + "/active", token, Map.of("active", 1));
        assertEquals("A0714", mapper.readTree(refused.getBody()).path("code").asText(),
                "activating a second live rule of one family is refused: " + refused.getBody());

        // deactivating the live rule is always allowed
        ResponseEntity<String> off = exchangeJson(
                HttpMethod.PUT, RULES_URL + "/" + liveId + "/active", token, Map.of("active", 0));
        assertEquals("200", mapper.readTree(off.getBody()).path("code").asText(),
                "deactivation is unconditional: " + off.getBody());

        // the family is free - the dormant sibling activates, exactly one live remains
        ResponseEntity<String> on = exchangeJson(
                HttpMethod.PUT, RULES_URL + "/" + dormantId + "/active", token, Map.of("active", 1));
        assertEquals("200", mapper.readTree(on.getBody()).path("code").asText(),
                "activating into a free family succeeds: " + on.getBody());
        Integer liveCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM number_rule WHERE rule_type = 'nr_toggle' AND active = 1",
                Integer.class);
        assertEquals(1, liveCount, "exactly one live rule after the handoff");
    }

    // ===================== internal-key guard behavioral =====================

    @Test
    void issueEndpointRejectsNonInternalCallers() throws Exception {
        insertFixtureRule("nr_key", true);

        // MISSING key: rejected, no serial consumed
        ResponseEntity<String> missing = exchangeJson(HttpMethod.POST, ISSUE_URL, null,
                Map.of("ruleType", "nr_key"));
        assertEquals("401", mapper.readTree(missing.getBody()).path("code").asText(),
                "the missing internal key is an auth rejection: " + missing.getBody());

        // WRONG key: rejected, no serial consumed
        ResponseEntity<String> wrong = exchangeJsonWithKey(ISSUE_URL,
                Map.of("ruleType", "nr_key"), "not-the-key");
        assertEquals("401", mapper.readTree(wrong.getBody()).path("code").asText(),
                "a wrong internal key is an auth rejection");

        Integer serial = jdbcTemplate.queryForObject(
                "SELECT serial_cur FROM number_rule WHERE rule_type = 'nr_key'", Integer.class);
        assertEquals(0, serial, "no rejected call consumed a serial");
    }

    @Test
    void issueWithInternalKeyIssuesFromTheFamily() throws Exception {
        insertFixtureRule("nr_issue", true);

        ResponseEntity<String> ok = exchangeJsonWithKey(ISSUE_URL,
                Map.of("ruleType", "nr_issue"), TEST_INTERNAL_KEY);
        JsonNode body = mapper.readTree(ok.getBody());
        assertEquals("200", body.path("code").asText(), "the right key issues: " + body);
        String number = body.path("data").path("number").asText("");
        assertTrue(number.startsWith("NRI-") && number.length() > 4,
                "the issued number follows the fixture rule format: " + number);
    }

    // ===================== fixtures + helpers =====================

    private void insertFixtureRule(String type, boolean active) {
        jdbcTemplate.update(
                "DELETE FROM number_rule WHERE rule_type = ?", type);
        jdbcTemplate.update(
                "INSERT INTO number_rule (rule_type, prefix, suffix, serial_length, serial_cur,"
                + " is_stamp, stamp, active) VALUES (?, 'NRI-', '', 4, 0, 1, 'yyMMdd', ?)",
                type, active ? 1 : 0);
    }

    private String runToken() throws Exception {
        // the login body comes from the BASE's encrypted helper (the password travels
        // SM2-encrypted - a raw-password body is an A0400 decryption failure)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = restTemplate.exchange(LOGIN_URL, HttpMethod.POST,
                new HttpEntity<>(runAdminLoginBody(), headers), String.class);
        JsonNode body = mapper.readTree(login.getBody());
        assertEquals("200", body.path("code").asText(), "the run account must log in: " + body);
        String token = body.path("data").path("token").asText("");
        assertTrue(!token.isEmpty(), "the run token must be present");
        return token;
    }

    private ResponseEntity<String> exchangeJson(HttpMethod method, String url, String token,
            Map<String, Object> payload) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set("Authorization", "Bearer " + token);
        }
        return restTemplate.exchange(url, method,
                new HttpEntity<>(mapper.writeValueAsString(payload), headers), String.class);
    }

    private ResponseEntity<String> exchangeJsonWithKey(String url, Map<String, Object> payload,
            String internalKey) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Key", internalKey);
        return restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(mapper.writeValueAsString(payload), headers), String.class);
    }
}
