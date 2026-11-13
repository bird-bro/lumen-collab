package org.birdbro.lumen.manage;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Role enable toggle contract proof (role-type-dict-api tasks 3.1/3.3, coordination
 * D7): {@code PUT /api/v1/system/roles/{id}/enable} with body {@code {"enable": 0|1}}
 * persists the flag and returns the standard envelope with {@code data: true}; any
 * other body value is rejected WITHOUT mutation (the org-status-toggle mirror:
 * {@code PUT /orgs/{id}/status}); the toggle requires {@code system:role:update}
 * (A0301 for a perm-less principal); {@code roles/list} rows carry {@code enable}
 * and fresh rows read 1 (born enabled - the V24 column default, no drawer field).
 *
 * <p>Behavioral consequences of a disabled role are OUT of scope (D7): the test
 * pins store/display/toggle ONLY - nothing here asserts login or permission
 * recalculation effects.</p>
 *
 * @author birdbro
 */
class AuthRoleEnableToggleTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/system/login";
    private static final String ROLES_URL = "/api/v1/system/roles";
    private static final String ROLES_LIST_URL = "/api/v1/system/roles/list";
    private static final String USERS_URL = "/api/v1/system/users";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void togglePersistsAndEchoesOnList() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int roleId = createRole(token, "启停角色_" + ts);

        // Fresh rows read 1 (born enabled - the V24 column default).
        JsonNode row = listRow(token, roleId);
        assertEquals(1, row.path("enable").asInt(-1),
                "a fresh role must be born enabled: " + row);

        // Disable: standard envelope, data:true, and the list echoes 0.
        JsonNode disabled = parse(exchangeJson(HttpMethod.PUT,
                ROLES_URL + "/" + roleId + "/enable", "{\"enable\":0}", token));
        assertEquals("200", disabled.path("code").asText(),
                "the disable command must succeed: " + disabled);
        assertEquals(true, disabled.path("data").asBoolean(),
                "the toggle returns data: true (org-status-toggle mirror): " + disabled);
        assertEquals(0, listRow(token, roleId).path("enable").asInt(-1),
                "the disabled flag must persist through the list payload");

        // Re-enable: back to 1.
        JsonNode enabled = parse(exchangeJson(HttpMethod.PUT,
                ROLES_URL + "/" + roleId + "/enable", "{\"enable\":1}", token));
        assertEquals("200", enabled.path("code").asText(),
                "the re-enable command must succeed: " + enabled);
        assertEquals(1, listRow(token, roleId).path("enable").asInt(-1),
                "the re-enabled flag must persist");

        // The toggle must not disturb the other row fields.
        assertEquals("启停角色_" + ts, listRow(token, roleId).path("name").asText(),
                "the toggle must not alter profile fields");

        cleanupRoles(roleId);
    }

    @Test
    void toggleRejectsValuesOtherThanZeroOrOne() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int roleId = createRole(token, "启停角色_" + ts);

        for (String body : new String[] {"{\"enable\":2}", "{}", "{\"enable\":null}"}) {
            JsonNode rejected = parse(exchangeJson(HttpMethod.PUT,
                    ROLES_URL + "/" + roleId + "/enable", body, token));
            assertTrue(!"200".equals(rejected.path("code").asText()),
                    "a non-0/1 enable body must be rejected: " + body + " -> " + rejected);
        }
        // Without mutation: the flag still reads 1.
        assertEquals(1, listRow(token, roleId).path("enable").asInt(-1),
                "the rejected toggles must not have mutated the flag");

        cleanupRoles(roleId);
    }

    @Test
    void toggleRequiresUpdateAuthority() throws Exception {
        String adminToken = adminToken();
        long ts = System.currentTimeMillis();
        int roleId = createRole(adminToken, "启停角色_" + ts);

        // A perm-less principal (the registered 无权角色_ family) gets A0301.
        int roleIdNoPerm = createRole(adminToken, "无权角色_" + ts);
        String username = "noperm_u_" + ts;
        int userId = createUserWithRoles(adminToken, username, "无权用户_" + ts, roleIdNoPerm);
        String userToken = loginToken(username);

        JsonNode denied = parse(exchangeJson(HttpMethod.PUT,
                ROLES_URL + "/" + roleId + "/enable", "{\"enable\":0}", userToken));
        assertEquals("A0301", denied.path("code").asText(),
                "a perm-less toggle must be denied at the page gate: " + denied);
        assertEquals(1, listRow(adminToken, roleId).path("enable").asInt(-1),
                "the denied toggle must not have mutated the flag");

        // And an anonymous call hits the auth entry point (HTTP 200 + envelope 203,
        // the copied OPS entry-point shape).
        ResponseEntity<String> anonymous = restTemplate.exchange(
                ROLES_URL + "/" + roleId + "/enable", HttpMethod.PUT,
                new HttpEntity<>("{\"enable\":0}", new HttpHeaders()), String.class);
        JsonNode anonBody = parse(anonymous);
        assertEquals("203", anonBody.path("code").asText(),
                "an unauthenticated toggle must hit the auth entry point: " + anonBody);

        cleanupRoleAndUser(roleIdNoPerm, userId);
        cleanupRoles(roleId);
    }

    // ===== helpers (the sibling-suite house style) =====

    private String adminToken() throws Exception {
        ResponseEntity<String> loginResponse = postJson(LOGIN_URL, runAdminLoginBody());
        JsonNode loginBody = parse(loginResponse);
        assertEquals("200", loginBody.path("code").asText(), "admin login must hand out the token");
        return loginBody.path("data").path("token").asText("");
    }

    private int createRole(String token, String roleName) throws Exception {
        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, ROLES_URL,
                "{\"name\":\"" + roleName + "\",\"type\":11}", token);
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(),
                "the role create must succeed: " + createBody);
        return createBody.path("data").asInt(-1);
    }

    private int createUserWithRoles(String token, String username, String name, int roleId)
            throws Exception {
        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, USERS_URL,
                "{\"username\":\"" + username + "\",\"password\":\"" + encryptTestPassword("audit-pass-123") + "\","
                        + "\"name\":\"" + name + "\",\"roleIds\":[" + roleId + "]}", token);
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(),
                "the user create with roleIds must succeed: " + createBody);
        return createBody.path("data").asInt(-1);
    }

    private String loginToken(String username) throws Exception {
        ResponseEntity<String> loginResponse = postJson(LOGIN_URL, loginBody(username, "audit-pass-123"));
        JsonNode loginBody = parse(loginResponse);
        assertEquals("200", loginBody.path("code").asText(), "login must hand out the token");
        return loginBody.path("data").path("token").asText("");
    }

    private JsonNode listRow(String token, int roleId) throws Exception {
        JsonNode body = parse(exchangeJson(HttpMethod.GET,
                ROLES_LIST_URL + "?pageNum=1&pageSize=50", null, token));
        assertEquals("200", body.path("code").asText(),
                "the role list must answer the success envelope: " + body);
        JsonNode row = rowWithId(body.path("data").path("records"), roleId);
        assertNotNull(row, "the fixture role must be on page 1");
        return row;
    }

    private static JsonNode rowWithId(JsonNode records, int id) {
        for (JsonNode row : records) {
            if (row.path("id").asInt(-1) == id) {
                return row;
            }
        }
        return null;
    }

    /** HARD-delete cleanup (user ruling 2026-11-07), bindings before owners. */
    private void cleanupRoles(int... roleIds) {
        for (int roleId : roleIds) {
            jdbcTemplate.update("DELETE FROM system.role_menu WHERE role_id = ?", roleId);
            jdbcTemplate.update("DELETE FROM system.role_info WHERE id = ?", roleId);
        }
    }

    private void cleanupRoleAndUser(int roleId, int userId) {
        jdbcTemplate.update("DELETE FROM system.role_menu WHERE role_id = ?", roleId);
        jdbcTemplate.update("DELETE FROM system.user_role WHERE role_id = ?", roleId);
        jdbcTemplate.update("DELETE FROM system.role_info WHERE id = ?", roleId);
        jdbcTemplate.update("DELETE FROM system.user_role WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM system.user_info WHERE id = ?", userId);
    }

    private ResponseEntity<String> postJson(String url, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    private ResponseEntity<String> exchangeJson(HttpMethod method, String url, String json, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token); // Constants.TOKEN_PREFIX
        return restTemplate.exchange(url, method, new HttpEntity<>(json, headers), String.class);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }
}
