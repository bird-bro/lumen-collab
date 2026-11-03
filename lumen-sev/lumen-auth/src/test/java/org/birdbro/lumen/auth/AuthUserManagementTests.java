package org.birdbro.lumen.auth;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * User management proof (auth-rbac-foundation task 4.3): the full {@code
 * /api/v1/auth/users} endpoint set - list (DTO, NEVER the password hash), create with
 * the duplicate-username rejection ({@code A0111}, the Batch-3 review handoff), the
 * admin password reset (BCrypt + must-change-password flag, proven by logging in with
 * the NEW password), and enable/disable (a disabled user's login is rejected
 * {@code A0202}, re-enabling restores it). Driven through the real servlet context with
 * the admin token. Shared long-lived database: unique timestamp-suffixed usernames,
 * logical-delete cleanup.
 *
 * @author birdbro
 */
class AuthUserManagementTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String USERS_URL = "/api/v1/auth/users";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createUserThenListCarriesDtoWithoutPasswordHash() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String username = "mgmt_u_" + ts; // unique - shared DB

        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, USERS_URL,
                "{\"username\":\"" + username + "\",\"password\":\"plain-pass-123\","
                        + "\"name\":\"管理用户\"}", token);
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(), "the create must succeed: " + createBody);
        int createdId = createBody.path("data").asInt(-1);
        assertTrue(createdId > 1, "the created user id must come back (past the seeded ids)");

        ResponseEntity<String> listResponse = exchangeJson(HttpMethod.GET, USERS_URL + "/list", null, token);
        assertEquals(200, listResponse.getStatusCodeValue());
        JsonNode listBody = parse(listResponse);
        assertEquals("200", listBody.path("code").asText(), "the user list must succeed: " + listBody);

        JsonNode item = userWithUsername(listBody.path("data"), username);
        assertTrue(item != null, "the created user must be listed (shared DB, CONTAINS-style)");
        assertFalse(item.has("password"),
                "the list item DTO must NOT even carry a password field - the BCrypt hash "
                        + "must never serialize (task 4.3 binding: DTO, no entity leakage)");
        assertEquals(createdId, item.path("uid").asInt(-1), "the list item exposes uid (not id)");
        assertEquals("管理用户", item.path("name").asText());
        assertEquals(1, item.path("enable").asInt(-1), "a fresh user is enabled (V1 default)");
        assertEquals(0, item.path("mustChangePassword").asInt(-1),
                "a fresh user carries no must-change flag (V1 default)");
        assertFalse(item.path("createTime").asText("").isEmpty(),
                "the list item carries the create time");

        cleanupUser(createdId);
    }

    @Test
    void duplicateUsernameRejectedWithA0111() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String username = "dup_u_" + ts; // unique - shared DB

        ResponseEntity<String> firstResponse = exchangeJson(HttpMethod.POST, USERS_URL,
                "{\"username\":\"" + username + "\",\"password\":\"first-pass-123\","
                        + "\"name\":\"重复用户\"}", token);
        JsonNode firstBody = parse(firstResponse);
        assertEquals("200", firstBody.path("code").asText(), "the FIRST create must succeed: " + firstBody);
        int createdId = firstBody.path("data").asInt(-1);

        ResponseEntity<String> secondResponse = exchangeJson(HttpMethod.POST, USERS_URL,
                "{\"username\":\"" + username + "\",\"password\":\"second-pass-123\","
                        + "\"name\":\"重复用户\"}", token);
        assertEquals(200, secondResponse.getStatusCodeValue());
        JsonNode secondBody = parse(secondResponse);
        assertEquals("A0111", secondBody.path("code").asText(),
                "the service-level duplicate check must reject an existing live username "
                        + "(the DB partial unique index stays the backstop): " + secondBody);
        assertFalse(secondBody.path("msg").asText().isEmpty(),
                "the A-class rejection carries a descriptive msg");
        assertFalse(secondBody.hasNonNull("data"), "no id is handed out on rejection");

        cleanupUser(createdId);
    }

    @Test
    void adminResetPasswordForcesChangeAndNewPasswordLogsIn() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String username = "pw_u_" + ts; // unique - shared DB

        int createdId = createUser(token, username, "old-pass-123");

        ResponseEntity<String> resetResponse = exchangeJson(HttpMethod.PUT,
                USERS_URL + "/" + createdId + "/password",
                "{\"newPassword\":\"new-pass-456\"}", token);
        JsonNode resetBody = parse(resetResponse);
        assertEquals("200", resetBody.path("code").asText(),
                "the admin password reset must succeed: " + resetBody);

        // The OLD password no longer matches - the hash was REPLACED, not added to.
        JsonNode oldLoginBody = parse(postJson(LOGIN_URL,
                "{\"username\":\"" + username + "\",\"password\":\"old-pass-123\"}"));
        assertEquals("A0210", oldLoginBody.path("code").asText(),
                "the old password must be rejected after the admin reset");

        // The NEW password logs in, and the reset forced the must-change flag.
        JsonNode newLoginBody = parse(postJson(LOGIN_URL,
                "{\"username\":\"" + username + "\",\"password\":\"new-pass-456\"}"));
        assertEquals("200", newLoginBody.path("code").asText(),
                "the new password must log in (BCrypt re-encode proven end-to-end): "
                        + newLoginBody);
        assertEquals(1, newLoginBody.path("data").path("profile").path("mustChangePassword").asInt(-1),
                "the admin reset must set must_change_password = 1");

        cleanupUser(createdId);
    }

    @Test
    void disableUserBlocksLoginWithA0202ThenReEnableRestores() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String username = "en_u_" + ts; // unique - shared DB

        int createdId = createUser(token, username, "enable-pass-123");

        ResponseEntity<String> disableResponse = exchangeJson(HttpMethod.PUT,
                USERS_URL + "/" + createdId + "/enable", "{\"enable\":0}", token);
        JsonNode disableBody = parse(disableResponse);
        assertEquals("200", disableBody.path("code").asText(),
                "the disable must succeed: " + disableBody);

        JsonNode disabledLoginBody = parse(postJson(LOGIN_URL,
                "{\"username\":\"" + username + "\",\"password\":\"enable-pass-123\"}"));
        assertEquals("A0202", disabledLoginBody.path("code").asText(),
                "a disabled user's login must be rejected with the copied A0202 envelope");

        ResponseEntity<String> enableResponse = exchangeJson(HttpMethod.PUT,
                USERS_URL + "/" + createdId + "/enable", "{\"enable\":1}", token);
        JsonNode enableBody = parse(enableResponse);
        assertEquals("200", enableBody.path("code").asText(),
                "the re-enable must succeed: " + enableBody);

        JsonNode enabledLoginBody = parse(postJson(LOGIN_URL,
                "{\"username\":\"" + username + "\",\"password\":\"enable-pass-123\"}"));
        assertEquals("200", enabledLoginBody.path("code").asText(),
                "re-enabling must restore the login");

        cleanupUser(createdId);
    }

    private String adminToken() throws Exception {
        ResponseEntity<String> loginResponse = postJson(LOGIN_URL,
                "{\"username\":\"admin\",\"password\":\"admin123\"}");
        JsonNode loginBody = parse(loginResponse);
        assertEquals("200", loginBody.path("code").asText(), "admin login must hand out the token");
        String token = loginBody.path("data").path("token").asText("");
        assertTrue(!token.isEmpty(), "the admin token must be present");
        return token;
    }

    /** Creates a user through the endpoint and returns the new id. */
    private int createUser(String token, String username, String password) throws Exception {
        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, USERS_URL,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\","
                        + "\"name\":\"管理用户\"}", token);
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(),
                "the user create must succeed: " + createBody);
        int createdId = createBody.path("data").asInt(-1);
        assertTrue(createdId > 1, "the created user id must come back");
        return createdId;
    }

    private void cleanupUser(int id) {
        jdbcTemplate.update("UPDATE system.user_info SET deleted = 1 WHERE id = ?", id);
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

    private static JsonNode userWithUsername(JsonNode rows, String username) {
        if (!rows.isArray()) {
            return null;
        }
        for (JsonNode row : rows) {
            if (username.equals(row.path("username").asText())) {
                return row;
            }
        }
        return null;
    }
}
