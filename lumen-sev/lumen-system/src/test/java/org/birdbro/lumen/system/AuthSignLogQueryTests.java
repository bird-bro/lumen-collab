package org.birdbro.lumen.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.birdbro.common.core.pojo.LogSign;
import org.birdbro.common.utils.DateUtils;
import org.birdbro.framework.log.service.ILogSignService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sign-log query endpoint proof (sign-log-query tasks 2.1 / 2.3, spec scenarios
 * "Query sign logs" + "Sign-log permission is enforced server-side"): {@code GET
 * /api/v1/system/logs/sign} pages {@code public.log_sign} newest-first with the optional
 * {@code account}/{@code uid}/{@code dateYm} filters AND-combining, and no response row
 * carries the credential {@code token} field (design D7 - token never leaves the service).
 *
 * <p>The method-level {@code system:signlog:list} gate is pinned BOTH ways against the
 * class-level {@code system:log:list} (design D4, method-security precedence): a
 * log-only principal is denied on {@code /sign}, and a sign-only principal passes
 * {@code /sign} while staying denied on the operation log - the two page permissions
 * are independent.</p>
 *
 * <p>The rows are seeded through the framework {@code ILogSignService} with controlled
 * action times / uids / accounts, so the assertions are deterministic against the shared
 * long-lived database (unique per-run markers, keyed assertions - never global row
 * counts). The seeded rows carry REAL token values: the exclusion assertion is only
 * meaningful when the column exists to be dropped.</p>
 *
 * <p>The {@code system:signlog:list} authority is created per run WITHOUT depending on
 * the V14 menu seed (batch 3): a synthetic menu row with the perms string is inserted
 * under 安全 (16) and bound to the test role through the real grant API, then cleaned up
 * (AuthPermEnforcementTests fixture mechanics).</p>
 *
 * @author birdbro
 */
class AuthSignLogQueryTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/system/login";
    private static final String LOGS_URL = "/api/v1/system/logs";
    private static final String SIGN_LOGS_URL = "/api/v1/system/logs/sign";
    private static final String ROLES_URL = "/api/v1/system/roles";
    private static final String USERS_URL = "/api/v1/system/users";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ILogSignService logSignService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void signEndpointPagesFiltersOrdersAndExcludesToken() throws Exception {
        long ts = System.currentTimeMillis();
        String marker = "b2.signlog." + ts; // unique per run - shared DB
        int uidA = (int) (100000000 + ts % 100000000);
        int uidB = (int) (200000000 + ts % 100000000);
        int currentYm = Integer.parseInt(DateUtils.dateTimeNow("yyyyMM"));

        // Three controlled rows: A1 (newest), A2 (1 s older), B (uid B) - the insert stamps
        // date_ym with the current month, so the dateYm filter has a real value to hit.
        assertTrue(logSignService.insert(LogSign.builder()
                .uid(uidA).account(marker + "-a1").msg("Sign-IN")
                .descr("批量二登录日志A1").type(10).actionTime(new Date(ts))
                .token("ut.token." + ts + "-a1").ip("127.0.0.1")
                .build()), "the first seeded sign row must insert");
        assertTrue(logSignService.insert(LogSign.builder()
                .uid(uidA).account(marker + "-a2").msg("Sign-IN")
                .descr("批量二登录日志A2").type(10).actionTime(new Date(ts - 1000))
                .token("ut.token." + ts + "-a2").ip("127.0.0.1")
                .build()), "the second seeded sign row must insert");
        assertTrue(logSignService.insert(LogSign.builder()
                .uid(uidB).account(marker + "-b").msg("Sign-OUT")
                .descr("批量二登录日志B").type(12).actionTime(new Date(ts - 2000))
                .token("ut.token." + ts + "-b").ip("127.0.0.1")
                .build()), "the third seeded sign row must insert");

        // The querying principal holds system:signlog:list through the per-run synthetic
        // menu (spec scenario 4: "an administrator holding system:signlog:list") - the
        // seeded admin has NO signlog perm until batch 3's V14, so a fixture user carries
        // the authority for every run of this suite.
        String adminToken = adminToken();
        String fixtureUser = "b2signlogq_" + ts;
        int menuId = seedSignLogMenu(ts);
        int roleId = 0;
        int userId = 0;
        try {
            roleId = createRole(adminToken, "登录日志查询角色_" + ts);
            exchangeJson(HttpMethod.PUT, ROLES_URL + "/" + roleId + "/menus",
                    "{\"menuIds\":[1,16," + menuId + "]}", adminToken);
            userId = createUserWithRoles(adminToken, fixtureUser, "登录日志查询_" + ts, roleId);
            String token = loginToken(fixtureUser, "audit-pass-123");
            runQueryAssertions(token, marker, uidA, uidB, currentYm);
        } finally {
            cleanupRoleAndUser(roleId, userId);
            cleanupMenu(menuId);
        }
    }

    /** The 2.1 query assertions, run under the signlog-authorized fixture principal. */
    private void runQueryAssertions(String token, String marker, int uidA, int uidB, int currentYm) throws Exception {

        // Pagination: page 1 with pageSize 2 returns exactly two records and a total of 3+,
        // and none of the fetched rows carries the credential token field.
        ResponseEntity<String> pageResponse = exchangeJson(HttpMethod.GET,
                SIGN_LOGS_URL + "?page=1&pageSize=2", token);
        JsonNode pageBody = parse(pageResponse);
        assertEquals(200, pageResponse.getStatusCodeValue(),
                "the sign-log endpoint answers over HTTP 200 (envelope transport)");
        assertEquals("200", pageBody.path("code").asText(), "the sign-log query must succeed: " + pageBody);
        assertEquals(2, pageBody.path("data").path("records").size(),
                "page 1 with pageSize 2 must carry exactly two records");
        assertTrue(pageBody.path("data").path("total").asLong(-1) >= 3,
                "the total must count at least the three seeded rows: " + pageBody.path("data").path("total"));
        assertNoTokenField(pageBody);

        // Page-size clamp: a pageSize above the shared MAX_PAGE_SIZE cap is clamped
        // (the echoed page size is the served cap, not the requested one).
        ResponseEntity<String> capResponse = exchangeJson(HttpMethod.GET,
                SIGN_LOGS_URL + "?page=1&pageSize=500&uid=" + uidA, token);
        JsonNode capBody = parse(capResponse);
        assertEquals("200", capBody.path("code").asText(), "the clamped query must succeed: " + capBody);
        assertEquals(100, capBody.path("data").path("size").asInt(-1),
                "a pageSize above the MAX_PAGE_SIZE cap must clamp to 100: "
                        + capBody.path("data").path("size"));
        assertNoTokenField(capBody);

        // uid filter: EXACTLY the two rows of uidA, newest first (actionTime DESC, id DESC).
        ResponseEntity<String> uidResponse = exchangeJson(HttpMethod.GET,
                SIGN_LOGS_URL + "?page=1&pageSize=10&uid=" + uidA, token);
        JsonNode uidBody = parse(uidResponse);
        assertEquals("200", uidBody.path("code").asText(), "the uid-filtered query must succeed: " + uidBody);
        List<String> accountsInOrder = new ArrayList<>();
        uidBody.path("data").path("records")
                .forEach(item -> accountsInOrder.add(item.path("account").asText()));
        assertEquals(2, uidBody.path("data").path("total").asLong(-1),
                "the uid filter must match exactly the two seeded rows");
        assertEquals(java.util.Arrays.asList(marker + "-a1", marker + "-a2"), accountsInOrder,
                "the uid-filtered records must be ordered newest first (actionTime DESC): " + accountsInOrder);
        assertNoTokenField(uidBody);

        // account filter AND-ing with uid: only the exact account's single row survives.
        ResponseEntity<String> accResponse = exchangeJson(HttpMethod.GET,
                SIGN_LOGS_URL + "?page=1&pageSize=10&uid=" + uidA + "&account=" + marker + "-a2", token);
        JsonNode accBody = parse(accResponse);
        assertEquals("200", accBody.path("code").asText(), "the account-filtered query must succeed: " + accBody);
        assertEquals(1, accBody.path("data").path("total").asLong(-1),
                "the account + uid filters must AND-combine to the single a2 row");
        assertEquals(marker + "-a2", accBody.path("data").path("records").path(0).path("account").asText(),
                "the surviving row must be the exact-account match");
        assertNoTokenField(accBody);

        // dateYm filter (combined with uid): the current month holds all of uidA's rows.
        ResponseEntity<String> ymResponse = exchangeJson(HttpMethod.GET,
                SIGN_LOGS_URL + "?page=1&pageSize=10&uid=" + uidA + "&dateYm=" + currentYm, token);
        JsonNode ymBody = parse(ymResponse);
        assertEquals("200", ymBody.path("code").asText(), "the dateYm-filtered query must succeed: " + ymBody);
        assertEquals(2, ymBody.path("data").path("total").asLong(-1),
                "the current-month + uid filter must match exactly the two seeded rows");

        // Blank account is treated as unfiltered (StrUtil.isNotBlank guard), never as an
        // empty-string equality that would match nothing.
        ResponseEntity<String> blankResponse = exchangeJson(HttpMethod.GET,
                SIGN_LOGS_URL + "?page=1&pageSize=10&uid=" + uidA + "&account=", token);
        JsonNode blankBody = parse(blankResponse);
        assertEquals("200", blankBody.path("code").asText(), "the blank-account query must succeed: " + blankBody);
        assertEquals(2, blankBody.path("data").path("total").asLong(-1),
                "a blank account filter must be treated as unfiltered");

        // Pagination edge: a filter combination with no rows answers an empty page.
        ResponseEntity<String> emptyResponse = exchangeJson(HttpMethod.GET,
                SIGN_LOGS_URL + "?page=1&pageSize=10&uid=" + uidA + "&account=no.such.account", token);
        JsonNode emptyBody = parse(emptyResponse);
        assertEquals("200", emptyBody.path("code").asText(), "the empty-result query must still succeed");
        assertEquals(0, emptyBody.path("data").path("records").size(),
                "a filter with no matching rows must produce an empty records array");
        assertEquals(0, emptyBody.path("data").path("total").asLong(-1),
                "the empty result total must be 0");
    }

    /** Pin (a): the operation-log perm must NOT unlock the sign endpoint (design D4). */
    @Test
    void logListPermAloneIsDeniedOnSignEndpoint() throws Exception {
        String adminToken = adminToken();
        long ts = System.currentTimeMillis();
        String roleName = "仅日志角色_" + ts;
        String username = "b2logonly_" + ts;

        int roleId = 0;
        int userId = 0;
        try {
            roleId = createRole(adminToken, roleName);
            // 系统管理(1) > 安全(16) > 日志查询(12): the full ancestor chain whose only
            // perms string is system:log:list - the role's complete grant set.
            exchangeJson(HttpMethod.PUT, ROLES_URL + "/" + roleId + "/menus",
                    "{\"menuIds\":[1,16,12]}", adminToken);
            userId = createUserWithRoles(adminToken, username, roleName, roleId);

            String userToken = loginToken(username, "audit-pass-123");
            assertEquals("200", parse(exchangeJson(HttpMethod.GET,
                    LOGS_URL + "?page=1&pageSize=10", userToken)).path("code").asText(),
                    "the log-only principal must still read the operation log (fixture sanity)");
            assertEquals("A0301", parse(exchangeJson(HttpMethod.GET,
                    SIGN_LOGS_URL + "?page=1&pageSize=10", userToken)).path("code").asText(),
                    "a system:log:list-only principal must be denied on /system/logs/sign");
        } finally {
            cleanupRoleAndUser(roleId, userId);
        }
    }

    /** Pin (b): the sign-log perm stands ALONE - no system:log:list needed (design D4). */
    @Test
    void signLogPermAlonePassesSignEndpointAndNotOperationLog() throws Exception {
        String adminToken = adminToken();
        long ts = System.currentTimeMillis();
        String roleName = "仅登录日志角色_" + ts;
        String username = "b2signlog_" + ts;

        // The signlog authority exists WITHOUT V14 (batch 3): a per-run synthetic menu row
        // carries the perms string, bound through the real grant API, cleaned up after.
        int menuId = seedSignLogMenu(ts);
        int roleId = 0;
        int userId = 0;
        try {
            roleId = createRole(adminToken, roleName);
            // 系统管理(1) > 安全(16) > <synthetic 登录日志>: the full ancestor chain.
            exchangeJson(HttpMethod.PUT, ROLES_URL + "/" + roleId + "/menus",
                    "{\"menuIds\":[1,16," + menuId + "]}", adminToken);
            userId = createUserWithRoles(adminToken, username, roleName, roleId);

            String userToken = loginToken(username, "audit-pass-123");
            assertEquals("200", parse(exchangeJson(HttpMethod.GET,
                    SIGN_LOGS_URL + "?page=1&pageSize=10", userToken)).path("code").asText(),
                    "a system:signlog:list-only principal must pass /system/logs/sign "
                            + "(method-level gate replaces the class-level one, design D4)");
            assertEquals("A0301", parse(exchangeJson(HttpMethod.GET,
                    LOGS_URL + "?page=1&pageSize=10", userToken)).path("code").asText(),
                    "the signlog-only principal must stay denied on the operation log "
                            + "(the two page permissions are independent both ways)");
        } finally {
            cleanupRoleAndUser(roleId, userId);
            cleanupMenu(menuId);
        }
    }

    // ---------- helpers (house style, mirroring AuthLogQueryTests/AuthPermEnforcementTests) ----------

    private String adminToken() throws Exception {
        return loginToken(RUN_ACCOUNT, ADMIN_TEST_PASSWORD);
    }

    private String loginToken(String username, String password) throws Exception {
        ResponseEntity<String> loginResponse = postJson(LOGIN_URL,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        JsonNode loginBody = parse(loginResponse);
        assertEquals("200", loginBody.path("code").asText(), "login must hand out the token: " + loginBody);
        String token = loginBody.path("data").path("token").asText("");
        assertTrue(!token.isEmpty(), "the token must be present");
        return token;
    }

    private int createRole(String token, String roleName) throws Exception {
        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, ROLES_URL,
                "{\"name\":\"" + roleName + "\",\"type\":11}", token);
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(),
                "the role create must succeed: " + createBody);
        int roleId = createBody.path("data").asInt(-1);
        assertTrue(roleId > 1, "the created role id must come back (past the seeded id 1)");
        return roleId;
    }

    private int createUserWithRoles(String token, String username, String name, int roleId)
            throws Exception {
        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, USERS_URL,
                "{\"username\":\"" + username + "\",\"password\":\"audit-pass-123\","
                        + "\"name\":\"" + name + "\",\"roleIds\":[" + roleId + "]}", token);
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(),
                "the user create with roleIds must succeed: " + createBody);
        int userId = createBody.path("data").asInt(-1);
        assertTrue(userId > 1, "the created user id must come back");
        return userId;
    }

    /** Seeds a per-run synthetic menu row carrying the sign-log perms string; returns its id. */
    private int seedSignLogMenu(long ts) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO system.menu_info (up_id, type, title, perms, path, component, sort) "
                        + "VALUES (16, 'C', ?, 'system:signlog:list', ?, NULL, 99) RETURNING id",
                Integer.class, "登录日志_b2probe_" + ts, "b2signlogmenu_" + ts);
    }

    /** HARD-delete cleanup (user ruling 2026-11-07) so failed runs leave no probe grant chain. */
    private void cleanupRoleAndUser(int roleId, int userId) {
        if (roleId > 0) {
            jdbcTemplate.update("DELETE FROM system.role_menu WHERE role_id = ?", roleId);
            jdbcTemplate.update("DELETE FROM system.user_role WHERE role_id = ?", roleId);
            jdbcTemplate.update("DELETE FROM system.role_info WHERE id = ?", roleId);
        }
        if (userId > 0) {
            jdbcTemplate.update("DELETE FROM system.user_role WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM system.user_info WHERE id = ?", userId);
        }
    }

    /**
     * HARD-delete cleanup (b2-review Important #1, batch 3): the probe menu is removed
     * outright - role_menu rows first (the FK on V1's role_menu.menu_id), then the
     * menu_info row. The former logical delete left the soft-deleted row occupying its
     * sequence id forever, and after enough suite runs a probe landed on id 17 - the
     * explicit id the V14 menu seed inserts - where a DO NOTHING seed would have
     * silently no-oped. Sequence advancement past 17 stays (harmless: V14 uses the
     * explicit id and per-run probes keep drawing fresh ids above it).
     */
    private void cleanupMenu(int menuId) {
        if (menuId > 0) {
            jdbcTemplate.update("DELETE FROM system.role_menu WHERE menu_id = ?", menuId);
            jdbcTemplate.update("DELETE FROM system.menu_info WHERE id = ?", menuId);
        }
    }

    /** Design D7: no sign-log response row may carry the credential token field at all. */
    private void assertNoTokenField(JsonNode body) {
        for (JsonNode item : body.path("data").path("records")) {
            assertFalse(item.has("token"), "no sign-log response row may carry the token field: " + item);
        }
    }

    private ResponseEntity<String> postJson(String url, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    /** GET with only the Bearer token (the read-endpoint shape). */
    private ResponseEntity<String> exchangeJson(HttpMethod method, String url, String token) {
        return exchangeJson(method, url, null, token);
    }

    private ResponseEntity<String> exchangeJson(HttpMethod method, String url, String json, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set("Authorization", "Bearer " + token); // Constants.TOKEN_PREFIX
        }
        return restTemplate.exchange(url, method, new HttpEntity<>(json, headers), String.class);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }
}
