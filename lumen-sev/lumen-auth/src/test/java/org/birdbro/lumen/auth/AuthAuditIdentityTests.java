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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit identity proof (auth-rbac-foundation task 3.3): with the admin token, a write
 * through {@code POST /api/v1/auth/users} must persist {@code create_by} = the admin id
 * (1) - the copied {@code MetaObjectConfig} insert fill fed by the lumen
 * {@code UserHeaderBridgeFilter}, which derives the OPS-style USER-HEADER request header
 * from the JWT-authenticated principal. Proven against the real database (team test
 * server), not a mock.
 *
 * <p>Shared long-lived database: the created user carries a timestamp-suffixed unique
 * username and is logically deleted after the assertion (Batch 2 data-layer precedent).</p>
 *
 * @author birdbro
 */
class AuthAuditIdentityTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String USERS_URL = "/api/v1/auth/users";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createUserWithAdminTokenFillsCreateByWithAdminId() throws Exception {
        ResponseEntity<String> loginResponse = postJson(LOGIN_URL,
                "{\"username\":\"admin\",\"password\":\"admin123\"}");
        JsonNode loginBody = parse(loginResponse);
        assertEquals("200", loginBody.path("code").asText(), "login must succeed to hand out the token");
        String token = loginBody.path("data").path("token").asText("");

        String username = "audit_user_" + System.currentTimeMillis(); // unique - shared DB
        ResponseEntity<String> createResponse = restTemplate.exchange(USERS_URL, HttpMethod.POST,
                entity("{\"username\":\"" + username + "\",\"password\":\"audit-pass-123\","
                        + "\"name\":\"audit identity\"}", token), String.class);

        assertEquals(200, createResponse.getStatusCodeValue());
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(), "the create must succeed: " + createBody);
        Integer createdId = createBody.path("data").asInt(-1);
        assertTrue(createdId > 1, "the created row's id must come back (past the seeded ids)");

        // The real database verdict: create_by must be the admin's id (1) - MetaObjectConfig
        // fill proven end-to-end - and create_time non-null (DB default).
        Integer createBy = jdbcTemplate.queryForObject(
                "SELECT create_by FROM system.user_info WHERE id = ?", Integer.class, createdId);
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM system.user_info WHERE id = ?", Integer.class, createdId);
        java.sql.Date createTime = jdbcTemplate.queryForObject(
                "SELECT create_time FROM system.user_info WHERE id = ?", java.sql.Date.class, createdId);
        assertNotNull(createTime, "create_time must be non-null");
        assertEquals(Integer.valueOf(0), deleted, "the new row must be live (deleted = 0)");
        assertEquals(Integer.valueOf(1), createBy,
                "create_by must be the admin id 1 - the JWT principal must reach MetaObjectConfig"
                        + " (bridge filter) and fill the audit column; actual: " + createBy);

        // Logical cleanup (shared DB): hide the row after the evidence is captured.
        jdbcTemplate.update("UPDATE system.user_info SET deleted = 1 WHERE id = ?", createdId);
    }

    private ResponseEntity<String> postJson(String url, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    private HttpEntity<String> entity(String json, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token); // Constants.TOKEN_PREFIX
        return new HttpEntity<>(json, headers);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }
}
