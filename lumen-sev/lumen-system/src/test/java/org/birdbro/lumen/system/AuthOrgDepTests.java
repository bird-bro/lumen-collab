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
 * Campus-scoped department management proof (org-management-redesign-api): ownership,
 * campus code uniqueness, immutable codes, and the paginated table query are driven through
 * the real API. Timestamped fixtures and logical deletes keep the shared database clean.
 */
class AuthOrgDepTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/system/login";
    private static final String ORG_DEPS_URL = "/api/v1/system/org-deps";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seededDepartmentsListUnderDefaultCampusAndHospitalOwnerIsRejected() throws Exception {
        String token = adminToken();
        int campusId = defaultCampusId();

        ResponseEntity<String> listResponse = exchangeJson(HttpMethod.GET,
                ORG_DEPS_URL + "/list?orgId=" + campusId, null, token);
        JsonNode listBody = parse(listResponse);
        assertEquals("200", listBody.path("code").asText(), "the list query must succeed");
        assertTrue(listBody.path("data").isArray(), "the list data must be an array");
        assertNotNull(departmentWithCode(listBody.path("data"), "radiology"),
                "the seeded radiology department must remain under the default campus");
        assertNotNull(departmentWithCode(listBody.path("data"), "us"),
                "the seeded ultrasound department must remain under the default campus");
        assertNotNull(departmentWithCode(listBody.path("data"), "endo"),
                "the seeded endoscopy department must remain under the default campus");

        ResponseEntity<String> hospitalOwnerResponse = exchangeJson(HttpMethod.POST, ORG_DEPS_URL,
                "{\"orgId\":1,\"code\":\"dep_hospital_" + System.currentTimeMillis()
                        + "\",\"name\":\"医院直属科室\"}", token);
        JsonNode hospitalOwnerBody = parse(hospitalOwnerResponse);
        assertEquals("A0400", hospitalOwnerBody.path("code").asText(),
                "the hospital root cannot own a department: " + hospitalOwnerBody);
    }

    @Test
    void sameCodeAcrossCampusesPersistsButDuplicateWithinCampusIsRejected() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int campusA = createCampus("科室编码A院区_" + ts);
        int campusB = createCampus("科室编码B院区_" + ts);
        String code = "dep_shared_" + ts;

        int depA = createDepartment(campusA, code, "共用编码A");
        int depB = createDepartment(campusB, code, "共用编码B");

        JsonNode duplicate = parse(exchangeJson(HttpMethod.POST, ORG_DEPS_URL,
                "{\"orgId\":" + campusA + ",\"code\":\"" + code + "\",\"name\":\"重复编码\"}", token));
        assertEquals("A0712", duplicate.path("code").asText(),
                "the same code must be rejected inside one campus: " + duplicate);

        softDeleteDepartment(depA);
        softDeleteDepartment(depB);
        softDeleteOrg(campusA);
        softDeleteOrg(campusB);
    }

    @Test
    void departmentCodeIsImmutableAndCampusReassignmentChecksTargetCampus() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int campusA = createCampus("编码不可变A院区_" + ts);
        int campusB = createCampus("编码不可变B院区_" + ts);
        String code = "dep_immutable_" + ts;
        int depId = createDepartment(campusA, code, "编码不可变科室");

        JsonNode rename = parse(exchangeJson(HttpMethod.PUT, ORG_DEPS_URL + "/" + depId,
                "{\"code\":\"" + code + "\",\"name\":\"编码不可变改名\"}", token));
        assertEquals("200", rename.path("code").asText(),
                "sending the unchanged code must remain compatible: " + rename);

        JsonNode changedCode = parse(exchangeJson(HttpMethod.PUT, ORG_DEPS_URL + "/" + depId,
                "{\"code\":\"changed_" + ts + "\"}", token));
        assertEquals("A0400", changedCode.path("code").asText(),
                "changing a department code must be rejected: " + changedCode);

        JsonNode reassigned = parse(exchangeJson(HttpMethod.PUT, ORG_DEPS_URL + "/" + depId,
                "{\"orgId\":" + campusB + "}", token));
        assertEquals("200", reassigned.path("code").asText(),
                "reassignment to another live campus must succeed: " + reassigned);
        String storedCode = jdbcTemplate.queryForObject(
                "SELECT code FROM system.org_dep WHERE id = ?", String.class, depId);
        assertEquals(code, storedCode, "the code must remain unchanged");

        softDeleteDepartment(depId);
        softDeleteOrg(campusA);
        softDeleteOrg(campusB);
    }

    @Test
    void departmentPageSupportsCampusKeywordStatusAndPagination() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int campusA = createCampus("分页A院区_" + ts);
        int campusB = createCampus("分页B院区_" + ts);
        String code = "dep_page_" + ts;
        int depA = createDepartment(campusA, code, "分页测试A");
        int depB = createDepartment(campusB, code, "分页测试B");

        ResponseEntity<String> allResponse = exchangeJson(HttpMethod.GET,
                ORG_DEPS_URL + "/page?pageNum=1&pageSize=100&keyword=" + code, null, token);
        JsonNode allBody = parse(allResponse);
        assertEquals("200", allBody.path("code").asText(), "the all-campus page must succeed");
        assertEquals(2, allBody.path("data").path("total").asLong(-1),
                "both campuses using the code must match: " + allBody);

        ResponseEntity<String> campusResponse = exchangeJson(HttpMethod.GET,
                ORG_DEPS_URL + "/page?pageNum=1&pageSize=20&orgId=" + campusA + "&keyword=" + code,
                null, token);
        JsonNode campusBody = parse(campusResponse);
        assertEquals("200", campusBody.path("code").asText(), "the campus page must succeed");
        assertEquals(1, campusBody.path("data").path("total").asLong(-1),
                "campus filtering must isolate its row: " + campusBody);
        assertEquals(depA, campusBody.path("data").path("records").get(0).path("id").asInt(-1),
                "the campus page must return campus A's department");

        JsonNode disabled = parse(exchangeJson(HttpMethod.PUT, ORG_DEPS_URL + "/" + depB,
                "{\"status\":0}", token));
        assertEquals("200", disabled.path("code").asText(), "department status must update");

        ResponseEntity<String> enabledResponse = exchangeJson(HttpMethod.GET,
                ORG_DEPS_URL + "/page?pageNum=1&pageSize=100&keyword=" + code + "&status=1", null, token);
        JsonNode enabledBody = parse(enabledResponse);
        assertEquals(1, enabledBody.path("data").path("total").asLong(-1),
                "the enabled filter must exclude the disabled campus B row");

        ResponseEntity<String> disabledResponse = exchangeJson(HttpMethod.GET,
                ORG_DEPS_URL + "/page?pageNum=1&pageSize=100&keyword=" + code + "&status=0", null, token);
        JsonNode disabledBody = parse(disabledResponse);
        assertEquals(1, disabledBody.path("data").path("total").asLong(-1),
                "the disabled filter must include only campus B's row");

        softDeleteDepartment(depA);
        softDeleteDepartment(depB);
        softDeleteOrg(campusA);
        softDeleteOrg(campusB);
    }

    private int defaultCampusId() {
        Integer id = jdbcTemplate.queryForObject(
                "SELECT id FROM system.org_info WHERE up_id = 1 AND deleted = 0 "
                + "AND is_default = 1 ORDER BY id LIMIT 1", Integer.class);
        assertNotNull(id, "the seeded default campus must exist");
        return id;
    }

    private int createCampus(String name) throws Exception {
        String token = adminToken();
        ResponseEntity<String> response = exchangeJson(HttpMethod.POST, "/api/v1/system/orgs",
                "{\"upId\":1,\"name\":\"" + name + "\"}", token);
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(), "campus fixture create must succeed: " + body);
        return body.path("data").asInt(-1);
    }

    private int createDepartment(int campusId, String code, String name) throws Exception {
        String token = adminToken();
        ResponseEntity<String> response = exchangeJson(HttpMethod.POST, ORG_DEPS_URL,
                "{\"orgId\":" + campusId + ",\"code\":\"" + code + "\",\"name\":\"" + name + "\"}", token);
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(), "department create must succeed: " + body);
        return body.path("data").asInt(-1);
    }

    private void softDeleteDepartment(int id) {
        jdbcTemplate.update("UPDATE system.org_dep SET deleted = 1 WHERE id = ?", id);
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
