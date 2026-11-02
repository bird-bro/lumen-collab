package org.birdbro.lumen.auth;

import java.util.ArrayList;
import java.util.List;

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
 * Department management proof (auth-rbac-foundation task 4.2): the {@code
 * /api/v1/auth/departments} CRUD endpoints against the real servlet context with the
 * admin token.
 *
 * <p>The required scenario is the SEEDED LIST (spec: Department Anchored to the Unit
 * Tree): the V2 seed's three departments (radiology / us / endo) must come back from
 * the list endpoint, and a department created for the hospital root (unit id 1) must
 * appear under that unit. The FK is enforced SERVICE-side with an {@code A0400}
 * rejection for a non-existent unit (the DB FK stays as the backstop) - proven by the
 * third test. Shared long-lived database: unique timestamp-suffixed code, logical-delete
 * cleanup.</p>
 *
 * @author birdbro
 */
class AuthDepartmentTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String DEPARTMENTS_URL = "/api/v1/auth/departments";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seededThreeDepartmentsAreListed() throws Exception {
        String token = adminToken();

        ResponseEntity<String> listResponse = exchangeJson(HttpMethod.GET,
                DEPARTMENTS_URL + "/list", null, token);
        assertEquals(200, listResponse.getStatusCodeValue());
        JsonNode listBody = parse(listResponse);
        assertEquals("200", listBody.path("code").asText(),
                "the department list must succeed: " + listBody);
        JsonNode rows = listBody.path("data");
        assertTrue(rows.isArray(), "the list data must be an array");

        assertTrue(departmentWithCode(rows, "radiology") != null,
                "the seeded radiology department must be listed (spec: three departments)");
        assertTrue(departmentWithCode(rows, "us") != null,
                "the seeded ultrasound department must be listed");
        assertTrue(departmentWithCode(rows, "endo") != null,
                "the seeded endoscopy department must be listed");

        JsonNode radiology = departmentWithCode(rows, "radiology");
        assertEquals("放射科", radiology.path("name").asText(),
                "the seeded display name must come through");
        assertEquals(1, radiology.path("unitId").asInt(-1),
                "the seeded departments are FK-linked to the hospital root (unit id 1)");
    }

    @Test
    void createDepartmentForHospitalRootAppearsInUnitList() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String code = "dep_" + ts; // unique among live rows - shared DB
        String name = "体检科_" + ts;

        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, DEPARTMENTS_URL,
                "{\"unitId\":1,\"code\":\"" + code + "\",\"name\":\"" + name + "\"}", token);
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(),
                "the department create must succeed: " + createBody);
        int createdId = createBody.path("data").asInt(-1);
        assertTrue(createdId > 0, "the created department id must come back");

        ResponseEntity<String> unitListResponse = exchangeJson(HttpMethod.GET,
                DEPARTMENTS_URL + "/list?unitId=1", null, token);
        JsonNode unitListBody = parse(unitListResponse);
        assertEquals("200", unitListBody.path("code").asText(),
                "the unit-scoped list must succeed: " + unitListBody);
        assertNotNull(departmentWithCode(unitListBody.path("data"), code),
                "the created department must appear in the list of its unit (unitId=1)");

        ResponseEntity<String> otherListResponse = exchangeJson(HttpMethod.GET,
                DEPARTMENTS_URL + "/list?unitId=" + createdId, null, token);
        JsonNode otherListBody = parse(otherListResponse);
        assertEquals("200", otherListBody.path("code").asText());
        assertTrue(departmentWithCode(otherListBody.path("data"), code) == null,
                "a department of unit 1 must NOT appear under a different unit");

        jdbcTemplate.update("UPDATE auth.department SET deleted = 1 WHERE id = ?", createdId);
    }

    @Test
    void createDepartmentWithUnknownUnitRejectedWithA0400() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();

        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, DEPARTMENTS_URL,
                "{\"unitId\":987654321,\"code\":\"orphan_" + ts + "\",\"name\":\"孤儿科室_" + ts + "\"}",
                token);
        assertEquals(200, createResponse.getStatusCodeValue());
        JsonNode createBody = parse(createResponse);
        assertEquals("A0400", createBody.path("code").asText(),
                "a non-existent FK unit must be rejected service-side (the DB FK stays the "
                        + "backstop): " + createBody);
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

    private static JsonNode departmentWithCode(JsonNode rows, String code) {
        if (!rows.isArray()) {
            return null;
        }
        for (JsonNode row : rows) {
            if (code.equals(row.path("code").asText())) {
                return row;
            }
        }
        return null;
    }
}
