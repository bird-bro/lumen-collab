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
 * Role list contract proof (role-list-api tasks 1.1/1.2): {@code
 * GET /api/v1/system/roles/list} - the api spec's former gap endpoint - returns the
 * standard envelope wrapping an IPage-shaped payload whose records carry the role
 * fields PLUS the {@code menuIds} grant echo (the role's live {@code role_menu} ids),
 * in ascending id order, with optional {@code name} (substring) and {@code type}
 * (equality) filters (the 随访 {@code RoleInfoServiceImpl.getPage} semantics).
 *
 * <p>Driven through the real servlet context with the run-admin token (the class-level
 * {@code system:role:list} page gate admits it). Shared long-lived database:
 * timestamp-suffixed unique names (prefixes registered in the base's
 * {@code ROLE_NAME_PREFIXES} sweep list), HARD-delete cleanup (user ruling
 * 2026-11-07).</p>
 *
 * @author birdbro
 */
class AuthRoleListTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/system/login";
    private static final String ROLES_URL = "/api/v1/system/roles";
    private static final String ROLES_LIST_URL = "/api/v1/system/roles/list";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void roleListReturnsPagedRowsWithGrantEcho() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int roleA = createRole(token, "回显角色A_" + ts);
        int roleB = createRole(token, "回显角色B_" + ts);

        // Grant role A menus 7 (机构管理) and 2 (用户管理) - posted out of order on
        // purpose: the echo must come back ascending regardless of insert order.
        // Role B stays ungranted (its echo must be an empty array, not null/absent).
        assertEquals("200", parse(exchangeJson(HttpMethod.PUT,
                ROLES_URL + "/" + roleA + "/menus", "{\"menuIds\":[7,2]}", token))
                .path("code").asText(), "the grant must succeed");

        ResponseEntity<String> response = exchangeJson(HttpMethod.GET,
                ROLES_LIST_URL + "?pageNum=1&pageSize=20", null, token);
        assertEquals(200, response.getStatusCodeValue());
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(),
                "the role list must answer the success envelope: " + body);

        JsonNode data = body.path("data");
        assertEquals(20, data.path("size").asInt(), "the requested pageSize echoes back");
        assertEquals(1, data.path("current").asInt(), "the requested pageNum echoes back");
        assertTrue(data.path("total").asLong() >= 2,
                "the total covers at least the two fixture roles: " + data.path("total"));
        JsonNode records = data.path("records");
        assertTrue(records.isArray(), "data.records must be the IPage record array");

        JsonNode rowA = rowWithId(records, roleA);
        JsonNode rowB = rowWithId(records, roleB);
        assertNotNull(rowA, "role A must be on page 1");
        assertNotNull(rowB, "role B must be on page 1");

        // Grant echo: A carries [2, 7] ascending; B carries an empty array.
        JsonNode menuIdsA = rowA.path("menuIds");
        assertTrue(menuIdsA.isArray(), "the grant echo must be a JSON array");
        assertEquals(2, menuIdsA.size(), "role A's live binding count: " + menuIdsA);
        assertEquals(2, menuIdsA.get(0).asInt(), "the echo is ascending by menu id");
        assertEquals(7, menuIdsA.get(1).asInt(), "the echo is ascending by menu id");
        JsonNode menuIdsB = rowB.path("menuIds");
        assertTrue(menuIdsB.isArray(), "an ungranted role's echo must still be an array");
        assertEquals(0, menuIdsB.size(), "an ungranted role echoes an empty grant");

        // Role fields ride the row (the frontend's RoleItem contract).
        assertEquals("回显角色A_" + ts, rowA.path("name").asText());
        assertEquals(11, rowA.path("type").asInt(), "the created business type echoes back");
        assertTrue(!rowA.path("createTime").asText("").isEmpty(), "audit fields ride the row");

        // Ascending id order: A was created before B, so A must precede B in the page.
        assertTrue(indexOfId(records, roleA) < indexOfId(records, roleB),
                "rows must be ascending by role id");

        cleanupRoles(roleA, roleB);
    }

    @Test
    void roleListFiltersByNameAndType() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int role = createRole(token, "名单过滤_" + ts);

        // Name filter (substring): only roles whose name contains the needle come back,
        // and the fixture role is among them.
        JsonNode named = parse(exchangeJson(HttpMethod.GET,
                ROLES_LIST_URL + "?name=" + ts, null, token));
        assertEquals("200", named.path("code").asText(), "the name-filtered list must succeed");
        JsonNode namedRecords = named.path("data").path("records");
        assertTrue(namedRecords.isArray() && namedRecords.size() >= 1,
                "the name needle must match the fixture role: " + namedRecords);
        for (JsonNode row : namedRecords) {
            assertTrue(row.path("name").asText().contains(String.valueOf(ts)),
                    "every name-filtered row must contain the needle: " + row);
        }
        assertNotNull(rowWithId(namedRecords, role), "the fixture role matches its own needle");

        // Type filter (equality): business roles only.
        JsonNode typed = parse(exchangeJson(HttpMethod.GET,
                ROLES_LIST_URL + "?type=11", null, token));
        assertEquals("200", typed.path("code").asText(), "the type-filtered list must succeed");
        JsonNode typedRecords = typed.path("data").path("records");
        assertTrue(typedRecords.isArray() && typedRecords.size() >= 1,
                "type 11 must match the fixture role");
        for (JsonNode row : typedRecords) {
            assertEquals(11, row.path("type").asInt(),
                    "every type-filtered row must equal the requested type: " + row);
        }
        assertNotNull(rowWithId(typedRecords, role), "the fixture role is type 11");

        // Combined filters narrow to the intersection.
        JsonNode both = parse(exchangeJson(HttpMethod.GET,
                ROLES_LIST_URL + "?name=" + ts + "&type=11", null, token));
        assertEquals("200", both.path("code").asText(), "the combined list must succeed");
        assertNotNull(rowWithId(both.path("data").path("records"), role),
                "the fixture role satisfies both filters");

        // No params at all: the copied PageSizeConfig defaults (1/20) apply.
        JsonNode defaults = parse(exchangeJson(HttpMethod.GET, ROLES_LIST_URL, null, token));
        assertEquals("200", defaults.path("code").asText(),
                "the bare list must succeed on the pagination defaults");
        assertEquals(1, defaults.path("data").path("current").asInt());
        assertEquals(20, defaults.path("data").path("size").asInt());

        cleanupRoles(role);
    }

    // ===== helpers (the sibling-suite house style) =====

    private String adminToken() throws Exception {
        ResponseEntity<String> loginResponse = postJson(LOGIN_URL, runAdminLoginBody());
        JsonNode loginBody = parse(loginResponse);
        assertEquals("200", loginBody.path("code").asText(), "admin login must hand out the token");
        String token = loginBody.path("data").path("token").asText("");
        assertTrue(!token.isEmpty(), "the admin token must be present");
        return token;
    }

    private int createRole(String token, String roleName) throws Exception {
        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, ROLES_URL,
                "{\"name\":\"" + roleName + "\",\"type\":11,\"remark\":\"role-list-api test role\"}", token);
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(),
                "the role create must succeed: " + createBody);
        int roleId = createBody.path("data").asInt(-1);
        assertTrue(roleId > 1, "the created role id must come back (past the seeded id 1)");
        return roleId;
    }

    /** HARD-delete cleanup of everything the flow created (user ruling 2026-11-07). */
    private void cleanupRoles(int... roleIds) {
        for (int roleId : roleIds) {
            jdbcTemplate.update("DELETE FROM system.role_menu WHERE role_id = ?", roleId);
            jdbcTemplate.update("DELETE FROM system.role_info WHERE id = ?", roleId);
        }
    }

    private static JsonNode rowWithId(JsonNode records, int id) {
        for (JsonNode row : records) {
            if (row.path("id").asInt(-1) == id) {
                return row;
            }
        }
        return null;
    }

    private static int indexOfId(JsonNode records, int id) {
        for (int i = 0; i < records.size(); i++) {
            if (records.get(i).path("id").asInt(-1) == id) {
                return i;
            }
        }
        return -1;
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
