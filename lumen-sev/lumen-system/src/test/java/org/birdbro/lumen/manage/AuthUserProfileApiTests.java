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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * User profile CRUD proof (user-edit-delete-profile-api tasks 2.1-3.3; spec deltas api
 * "User Management Endpoints" + auth-rbac "User Management"): the list rows carry the
 * profile payload with batch-resolved roleNames/depName, the detail endpoint echoes the
 * full profile (never the hash), the partial update replaces role bindings, and the
 * delete is the protected logical delete (self + seed-admin rejected, user_role
 * unbound, username re-creatable).
 *
 * <p>Shared long-lived database: timestamp-suffixed fixtures, hard-delete cleanup
 * (user ruling 2026-11-07), the base sweep as the safety net.</p>
 *
 * @author birdbro
 */
class AuthUserProfileApiTests extends AuthIntegrationTestBase {

    private static final String USERS_URL = "/api/v1/system/users";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ===================== task 2.1: list rows carry the payload =====================

    @Test
    void listRowCarriesProfileRoleNamesAndDepName() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String username = "profile_u_" + ts;
        int campusId = createCampus("档案院区_" + ts);
        int depId = createDepartment(campusId, "dep_profile_" + ts, "档案科室");

        // Create with roles bound (admin role 1 is always live on the shared DB).
        int uid = createUser(token, username, "档案用户");
        bindRole(uid, 1);

        // Profile values ride a JDBC patch (the update endpoint's own proof is task
        // 3.2's tests) - this test pins the READ shape only.
        jdbcTemplate.update("UPDATE system.user_info SET phone = ?, email = ?, gender = ?, "
                        + "title = ?, dep_id = ?, modalitys = ?, rep_audit = ?, signature = ? "
                        + "WHERE id = ?",
                "13800001111", "doc@example.com", 100401, 100503, depId, "CT,MR", 2,
                "/profile/avatar/x.png", uid);

        try {
            JsonNode item = listItem(token, username);
            assertNotNull(item, "the created user must be listed");
            assertEquals("13800001111", item.path("phone").asText(), "the row carries phone");
            assertEquals("doc@example.com", item.path("email").asText(), "the row carries email");
            assertEquals(100401, item.path("gender").asInt(-1), "the row carries the gender dict code");
            assertEquals(100503, item.path("title").asInt(-1), "the row carries the title dict code");
            assertEquals(depId, item.path("depId").asInt(-1), "the row carries depId");
            assertEquals("档案科室", item.path("depName").asText(), "depName resolves batch-wise");
            assertEquals("CT,MR", item.path("modalitys").asText(), "the row carries the modalitys CSV");
            assertEquals(2, item.path("repAudit").asInt(-1), "the row carries the repAudit tier");
            assertEquals("/profile/avatar/x.png", item.path("signature").asText(),
                    "the row carries the signature path");
            assertTrue(item.path("roleNames").isArray() && item.path("roleNames").size() >= 1,
                    "roleNames carries the bound role names");
            Integer createBy = jdbcTemplate.queryForObject(
                    "SELECT create_by FROM system.user_info WHERE id = ?", Integer.class, uid);
            assertEquals(createBy, item.path("createBy").asInt(-1),
                    "the row carries the audit creator UID");
            assertFalse(item.has("password"), "the hash never serializes");
        } finally {
            cleanupUser(uid);
            cleanupDep(depId, campusId);
        }
    }

    @Test
    void listRowWithoutBindingsRendersEmptyNotError() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String username = "profile_bare_u_" + ts;
        int uid = createUser(token, username, "裸用户");
        try {
            JsonNode item = listItem(token, username);
            assertNotNull(item, "the bare user must be listed");
            assertTrue(item.path("roleNames").isArray() && item.path("roleNames").size() == 0,
                    "no roles -> an EMPTY roleNames array, never an error");
            assertTrue(item.path("depName").isNull() || item.path("depName").asText("").isEmpty(),
                    "no department -> an absent depName, never an error");
            assertTrue(item.path("phone").isNull() || item.path("phone").asText("").isEmpty(),
                    "no phone -> absent, never an error");
        } finally {
            cleanupUser(uid);
        }
    }

    @Test
    void createCarriesProfileFieldsInOneCall() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String username = "profile_full_u_" + ts;
        int campusId = createCampus("全档案院区_" + ts);
        int depId = createDepartment(campusId, "dep_full_" + ts, "全档案科室");

        try {
            String body = "{\"username\":\"" + username + "\",\"password\":\""
                    + encryptTestPassword("plain-pass-123") + "\",\"name\":\"全档案用户\","
                    + "\"phone\":\"13700004444\",\"email\":\"full@example.com\","
                    + "\"gender\":100401,\"title\":100501,\"depId\":" + depId + ","
                    + "\"modalitys\":\"CT,US\",\"repAudit\":1,"
                    + "\"signature\":\"/profile/avatar/z.png\",\"roleIds\":[1]}";
            ResponseEntity<String> response = exchangeJson(HttpMethod.POST, USERS_URL, body, token);
            JsonNode parsed = parse(response);
            assertEquals("200", parsed.path("code").asText(), "the full create must succeed: " + parsed);
            int uid = parsed.path("data").asInt(-1);

            var row = jdbcTemplate.queryForMap(
                    "SELECT phone, email, gender, title, dep_id, modalitys, rep_audit, signature "
                            + "FROM system.user_info WHERE id = ?", uid);
            assertEquals("13700004444", row.get("phone"));
            assertEquals("full@example.com", row.get("email"));
            assertEquals(100401, ((Number) row.get("gender")).intValue());
            assertEquals(depId, ((Number) row.get("dep_id")).intValue());
            assertEquals("CT,US", row.get("modalitys"));
            assertEquals(1, ((Number) row.get("rep_audit")).intValue());
            assertEquals("/profile/avatar/z.png", row.get("signature"));

            cleanupUser(uid);
        } finally {
            cleanupDep(depId, campusId);
        }
    }

    @Test
    void createWithDeadDepIdRejectsA0400AndCreatesNothing() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String username = "profile_baddep_u_" + ts;
        String body = "{\"username\":\"" + username + "\",\"password\":\""
                + encryptTestPassword("plain-pass-123") + "\",\"name\":\"坏科室用户\","
                + "\"depId\":2147483000}";
        ResponseEntity<String> response = exchangeJson(HttpMethod.POST, USERS_URL, body, token);
        JsonNode parsed = parse(response);
        assertEquals("A0400", parsed.path("code").asText(), "a dead depId rejects A0400: " + parsed);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.user_info WHERE username = ?", Integer.class, username);
        assertEquals(Integer.valueOf(0), count, "NO row is created");
    }

    // ===================== task 2.2: the detail endpoint =====================

    @Test
    void detailEchoesFullProfileWithoutPassword() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String username = "detail_u_" + ts;
        int uid = createUser(token, username, "详情用户");
        bindRole(uid, 1);
        try {
            ResponseEntity<String> response = exchangeJson(HttpMethod.GET,
                    USERS_URL + "/" + uid, null, token);
            JsonNode body = parse(response);
            assertEquals("200", body.path("code").asText(), "the detail must succeed: " + body);
            JsonNode data = body.path("data");
            assertEquals(username, data.path("username").asText(), "the detail echoes username");
            assertEquals("详情用户", data.path("name").asText());
            assertTrue(data.path("roleIds").isArray() && data.path("roleIds").size() == 1
                            && data.path("roleIds").get(0).asInt() == 1,
                    "the detail echoes roleIds (the edit dialog needs IDS, not labels)");
            assertFalse(data.has("password"), "the detail NEVER carries the hash");
        } finally {
            cleanupUser(uid);
        }
    }

    @Test
    void detailOfDeadIdAnswersB0031() throws Exception {
        String token = adminToken();
        ResponseEntity<String> response = exchangeJson(HttpMethod.GET,
                USERS_URL + "/2147483000", null, token);
        JsonNode body = parse(response);
        assertTrue(body.path("code").asText().startsWith("B"),
                "a dead id answers the B-class not-found code: " + body);
        assertNull(body.path("data").asText(null), "data is null on the miss");
    }

    // ===================== task 3.2: the profile update =====================

    @Test
    void updateChangesProfileAndReplacesRoleBindings() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String username = "update_u_" + ts;
        int campusId = createCampus("更新院区_" + ts);
        int depId = createDepartment(campusId, "dep_upd_" + ts, "更新科室");
        int uid = createUser(token, username, "更新用户");
        bindRole(uid, 1);
        try {
            String body = "{\"name\":\"更新后用户\",\"phone\":\"13900002222\","
                    + "\"email\":\"upd@example.com\",\"gender\":100402,\"title\":100501,"
                    + "\"depId\":" + depId + ",\"modalitys\":\"US\","
                    + "\"repAudit\":3,\"signature\":\"/profile/avatar/y.png\"}";
            ResponseEntity<String> response = exchangeJson(HttpMethod.PUT,
                    USERS_URL + "/" + uid, body, token);
            JsonNode parsed = parse(response);
            assertEquals("200", parsed.path("code").asText(), "the update must succeed: " + parsed);

            // Role full-replace: roleIds [1] removed by ABSENCE is not enough to prove
            // replace - assert the SET replacement with an empty array (bindings gone).
            int bindings = countBindings(uid);
            assertEquals(1, bindings, "absent roleIds leaves the bindings untouched (partial semantics)");

            var row = jdbcTemplate.queryForMap(
                    "SELECT name, phone, email, gender, title, dep_id, modalitys, rep_audit, "
                            + "signature, username FROM system.user_info WHERE id = ?", uid);
            assertEquals("更新后用户", row.get("name"));
            assertEquals("13900002222", row.get("phone"));
            assertEquals(100402, ((Number) row.get("gender")).intValue());
            assertEquals(depId, ((Number) row.get("dep_id")).intValue());
            assertEquals("US", row.get("modalitys"));
            assertEquals(3, ((Number) row.get("rep_audit")).intValue());
            assertEquals(username, row.get("username"), "the username never moves");
        } finally {
            cleanupUser(uid);
            cleanupDep(depId, campusId);
        }
    }

    @Test
    void updateWithRoleIdsFullReplacesBindings() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String username = "rebind_u_" + ts;
        int uid = createUser(token, username, "换绑用户");
        bindRole(uid, 1);
        try {
            // role 1 bound at create; the update carries roleIds [] -> full replace
            // clears every binding (the role-menu assignment precedent).
            ResponseEntity<String> response = exchangeJson(HttpMethod.PUT,
                    USERS_URL + "/" + uid, "{\"roleIds\":[]}", token);
            JsonNode parsed = parse(response);
            assertEquals("200", parsed.path("code").asText(), "the replace must succeed: " + parsed);
            assertEquals(0, countBindings(uid), "roleIds [] clears the bindings (full-replace)");

            // And a positive replace rebinds to exactly the posted set.
            ResponseEntity<String> response2 = exchangeJson(HttpMethod.PUT,
                    USERS_URL + "/" + uid, "{\"roleIds\":[1]}", token);
            assertEquals("200", parse(response2).path("code").asText());
            assertEquals(1, countBindings(uid), "roleIds [1] rebinds exactly role 1");
        } finally {
            cleanupUser(uid);
        }
    }

    @Test
    void updateWithBadRoleOrDepIdRejectsA0400AndChangesNothing() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String username = "badid_u_" + ts;
        int uid = createUser(token, username, "坏引用用户");
        bindRole(uid, 1);
        try {
            ResponseEntity<String> response = exchangeJson(HttpMethod.PUT,
                    USERS_URL + "/" + uid, "{\"roleIds\":[2147483000]}", token);
            JsonNode body = parse(response);
            assertEquals("A0400", body.path("code").asText(),
                    "a dead role id rejects A0400: " + body);
            assertEquals(1, countBindings(uid), "the rejection changes NO binding");

            ResponseEntity<String> depResponse = exchangeJson(HttpMethod.PUT,
                    USERS_URL + "/" + uid, "{\"depId\":2147483000}", token);
            JsonNode depBody = parse(depResponse);
            assertEquals("A0400", depBody.path("code").asText(),
                    "a dead department id rejects A0400: " + depBody);
            var row = jdbcTemplate.queryForMap("SELECT name FROM system.user_info WHERE id = ?", uid);
            assertEquals("坏引用用户", row.get("name"), "the rejection changes NO profile column");
        } finally {
            cleanupUser(uid);
        }
    }

    @Test
    void updateWithDifferentUsernameDoesNotOverwrite() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String username = "immutable_u_" + ts;
        int uid = createUser(token, username, "不可变用户");
        try {
            ResponseEntity<String> response = exchangeJson(HttpMethod.PUT,
                    USERS_URL + "/" + uid, "{\"username\":\"hacked_" + ts + "\",\"name\":\"改名\"}",
                    token);
            JsonNode body = parse(response);
            assertEquals("200", body.path("code").asText(), "the update succeeds: " + body);
            var row = jdbcTemplate.queryForMap("SELECT username, name FROM system.user_info WHERE id = ?", uid);
            assertEquals(username, row.get("username"), "the posted username is IGNORED, not applied");
            assertEquals("改名", row.get("name"), "the mutable fields still apply");
        } finally {
            cleanupUser(uid);
        }
    }

    // ===================== task 3.3: the protected logical delete =====================

    @Test
    void deleteRemovesFromListAndUnbindsRolesAndFreesUsername() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String username = "delete_u_" + ts;
        int uid = createUser(token, username, "待删用户");
        bindRole(uid, 1);

        ResponseEntity<String> response = exchangeJson(HttpMethod.DELETE,
                USERS_URL + "/" + uid, null, token);
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(), "the delete must succeed: " + body);
        assertTrue(body.path("data").asBoolean(false), "data is true on the delete");

        // gone from the list
        JsonNode item = listItem(token, username);
        assertNull(item, "the deleted user is absent from the list");
        // the row persists, logically deleted
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM system.user_info WHERE id = ?", Integer.class, uid);
        assertEquals(1, deleted.intValue(), "the row persists with deleted = 1 (the EntityBase contract)");
        // the bindings are unbound (logically)
        Integer liveBindings = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.user_role WHERE user_id = ? AND deleted = 0",
                Integer.class, uid);
        assertEquals(0, liveBindings.intValue(), "the user_role bindings are unbound");
        // the username is re-creatable (the partial unique index covers live rows)
        int recreated = createUser(token, username, "复用用户");
        assertTrue(recreated > 0, "the freed username accepts a new row");
        cleanupUser(recreated);
    }

    @Test
    void selfDeleteRejected() throws Exception {
        String token = adminToken();
        int selfId = runUserId().intValue();
        ResponseEntity<String> response = exchangeJson(HttpMethod.DELETE,
                USERS_URL + "/" + selfId, null, token);
        JsonNode body = parse(response);
        assertTrue(body.path("code").asText().startsWith("A"),
                "deleting yourself rejects A-class: " + body);
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM system.user_info WHERE id = ?", Integer.class, selfId);
        assertEquals(0, deleted.intValue(), "nothing changed");
    }

    @Test
    void seedAdminDeleteRejected() throws Exception {
        String token = adminToken();
        ResponseEntity<String> response = exchangeJson(HttpMethod.DELETE,
                USERS_URL + "/1", null, token);
        JsonNode body = parse(response);
        assertTrue(body.path("code").asText().startsWith("A"),
                "deleting the seed admin rejects A-class: " + body);
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM system.user_info WHERE id = 1", Integer.class);
        assertEquals(0, deleted.intValue(), "nothing changed");
    }

    // ===================== helpers =====================

    private int createUser(String token, String username, String name) throws Exception {
        ResponseEntity<String> response = exchangeJson(HttpMethod.POST, USERS_URL,
                "{\"username\":\"" + username + "\",\"password\":\""
                        + encryptTestPassword("plain-pass-123") + "\",\"name\":\"" + name + "\"}",
                token);
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(), "fixture create must succeed: " + body);
        return body.path("data").asInt(-1);
    }

    private void bindRole(int uid, int roleId) {
        jdbcTemplate.update(
                "INSERT INTO system.user_role (user_id, role_id) VALUES (?, ?) "
                        + "ON CONFLICT DO NOTHING", uid, roleId);
    }

    private int countBindings(int uid) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.user_role WHERE user_id = ? AND deleted = 0",
                Integer.class, uid);
        return count == null ? 0 : count;
    }

    private JsonNode listItem(String token, String username) throws Exception {
        ResponseEntity<String> response = exchangeJson(HttpMethod.GET, USERS_URL + "/list/profile",
                null, token);
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(), "the list must succeed: " + body);
        return userWithUsername(body.path("data"), username);
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
        ResponseEntity<String> response = exchangeJson(HttpMethod.POST, "/api/v1/system/org-deps",
                "{\"orgId\":" + campusId + ",\"code\":\"" + code + "\",\"name\":\"" + name + "\"}", token);
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(), "department create must succeed: " + body);
        return body.path("data").asInt(-1);
    }

    /** HARD-delete cleanup (user ruling 2026-11-07), bindings before the user row. */
    private void cleanupUser(int id) {
        jdbcTemplate.update("DELETE FROM system.user_role WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM system.user_info WHERE id = ?", id);
    }

    private void cleanupDep(int depId, int campusId) {
        jdbcTemplate.update("DELETE FROM system.org_dep WHERE id = ?", depId);
        jdbcTemplate.update("DELETE FROM system.org_info WHERE id = ?", campusId);
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

    private String adminToken() throws Exception {
        ResponseEntity<String> response = postJson("/api/v1/system/login", runAdminLoginBody());
        JsonNode body = objectMapper.readTree(response.getBody());
        return body.path("data").path("token").asText();
    }

    private ResponseEntity<String> postJson(String url, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
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
