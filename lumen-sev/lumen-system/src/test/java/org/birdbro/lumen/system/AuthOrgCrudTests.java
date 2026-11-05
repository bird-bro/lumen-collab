package org.birdbro.lumen.system;

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
 * Org-tree management proof (auth-rbac-foundation task 4.1): the {@code /api/v1/system/
 * orgs} CRUD + tree endpoints, driven through the real servlet context with the admin
 * token (TestRestTemplate against the RANDOM_PORT server of {@link
 * AuthIntegrationTestBase}).
 *
 * <p>The required scenario is the MULTI-ROOT capability (spec: Hierarchical Org Tree):
 * a second root is just another {@code upId = 0} row, so the tree must hold the seeded
 * hospital root AND the created root side by side, and a child created under the
 * hospital root must nest there (after a rename proves the update path). Shared
 * long-lived database: unique timestamp-suffixed names, logical-delete cleanup.</p>
 *
 * @author birdbro
 */
class AuthOrgCrudTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/system/login";
    private static final String ORGS_URL = "/api/v1/system/orgs";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void secondRootAppearsAsIndependentTreeRoot() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String rootName = "第二根_" + ts; // unique - shared DB

        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, ORGS_URL,
                "{\"upId\":0,\"level\":1,\"type\":0,\"name\":\"" + rootName + "\"}", token);
        assertEquals(200, createResponse.getStatusCodeValue(),
                "org create must answer over HTTP 200 (envelope transport)");
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(),
                "the root-org create must succeed: " + createBody);
        int createdId = createBody.path("data").asInt(-1);
        assertTrue(createdId > 0, "the created org id must come back (IdType.AUTO backfill)");

        ResponseEntity<String> treeResponse = exchangeJson(HttpMethod.GET, ORGS_URL + "/tree", null, token);
        assertEquals(200, treeResponse.getStatusCodeValue());
        JsonNode treeBody = parse(treeResponse);
        assertEquals("200", treeBody.path("code").asText(), "the tree query must succeed: " + treeBody);
        JsonNode roots = treeBody.path("data");
        assertTrue(roots.isArray(), "the tree data must be the root array");

        List<String> rootNames = new ArrayList<>();
        roots.forEach(root -> rootNames.add(root.path("name").asText()));
        assertTrue(rootNames.contains("示例医院"),
                "the seeded hospital root must stay a tree root; roots were " + rootNames);
        assertTrue(rootNames.contains(rootName),
                "the new upId=0 row must be an INDEPENDENT second root (multi-root capable); "
                        + "roots were " + rootNames);
        assertTrue(rootNames.size() >= 2, "the tree must hold at least the two roots");

        jdbcTemplate.update("UPDATE system.org_info SET deleted = 1 WHERE id = ?", createdId);
    }

    @Test
    void childOrgNestsUnderHospitalRootAfterCreateAndRename() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String childName = "二级机构_" + ts; // unique - shared DB
        String renamedName = "二级机构改名_" + ts;

        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, ORGS_URL,
                "{\"upId\":1,\"type\":1,\"name\":\"" + childName + "\"}", token);
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(),
                "the child create (upId = 1, level omitted) must succeed: " + createBody);
        int createdId = createBody.path("data").asInt(-1);
        assertTrue(createdId > 0, "the created child id must come back");

        ResponseEntity<String> renameResponse = exchangeJson(HttpMethod.PUT, ORGS_URL + "/" + createdId,
                "{\"name\":\"" + renamedName + "\"}", token);
        JsonNode renameBody = parse(renameResponse);
        assertEquals("200", renameBody.path("code").asText(),
                "the rename (partial update) must succeed: " + renameBody);

        ResponseEntity<String> treeResponse = exchangeJson(HttpMethod.GET, ORGS_URL + "/tree", null, token);
        JsonNode treeBody = parse(treeResponse);
        assertEquals("200", treeBody.path("code").asText(), "the tree query must succeed: " + treeBody);

        JsonNode hospitalRoot = rootWithName(treeBody.path("data"), "示例医院");
        assertNotNull(hospitalRoot, "the seeded hospital root must be present in the tree");
        List<String> childNames = new ArrayList<>();
        hospitalRoot.path("children").forEach(child -> childNames.add(child.path("name").asText()));
        assertTrue(childNames.contains(renamedName),
                "the created child must nest under the hospital root with the renamed title; "
                        + "children were " + childNames);

        jdbcTemplate.update("UPDATE system.org_info SET deleted = 1 WHERE id = ?", createdId);
    }

    @Test
    void createOrgUnderUnknownParentRejectedWithA0400() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();

        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, ORGS_URL,
                "{\"upId\":987654321,\"name\":\"孤儿机构_" + ts + "\"}", token);
        assertEquals(200, createResponse.getStatusCodeValue());
        JsonNode createBody = parse(createResponse);
        assertEquals("A0400", createBody.path("code").asText(),
                "a non-existent parent org must be rejected service-side (org_info has no "
                        + "self-FK, tree integrity is service-level): " + createBody);
    }

    private String adminToken() throws Exception {
        ResponseEntity<String> loginResponse = postJson(LOGIN_URL, runAdminLoginBody());
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

    private static JsonNode rootWithName(JsonNode roots, String name) {
        if (!roots.isArray()) {
            return null;
        }
        for (JsonNode root : roots) {
            if (name.equals(root.path("name").asText())) {
                return root;
            }
        }
        return null;
    }
}
