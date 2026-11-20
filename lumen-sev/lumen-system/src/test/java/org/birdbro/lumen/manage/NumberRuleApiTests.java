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

    // ===================== scope-aware CRUD (number-rule-modality-scope task 1.3) =====================

    @Test
    void scopedCreatePersistsModalityToken() throws Exception {
        String token = runToken();
        jdbcTemplate.update("DELETE FROM number_rule WHERE rule_type = 'nr_sc01'");

        ResponseEntity<String> created = exchangeJson(HttpMethod.POST, RULES_URL, token,
                Map.of("ruleType", "nr_sc01", "modality", "CT", "prefix", "NRS1-", "suffix", "",
                        "serialLength", 4, "isStamp", 0, "active", 1));
        assertEquals("200", mapper.readTree(created.getBody()).path("code").asText(),
                "the scoped create must succeed: " + created.getBody());
        String modality = jdbcTemplate.queryForObject(
                "SELECT modality FROM number_rule WHERE rule_type = 'nr_sc01'", String.class);
        assertEquals("CT", modality, "the create must persist the dict-1024 scope token");
    }

    @Test
    void scopedActiveCoexistsWithFamilyGlobal() throws Exception {
        // spec "Scoped rule coexists with the family global rule": same family,
        // different scopes - both actives coexist.
        String token = runToken();
        jdbcTemplate.update("DELETE FROM number_rule WHERE rule_type = 'nr_sc02'");
        jdbcTemplate.update(
                "INSERT INTO number_rule (rule_type, prefix, suffix, serial_length, serial_cur,"
                + " is_stamp, stamp, active) VALUES ('nr_sc02', 'NRS2-', '', 4, 0, 0, NULL, 1)");

        ResponseEntity<String> created = exchangeJson(HttpMethod.POST, RULES_URL, token,
                Map.of("ruleType", "nr_sc02", "modality", "CT", "prefix", "NRS2C-", "suffix", "",
                        "serialLength", 4, "isStamp", 0, "active", 1));
        assertEquals("200", mapper.readTree(created.getBody()).path("code").asText(),
                "a scoped active of one family coexists with the family global active: "
                + created.getBody());
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM number_rule WHERE rule_type = 'nr_sc02' AND active = 1",
                Integer.class);
        assertEquals(2, rows, "both actives live after the scoped create");
    }

    @Test
    void sameScopeSecondActiveRejectedNamingScope() throws Exception {
        // A second active rule of the SAME scope is rejected (A0714) and the rejection
        // NAMES the scope (design D2: the message is the front line, the index the
        // backstop).
        String token = runToken();
        jdbcTemplate.update("DELETE FROM number_rule WHERE rule_type = 'nr_sc03'");
        jdbcTemplate.update(
                "INSERT INTO number_rule (rule_type, modality, prefix, suffix, serial_length,"
                + " serial_cur, is_stamp, stamp, active) VALUES ('nr_sc03', 'CT', 'NRS3-', '',"
                + " 4, 0, 0, NULL, 1)");

        ResponseEntity<String> refused = exchangeJson(HttpMethod.POST, RULES_URL, token,
                Map.of("ruleType", "nr_sc03", "modality", "CT", "prefix", "NRS3B-", "suffix", "",
                        "serialLength", 4, "isStamp", 0, "active", 1));
        JsonNode body = mapper.readTree(refused.getBody());
        assertEquals("A0714", body.path("code").asText(),
                "the duplicate-active rejection is a data-duplication error: " + refused.getBody());
        assertTrue(body.path("msg").asText("").contains("modality=CT"),
                "the rejection names the conflicting scope: " + refused.getBody());
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM number_rule WHERE rule_type = 'nr_sc03' AND deleted = 0",
                Integer.class);
        assertEquals(1, rows, "the second same-scope active never lands");
    }

    @Test
    void activationIntoDifferentScopeCoexists() throws Exception {
        // Activating the family's dormant GLOBAL rule while a CT-scoped rule is live
        // succeeds - one active per family AND scope, not per family alone.
        String token = runToken();
        jdbcTemplate.update("DELETE FROM number_rule WHERE rule_type = 'nr_sc04'");
        jdbcTemplate.update(
                "INSERT INTO number_rule (rule_type, modality, prefix, suffix, serial_length,"
                + " serial_cur, is_stamp, stamp, active) VALUES ('nr_sc04', 'CT', 'NRS4-', '',"
                + " 4, 0, 0, NULL, 1)");
        jdbcTemplate.update(
                "INSERT INTO number_rule (rule_type, prefix, suffix, serial_length, serial_cur,"
                + " is_stamp, stamp, active) VALUES ('nr_sc04', 'NRS4G-', '', 4, 0, 0, NULL, 0)");
        Integer dormantGlobalId = jdbcTemplate.queryForObject(
                "SELECT id FROM number_rule WHERE rule_type = 'nr_sc04' AND active = 0",
                Integer.class);

        ResponseEntity<String> on = exchangeJson(
                HttpMethod.PUT, RULES_URL + "/" + dormantGlobalId + "/active", token,
                Map.of("active", 1));
        assertEquals("200", mapper.readTree(on.getBody()).path("code").asText(),
                "activating into a DIFFERENT scope succeeds while the CT rule is live: "
                + on.getBody());
        Integer liveActives = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM number_rule WHERE rule_type = 'nr_sc04' AND active = 1",
                Integer.class);
        assertEquals(2, liveActives, "one live per scope: the CT rule and the global rule");
    }

    @Test
    void sameScopeToggleConflictRejectedNamingScope() throws Exception {
        // Activating a second rule of the SAME scope while it is live: A0714 naming
        // the scope (the active-toggle conflict on a scoped rule).
        String token = runToken();
        jdbcTemplate.update("DELETE FROM number_rule WHERE rule_type = 'nr_sc05'");
        jdbcTemplate.update(
                "INSERT INTO number_rule (rule_type, modality, prefix, suffix, serial_length,"
                + " serial_cur, is_stamp, stamp, active) VALUES ('nr_sc05', 'CT', 'NRS5-', '',"
                + " 4, 0, 0, NULL, 1)");
        jdbcTemplate.update(
                "INSERT INTO number_rule (rule_type, modality, prefix, suffix, serial_length,"
                + " serial_cur, is_stamp, stamp, active) VALUES ('nr_sc05', 'CT', 'NRS5B-',"
                + " '', 4, 0, 0, NULL, 0)");
        Integer dormantCtId = jdbcTemplate.queryForObject(
                "SELECT id FROM number_rule WHERE rule_type = 'nr_sc05' AND modality = 'CT'"
                + " AND active = 0", Integer.class);

        ResponseEntity<String> refused = exchangeJson(
                HttpMethod.PUT, RULES_URL + "/" + dormantCtId + "/active", token,
                Map.of("active", 1));
        JsonNode body = mapper.readTree(refused.getBody());
        assertEquals("A0714", body.path("code").asText(),
                "activating a second live rule of one scope is refused: " + refused.getBody());
        assertTrue(body.path("msg").asText("").contains("modality=CT"),
                "the toggle rejection names the conflicting scope: " + refused.getBody());
    }

    @Test
    void toggleBodyModalityIsInformationalStoredScopeGuards() throws Exception {
        // Review hardening (batch 1): SetNumberRuleActiveRequest.modality is
        // INFORMATIONAL - the active-toggle conflict guard keys on the STORED row's
        // scope, never the request body's token (a mismatched body token must not
        // widen or dodge the conflict check).
        String token = runToken();
        jdbcTemplate.update("DELETE FROM number_rule WHERE rule_type = 'nr_sc09'");
        jdbcTemplate.update(
                "INSERT INTO number_rule (rule_type, modality, prefix, suffix, serial_length,"
                + " serial_cur, is_stamp, stamp, active) VALUES ('nr_sc09', 'CT', 'NRS9-', '',"
                + " 4, 0, 0, NULL, 1)");
        jdbcTemplate.update(
                "INSERT INTO number_rule (rule_type, modality, prefix, suffix, serial_length,"
                + " serial_cur, is_stamp, stamp, active) VALUES ('nr_sc09', 'CT', 'NRS9B-',"
                + " '', 4, 0, 0, NULL, 0)");
        Integer dormantCtId = jdbcTemplate.queryForObject(
                "SELECT id FROM number_rule WHERE rule_type = 'nr_sc09' AND modality = 'CT'"
                + " AND active = 0", Integer.class);

        ResponseEntity<String> refused = exchangeJson(
                HttpMethod.PUT, RULES_URL + "/" + dormantCtId + "/active", token,
                Map.of("active", 1, "modality", "DR"));
        JsonNode body = mapper.readTree(refused.getBody());
        assertEquals("A0714", body.path("code").asText(),
                "the mismatched body token cannot dodge the stored-scope conflict: "
                + refused.getBody());
        assertTrue(body.path("msg").asText("").contains("modality=CT"),
                "the rejection names the STORED scope, not the body token: " + refused.getBody());
    }

    @Test
    void updatePatchesParamsButKeepsStoredScope() throws Exception {
        // Design D2: the scope is pool identity - IMMUTABLE after create (mirrors the
        // ruleType-immutable handling). The update patches format parameters only and
        // silently keeps the stored scope (the edit form echoes it disabled).
        String token = runToken();
        jdbcTemplate.update("DELETE FROM number_rule WHERE rule_type = 'nr_sc06'");
        jdbcTemplate.update(
                "INSERT INTO number_rule (rule_type, modality, prefix, suffix, serial_length,"
                + " serial_cur, is_stamp, stamp, active) VALUES ('nr_sc06', 'CT', 'NRS6-', '',"
                + " 4, 0, 0, NULL, 1)");
        Integer id = jdbcTemplate.queryForObject(
                "SELECT id FROM number_rule WHERE rule_type = 'nr_sc06'", Integer.class);

        ResponseEntity<String> updated = exchangeJson(
                HttpMethod.PUT, RULES_URL + "/" + id, token,
                Map.of("ruleType", "nr_sc06", "modality", "DR", "prefix", "NRS6NEW-", "suffix",
                        "X", "serialLength", 6, "isStamp", 0, "active", 1));
        assertEquals("200", mapper.readTree(updated.getBody()).path("code").asText(),
                "the update itself succeeds: " + updated.getBody());
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT modality, prefix, suffix, serial_length FROM number_rule WHERE id = ?", id);
        assertEquals("CT", row.get("modality"),
                "the stored scope survives an update carrying a DIFFERENT token");
        assertEquals("NRS6NEW-", row.get("prefix"), "format parameters still patch");
        assertEquals("X", row.get("suffix"), "format parameters still patch");
        assertEquals(6, ((Number) row.get("serial_length")).intValue(),
                "format parameters still patch");
    }

    @Test
    void blankModalityTokenStoresGlobalShape() throws Exception {
        // A blank token IS 全局: the create stores the NULL shape the index and the
        // issuer resolution key on (a stored '' would be invisible to BOTH the exact
        // lookup and the modality-IS-NULL global lookup).
        String token = runToken();
        jdbcTemplate.update("DELETE FROM number_rule WHERE rule_type = 'nr_sc07'");

        ResponseEntity<String> created = exchangeJson(HttpMethod.POST, RULES_URL, token,
                Map.of("ruleType", "nr_sc07", "modality", "", "prefix", "NRS7-", "suffix", "",
                        "serialLength", 4, "isStamp", 0, "active", 1));
        assertEquals("200", mapper.readTree(created.getBody()).path("code").asText(),
                "the blank-token create must succeed: " + created.getBody());
        Integer nullScopeRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM number_rule WHERE rule_type = 'nr_sc07'"
                + " AND modality IS NULL", Integer.class);
        assertEquals(1, nullScopeRows, "a blank token stores the GLOBAL (NULL) shape");
    }

    // ===================== internal-key guard behavioral =====================

    @Test
    void untrimmedModalityTokenNormalizesOnCreate() throws Exception {
        // Review hardening (batch 1): a whitespace-padded token (" CT ") trims to "CT"
        // on create - stored verbatim it could NEVER be resolved by a clean "CT" issue
        // request (invisible to the exact lookup, not the global shape either).
        String token = runToken();
        jdbcTemplate.update("DELETE FROM number_rule WHERE rule_type = 'nr_sc08'");

        ResponseEntity<String> created = exchangeJson(HttpMethod.POST, RULES_URL, token,
                Map.of("ruleType", "nr_sc08", "modality", " CT ", "prefix", "NRS8-", "suffix",
                        "", "serialLength", 4, "isStamp", 0, "active", 1));
        assertEquals("200", mapper.readTree(created.getBody()).path("code").asText(),
                "the create itself succeeds: " + created.getBody());
        String modality = jdbcTemplate.queryForObject(
                "SELECT modality FROM number_rule WHERE rule_type = 'nr_sc08'", String.class);
        assertEquals("CT", modality, "a whitespace-padded token stores the clean token");
    }

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

    @Test
    void issueWithModalityResolvesTheScopedRule() throws Exception {
        // number-rule-modality-scope task 1.2 (spec "Modality-Scoped Issuance
        // Resolution", "Exact scoped rule wins"): the machine endpoint binds the
        // optional modality and passes it through - a CT-scoped rule answers the
        // CT request from ITS pool, not the family global's.
        String family = "nr_imod";
        jdbcTemplate.update("DELETE FROM number_rule WHERE rule_type = ?", family);
        jdbcTemplate.update(
                "INSERT INTO number_rule (rule_type, prefix, suffix, serial_length, serial_cur,"
                + " is_stamp, stamp, active) VALUES (?, 'NRIG-', '', 4, 0, 0, NULL, 1)", family);
        jdbcTemplate.update(
                "INSERT INTO number_rule (rule_type, modality, prefix, suffix, serial_length,"
                + " serial_cur, is_stamp, stamp, active) VALUES (?, 'CT', 'NRIC-', '', 4, 0,"
                + " 0, NULL, 1)", family);

        ResponseEntity<String> ok = exchangeJsonWithKey(ISSUE_URL,
                Map.of("ruleType", family, "modality", "CT"), TEST_INTERNAL_KEY);
        JsonNode body = mapper.readTree(ok.getBody());
        assertEquals("200", body.path("code").asText(), "the scoped request issues: " + body);
        String number = body.path("data").path("number").asText("");
        assertTrue(number.startsWith("NRIC-"),
                "the CT request must resolve the CT-scoped rule's pool, not the global's: "
                + number);
        Integer scopedSerial = jdbcTemplate.queryForObject(
                "SELECT serial_cur FROM number_rule WHERE rule_type = ? AND modality = 'CT'",
                Integer.class, family);
        assertEquals(1, scopedSerial, "the scoped pool advanced");
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
