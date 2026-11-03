package org.birdbro.lumen.auth;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Role and menu management proof (auth-rbac-foundation task 4.4): role CRUD, the
 * role-menu assignment (full-replace semantics), and the {@code /api/v1/auth/menus/tree}
 * endpoint - driven through the real servlet context with the admin token.
 *
 * <p>The REQUIRED scenario (tasks.md 4.4): after an administrator creates a role, binds
 * it to a NEW user at creation time ({@code CreateUserRequest.roleIds} - the recorded
 * user-role creation path), and assigns the menus, that user logs in and {@code
 * /auth/me} carries the configured button string in the perms set - the seeded
 * {@code radiology:report:audit} audit button. The assignment payload is the radiology
 * page (menu 3) PLUS its audit button (menu 6): the copied {@code TreeUtils} assembly
 * drops orphans, so a button granted without its parent page yields perms but an empty
 * menu tree - the test asserts BOTH the perms string and the 放射科 page node. A second
 * test proves the FULL-REPLACE semantics (re-assignment drops the previous grants), and
 * a third proves role rename, the duplicate-name rejection, and that the logical role
 * delete cascades to its {@code user_role}/{@code role_menu} bindings. Shared long-lived
 * database: unique timestamp-suffixed names, logical-delete cleanup.</p>
 *
 * @author birdbro
 */
class AuthRoleMenuTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String USERS_URL = "/api/v1/auth/users";
    private static final String ROLES_URL = "/api/v1/auth/roles";
    private static final String MENUS_URL = "/api/v1/auth/menus";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void menuTreeEndpointReturnsSeededTree() throws Exception {
        String token = adminToken();

        ResponseEntity<String> treeResponse = exchangeJson(HttpMethod.GET, MENUS_URL + "/tree", null, token);
        assertEquals(200, treeResponse.getStatusCodeValue());
        JsonNode treeBody = parse(treeResponse);
        assertEquals("200", treeBody.path("code").asText(),
                "the menu tree query must succeed: " + treeBody);

        JsonNode systemDirectory = menuWithMid(treeBody.path("data"), 1);
        assertNotNull(systemDirectory, "menu 1 系统管理 must be a tree root");
        assertEquals("系统管理", systemDirectory.path("title").asText());
        assertNotNull(menuWithMid(systemDirectory.path("children"), 2),
                "user management (menu 2) nests under the system directory");
        JsonNode radiologyPage = menuWithMid(treeBody.path("data"), 3);
        assertNotNull(radiologyPage, "menu 3 放射科 must be a tree root");
        assertNotNull(menuWithMid(radiologyPage.path("children"), 6),
                "the audit button (menu 6) nests under the radiology page");
    }

    @Test
    void roleMenuAssignmentFlowsToUserPermsOnMe() throws Exception {
        String adminToken = adminToken();
        long ts = System.currentTimeMillis();
        String roleName = "报告审核员_" + ts; // unique - shared DB
        String username = "audit_u_" + ts; // unique - shared DB

        // 1. Create the role.
        int roleId = createRole(adminToken, roleName);

        // 2. Create the user WITH that role (the recorded creation path: roleIds).
        int userId = createUserWithRoles(adminToken, username, roleName, roleId);

        // 3. Assign the menus to the role: the radiology page (3) + its audit button (6,
        //    perms radiology:report:audit). The button alone would be an orphan in the
        //    copied tree assembly - a real grant carries the page and its buttons.
        ResponseEntity<String> assignResponse = exchangeJson(HttpMethod.PUT,
                ROLES_URL + "/" + roleId + "/menus", "{\"menuIds\":[3,6]}", adminToken);
        JsonNode assignBody = parse(assignResponse);
        assertEquals("200", assignBody.path("code").asText(),
                "the role-menu assignment must succeed: " + assignBody);

        // 4. Log in AS THAT USER.
        JsonNode loginBody = parse(postJson(LOGIN_URL,
                "{\"username\":\"" + username + "\",\"password\":\"audit-pass-123\"}"));
        assertEquals("200", loginBody.path("code").asText(),
                "the new user must be able to log in: " + loginBody);
        String userToken = loginBody.path("data").path("token").asText("");
        assertTrue(!userToken.isEmpty(), "the new user's token must come back");

        // 5. /auth/me: the perms set CONTAINS the configured button string; the menus
        //    contain the 放射科 page.
        ResponseEntity<String> meResponse = exchangeJson(HttpMethod.GET, "/api/v1/auth/me", null, userToken);
        assertEquals(200, meResponse.getStatusCodeValue());
        JsonNode meBody = parse(meResponse);
        assertEquals("200", meBody.path("code").asText(), "the new user's /me must succeed: " + meBody);

        JsonNode perms = meBody.path("data").path("perms");
        assertTrue(perms.isArray(), "the perms set must be present");
        List<String> permStrings = new java.util.ArrayList<>();
        perms.forEach(perm -> permStrings.add(perm.asText()));
        assertTrue(permStrings.contains("radiology:report:audit"),
                "the assigned button's perms string must reach /auth/me (tasks.md 4.4 verify): "
                        + permStrings);

        JsonNode menus = meBody.path("data").path("menus");
        JsonNode radiologyPage = menuWithMid(menus, 3);
        assertNotNull(radiologyPage, "the granted page must appear in the user's menu tree");
        assertEquals("放射科", radiologyPage.path("title").asText());
        assertNotNull(menuWithMid(radiologyPage.path("children"), 6),
                "the audit button nests under the granted page");

        cleanupRoleAndUser(roleId, userId);
    }

    @Test
    void menuAssignmentIsFullReplace() throws Exception {
        String adminToken = adminToken();
        long ts = System.currentTimeMillis();
        String roleName = "全量替换角色_" + ts; // unique - shared DB
        String username = "replace_u_" + ts; // unique - shared DB

        int roleId = createRole(adminToken, roleName);
        int userId = createUserWithRoles(adminToken, username, roleName, roleId);

        // First grant: the audit button (6) alone - perms flow, the button is an orphan
        // in the tree walk (parent page not granted) but the PERMS set still carries it.
        exchangeJson(HttpMethod.PUT, ROLES_URL + "/" + roleId + "/menus",
                "{\"menuIds\":[6]}", adminToken);
        JsonNode firstLogin = parse(postJson(LOGIN_URL,
                "{\"username\":\"" + username + "\",\"password\":\"audit-pass-123\"}"));
        assertEquals("200", firstLogin.path("code").asText());
        String firstToken = firstLogin.path("data").path("token").asText("");
        JsonNode firstMe = parse(exchangeJson(HttpMethod.GET, "/api/v1/auth/me", null, firstToken));
        List<String> firstPerms = new java.util.ArrayList<>();
        firstMe.path("data").path("perms").forEach(perm -> firstPerms.add(perm.asText()));
        assertTrue(firstPerms.contains("radiology:report:audit"),
                "the audit string must flow after the first grant: " + firstPerms);

        // Full-replace re-assignment: ONLY menu 3 (no perms) - the previous grant is
        // dropped, so the audit string must vanish (delete-then-insert semantics).
        ResponseEntity<String> reassignResponse = exchangeJson(HttpMethod.PUT,
                ROLES_URL + "/" + roleId + "/menus", "{\"menuIds\":[3]}", adminToken);
        assertEquals("200", parse(reassignResponse).path("code").asText(),
                "the re-assignment must succeed");

        JsonNode secondLogin = parse(postJson(LOGIN_URL,
                "{\"username\":\"" + username + "\",\"password\":\"audit-pass-123\"}"));
        assertEquals("200", secondLogin.path("code").asText());
        String secondToken = secondLogin.path("data").path("token").asText("");
        JsonNode secondMe = parse(exchangeJson(HttpMethod.GET, "/api/v1/auth/me", null, secondToken));
        List<String> secondPerms = new java.util.ArrayList<>();
        secondMe.path("data").path("perms").forEach(perm -> secondPerms.add(perm.asText()));
        assertFalse(secondPerms.contains("radiology:report:audit"),
                "full-replace semantics: re-assignment must DROP the previous grant - the "
                        + "audit string must no longer flow: " + secondPerms);

        cleanupRoleAndUser(roleId, userId);
    }

    @Test
    void roleCrudDuplicateNameRejectedAndLogicalDeleteCascadesBindings() throws Exception {
        String adminToken = adminToken();
        long ts = System.currentTimeMillis();
        String roleName = "临时角色_" + ts; // unique - shared DB
        String renamedName = "临时角色改名_" + ts;
        String username = "role_del_u_" + ts; // unique - shared DB

        // Duplicate live role name (the seeded 系统管理员) is rejected.
        ResponseEntity<String> duplicateResponse = exchangeJson(HttpMethod.POST, ROLES_URL,
                "{\"name\":\"系统管理员\",\"type\":11}", adminToken);
        JsonNode duplicateBody = parse(duplicateResponse);
        assertEquals("A0714", duplicateBody.path("code").asText(),
                "a duplicate live role name must be rejected (the partial unique index stays "
                        + "the backstop): " + duplicateBody);

        // Create + rename.
        int roleId = createRole(adminToken, roleName);
        ResponseEntity<String> renameResponse = exchangeJson(HttpMethod.PUT,
                ROLES_URL + "/" + roleId, "{\"name\":\"" + renamedName + "\"}", adminToken);
        assertEquals("200", parse(renameResponse).path("code").asText(),
                "the role rename must succeed");

        // Bind a user and menus, then logically delete the role.
        int userId = createUserWithRoles(adminToken, username, renamedName, roleId);
        exchangeJson(HttpMethod.PUT, ROLES_URL + "/" + roleId + "/menus",
                "{\"menuIds\":[6]}", adminToken);

        ResponseEntity<String> deleteResponse = exchangeJson(HttpMethod.DELETE,
                ROLES_URL + "/" + roleId, null, adminToken);
        assertEquals("200", parse(deleteResponse).path("code").asText(),
                "the logical role delete must succeed");

        // The DB verdict: the role row is dead AND its bindings are dead (the recorded
        // cascade decision - the perms walk does not join role, so live bindings to a
        // dead role would keep granting).
        Integer roleDeleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM system.role_info WHERE id = ?", Integer.class, roleId);
        assertEquals(Integer.valueOf(1), roleDeleted, "the role row must be logically deleted");
        Integer liveUserRoleRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.user_role WHERE role_id = ? AND deleted = 0",
                Integer.class, roleId);
        assertEquals(Integer.valueOf(0), liveUserRoleRows,
                "the role's user_role bindings must be logically deleted with it");
        Integer totalUserRoleRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.user_role WHERE role_id = ?", Integer.class, roleId);
        assertTrue(totalUserRoleRows != null && totalUserRoleRows > 0,
                "the bindings must have existed before the cascade (evidence sanity)");
        Integer liveRoleMenuRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu WHERE role_id = ? AND deleted = 0",
                Integer.class, roleId);
        assertEquals(Integer.valueOf(0), liveRoleMenuRows,
                "the role's role_menu bindings must be logically deleted with it");

        // And the bound user no longer receives the granted perms.
        JsonNode userLogin = parse(postJson(LOGIN_URL,
                "{\"username\":\"" + username + "\",\"password\":\"audit-pass-123\"}"));
        assertEquals("200", userLogin.path("code").asText(),
                "the user itself still logs in (the account is untouched)");
        JsonNode userMe = parse(exchangeJson(HttpMethod.GET, "/api/v1/auth/me", null,
                userLogin.path("data").path("token").asText("")));
        List<String> userPerms = new java.util.ArrayList<>();
        userMe.path("data").path("perms").forEach(perm -> userPerms.add(perm.asText()));
        assertFalse(userPerms.contains("radiology:report:audit"),
                "a deleted role must not keep granting perms through dead bindings: " + userPerms);

        cleanupUser(userId);
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

    private int createRole(String token, String roleName) throws Exception {
        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, ROLES_URL,
                "{\"name\":\"" + roleName + "\",\"type\":11,\"remark\":\"batch-4 test role\"}", token);
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

    /** Logical-delete cleanup of everything the flow created (shared DB hygiene). */
    private void cleanupRoleAndUser(int roleId, int userId) {
        jdbcTemplate.update("UPDATE system.role_menu SET deleted = 1 WHERE role_id = ?", roleId);
        jdbcTemplate.update("UPDATE system.user_role SET deleted = 1 WHERE role_id = ?", roleId);
        jdbcTemplate.update("UPDATE system.role_info SET deleted = 1 WHERE id = ?", roleId);
        cleanupUser(userId);
    }

    private void cleanupUser(int userId) {
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
        headers.set("Authorization", "Bearer " + token); // Constants.TOKEN_PREFIX
        return restTemplate.exchange(url, method, new HttpEntity<>(json, headers), String.class);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private static JsonNode menuWithMid(JsonNode nodes, int mid) {
        if (!nodes.isArray()) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (node.path("mid").asInt(-1) == mid) {
                return node;
            }
            JsonNode found = menuWithMid(node.path("children"), mid);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
