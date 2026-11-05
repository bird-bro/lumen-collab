package org.birdbro.lumen.system;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import org.birdbro.common.core.pojo.user.UserLogin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server-side permission enforcement matrix (server-side-perm-enforcement tasks 1.1
 * and 2.1, spec deltas auth-rbac + errors): page-level method security over the seven
 * management families, city staying authenticated-only, the {@code A0301} deny
 * envelope over the HTTP-200 transport, and the `"203"` entry-point precedence.
 *
 * <p>TDD matrix — RED-first: while {@code UserLogin.getAuthorities()} returns null and
 * no method security exists, every DENY assertion below fails (the endpoint answers
 * 200); the pass-through assertions (admin, city, tokenless-203) are regression guards
 * that pass before AND after. The stale-session scenario is covered at the POJO level
 * (a null permission set must yield empty authorities, never null) plus the
 * perm-less-user integration case (the observable equivalent).</p>
 *
 * <p>Shared long-lived database: unique timestamp-suffixed names, logical-delete
 * cleanup.</p>
 *
 * @author birdbro
 */
class AuthPermEnforcementTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/system/login";
    private static final String USERS_URL = "/api/v1/system/users";
    private static final String ROLES_URL = "/api/v1/system/roles";
    private static final String MENUS_TREE_URL = "/api/v1/system/menus/tree";
    private static final String ORGS_TREE_URL = "/api/v1/system/orgs/tree";
    private static final String DEPS_LIST_URL = "/api/v1/system/org-deps/list";
    private static final String DETAIL_LIST_URL = "/api/v1/system/dicts/detail/list?caId=1000";
    private static final String LOGS_URL = "/api/v1/system/logs?pageNum=1&pageSize=10";
    private static final String CITY_TREE_URL = "/api/v1/system/dicts/city/tree";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** POJO level: authorities derive from the permission set, empty-safe, never null. */
    @Test
    void authoritiesDeriveFromPermissionSetAndAreNeverNull() {
        UserLogin empty = new UserLogin();
        empty.setPermissions(null);
        assertNotNull(empty.getAuthorities(),
                "null permissions must map to EMPTY authorities, never null (V-fix)");
        assertTrue(empty.getAuthorities().isEmpty(), "a null set yields no authorities");

        UserLogin granted = new UserLogin();
        granted.setPermissions(Set.of("system:user:list"));
        assertEquals(1, granted.getAuthorities().size(),
                "each permission string becomes one granted authority");
        assertEquals("system:user:list",
                granted.getAuthorities().iterator().next().getAuthority());
    }

    /** Core deny: a role with NO menus => empty permission set => A0301 on every family. */
    @Test
    void permLessUserIsDeniedOnEveryFamily() throws Exception {
        String adminToken = adminToken();
        long ts = System.currentTimeMillis();
        String roleName = "无权角色_" + ts;
        String username = "noperm_u_" + ts;

        int roleId = createRole(adminToken, roleName);
        int userId = createUserWithRoles(adminToken, username, roleName, roleId);

        String userToken = loginToken(username);
        List<String> probeUrls = List.of(
                USERS_URL + "/list?pageNum=1&pageSize=10",
                MENUS_TREE_URL,
                ORGS_TREE_URL,
                DEPS_LIST_URL,
                DETAIL_LIST_URL,
                LOGS_URL);
        for (String url : probeUrls) {
            JsonNode body = parse(exchangeJson(HttpMethod.GET, url, null, userToken));
            assertEquals("A0301", body.path("code").asText(),
                    "a perm-less authenticated call must be denied on " + url + ": " + body);
        }
        // The roles family is write-only - probe it through a valid create body: the
        // denial must fire BEFORE the controller (the role must not be created).
        JsonNode rolesBody = parse(exchangeJson(HttpMethod.POST, ROLES_URL,
                "{\"name\":\"denied_role_" + ts + "\",\"type\":11}", userToken));
        assertEquals("A0301", rolesBody.path("code").asText(),
                "a perm-less role create must be denied: " + rolesBody);
        Integer deniedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_info WHERE name = ?", Integer.class,
                "denied_role_" + ts);
        assertEquals(Integer.valueOf(0), deniedCount,
                "the denied create must not reach the controller (no role row)");

        cleanupRoleAndUser(roleId, userId);
    }

    /** Pass-through: the administrator (owns every menu) reads every read-family. */
    @Test
    void adminPassesReadFamiliesAndCity() throws Exception {
        String token = adminToken();
        List<String> probeUrls = List.of(
                USERS_URL + "/list?pageNum=1&pageSize=10",
                MENUS_TREE_URL,
                ORGS_TREE_URL,
                DEPS_LIST_URL,
                DETAIL_LIST_URL,
                LOGS_URL,
                CITY_TREE_URL);
        for (String url : probeUrls) {
            JsonNode body = parse(exchangeJson(HttpMethod.GET, url, null, token));
            assertEquals("200", body.path("code").asText(),
                    "the admin pass-through must hold on " + url + ": " + body);
        }
    }

    /** Cross-family: a page perm grants exactly its own family - nothing leaks sideways. */
    @Test
    void crossFamilyPermDoesNotLeak() throws Exception {
        String adminToken = adminToken();
        long ts = System.currentTimeMillis();
        String roleName = "单页角色_" + ts;
        String username = "single_u_" + ts;

        int roleId = createRole(adminToken, roleName);
        // 系统管理(1) > 用户权限(14) > 用户管理(2): the full ancestor chain, whose only
        // perms string is system:user:list.
        exchangeJson(HttpMethod.PUT, ROLES_URL + "/" + roleId + "/menus",
                "{\"menuIds\":[1,14,2]}", adminToken);
        int userId = createUserWithRoles(adminToken, username, roleName, roleId);

        String userToken = loginToken(username);
        assertEquals("200", parse(exchangeJson(HttpMethod.GET,
                USERS_URL + "/list?pageNum=1&pageSize=10", null, userToken)).path("code").asText(),
                "the granted family must pass");
        assertEquals("A0301", parse(exchangeJson(HttpMethod.GET,
                MENUS_TREE_URL, null, userToken)).path("code").asText(),
                "a sibling family must deny: system:user:list must not read menus");
        assertEquals("A0301", parse(exchangeJson(HttpMethod.GET,
                LOGS_URL, null, userToken)).path("code").asText(),
                "a sibling family must deny: system:user:list must not read logs");

        cleanupRoleAndUser(roleId, userId);
    }

    /** City reference data stays authenticated-only - any valid token reads it. */
    @Test
    void cityStaysAuthenticatedOnly() throws Exception {
        String adminToken = adminToken();
        long ts = System.currentTimeMillis();
        String roleName = "无权城市角色_" + ts;
        String username = "city_u_" + ts;

        int roleId = createRole(adminToken, roleName);
        int userId = createUserWithRoles(adminToken, username, roleName, roleId);

        String userToken = loginToken(username);
        JsonNode body = parse(exchangeJson(HttpMethod.GET, CITY_TREE_URL, null, userToken));
        assertEquals("200", body.path("code").asText(),
                "city reference queries stay authenticated-only (no page perm): " + body);

        cleanupRoleAndUser(roleId, userId);
    }

    /** Entry-point precedence: a tokenless request still answers "203", never "A0301". */
    @Test
    void tokenlessStillAnswers203() throws Exception {
        JsonNode body = parse(exchangeJson(HttpMethod.GET,
                USERS_URL + "/list?pageNum=1&pageSize=10", null, null));
        assertEquals("203", body.path("code").asText(),
                "tokenless must stay the entry point's 203: " + body);
    }

    // ---------- helpers (house style, mirroring AuthRoleMenuTests) ----------

    private String adminToken() throws Exception {
        return loginToken("admin");
    }

    private String loginToken(String username) throws Exception {
        ResponseEntity<String> loginResponse = postJson(LOGIN_URL,
                "{\"username\":\"" + username + "\",\"password\":\""
                        + ("admin".equals(username) ? "admin123" : "audit-pass-123") + "\"}");
        JsonNode loginBody = parse(loginResponse);
        assertEquals("200", loginBody.path("code").asText(), "login must hand out the token");
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

    private void cleanupRoleAndUser(int roleId, int userId) {
        jdbcTemplate.update("UPDATE system.role_menu SET deleted = 1 WHERE role_id = ?", roleId);
        jdbcTemplate.update("UPDATE system.user_role SET deleted = 1 WHERE role_id = ?", roleId);
        jdbcTemplate.update("UPDATE system.role_info SET deleted = 1 WHERE id = ?", roleId);
        jdbcTemplate.update("UPDATE system.user_info SET deleted = 1 WHERE id = ?", userId);
    }

    private ResponseEntity<String> postJson(String url, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
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
