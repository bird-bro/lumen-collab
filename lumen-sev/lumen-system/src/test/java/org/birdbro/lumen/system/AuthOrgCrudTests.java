package org.birdbro.lumen.system;

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
 * Controlled organization management proof (org-management-redesign-api): hospital and
 * campus create/update/delete guards are driven through the real servlet context with the
 * run-scoped admin token. The shared database keeps the live seeded hospital root, so
 * these tests exercise controlled campuses and clean up their own fixtures.
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
    void secondRootIsRejectedAndCampusCannotContainChild() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int campusId = createCampus("守卫院区_" + ts);

        JsonNode secondRoot = parse(exchangeJson(HttpMethod.POST, ORGS_URL,
                "{\"upId\":0,\"name\":\"第二根_" + ts + "\"}", token));
        assertEquals("A0400", secondRoot.path("code").asText(),
                "a second live hospital root must be rejected: " + secondRoot);

        JsonNode nestedOrg = parse(exchangeJson(HttpMethod.POST, ORGS_URL,
                "{\"upId\":" + campusId + ",\"name\":\"院区子机构_" + ts + "\"}", token));
        assertEquals("A0400", nestedOrg.path("code").asText(),
                "a campus must remain a leaf in org_info: " + nestedOrg);

        softDeleteOrg(campusId);
    }

    @Test
    void campusLifecycleProfileUpdateAndLevelDerivation() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String campusName = "东院区_" + ts;
        String campusCode = "CAMPUS_T_" + ts;
        String updatedName = "东院区改名_" + ts;

        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, ORGS_URL,
                "{\"upId\":1,\"level\":9,\"type\":7,\"name\":\"" + campusName
                        + "\",\"code\":\"" + campusCode + "\",\"shortName\":\"东院\",\"sort\":2}", token);
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(),
                "the hospital may create a campus: " + createBody);
        int campusId = createBody.path("data").asInt(-1);
        assertTrue(campusId > 0, "the created campus id must come back");

        JsonNode tree = tree(token);
        JsonNode hospital = rootWithName(tree.path("data"), "未命名医院");
        assertNotNull(hospital, "the single hospital root must be in the tree");
        JsonNode campus = nodeWithId(hospital.path("children"), campusId);
        assertNotNull(campus, "the created campus must nest under the hospital");
        assertEquals(1, campus.path("type").asInt(-1), "campus type is server-derived");
        assertEquals(2, campus.path("level").asInt(-1), "campus level is server-derived");
        assertEquals(campusCode, campus.path("code").asText());

        JsonNode update = parse(exchangeJson(HttpMethod.PUT, ORGS_URL + "/" + campusId,
                "{\"name\":\"" + updatedName + "\",\"shortName\":\"东院新\",\"status\":0}", token));
        assertEquals("200", update.path("code").asText(),
                "the campus profile must update: " + update);

        JsonNode changedType = parse(exchangeJson(HttpMethod.PUT, ORGS_URL + "/" + campusId,
                "{\"type\":0}", token));
        assertEquals("A0400", changedType.path("code").asText(),
                "organization type must be immutable: " + changedType);
        JsonNode reparent = parse(exchangeJson(HttpMethod.PUT, ORGS_URL + "/" + campusId,
                "{\"upId\":0}", token));
        assertEquals("A0400", reparent.path("code").asText(),
                "reparenting must be rejected: " + reparent);

        softDeleteOrg(campusId);
    }

    @Test
    void organizationDeleteIsGuardedByLiveDependents() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int campusId = createCampus("占用院区_" + ts);

        JsonNode rootDelete = parse(exchangeJson(HttpMethod.DELETE, ORGS_URL + "/1", null, token));
        assertEquals("A0400", rootDelete.path("code").asText(),
                "a hospital with live campuses cannot be deleted: " + rootDelete);

        String code = "dep_guard_" + ts;
        int depId = createDepartment(campusId, code, "占用科室_" + ts);
        JsonNode campusDelete = parse(exchangeJson(HttpMethod.DELETE, ORGS_URL + "/" + campusId,
                null, token));
        assertEquals("A0400", campusDelete.path("code").asText(),
                "a campus with live departments cannot be deleted: " + campusDelete);

        jdbcTemplate.update("UPDATE system.org_dep SET deleted = 1 WHERE id = ?", depId);
        softDeleteOrg(campusId);
    }

    private int createCampus(String name) throws Exception {
        String token = adminToken();
        ResponseEntity<String> response = exchangeJson(HttpMethod.POST, ORGS_URL,
                "{\"upId\":1,\"name\":\"" + name + "\"}", token);
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(), "campus fixture create must succeed: " + body);
        return body.path("data").asInt(-1);
    }

    private int createDepartment(int campusId, String code, String name) throws Exception {
        String token = adminToken();
        ResponseEntity<String> response = exchangeJson(HttpMethod.POST, "/api/v1/system/org-deps",
                "{\"orgId\":" + campusId + ",\"code\":\"" + code + "\",\"name\":\"" + name + "\"}", token);
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(), "department fixture create must succeed: " + body);
        return body.path("data").asInt(-1);
    }

    private JsonNode tree(String token) throws Exception {
        ResponseEntity<String> response = exchangeJson(HttpMethod.GET, ORGS_URL + "/tree", null, token);
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(), "the org tree query must succeed: " + body);
        return body;
    }

    private void softDeleteOrg(int id) {
        jdbcTemplate.update("UPDATE system.org_info SET deleted = 1 WHERE id = ?", id);
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
        headers.set("Authorization", "Bearer " + token);
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

    private static JsonNode nodeWithId(JsonNode nodes, int id) {
        if (!nodes.isArray()) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (node.path("id").asInt(-1) == id) {
                return node;
            }
        }
        return null;
    }
}
