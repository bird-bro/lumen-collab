package org.birdbro.lumen.system;

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
 * role-menu assignment (full-replace semantics), and the {@code /api/v1/system/menus/tree}
 * endpoint - driven through the real servlet context with the admin token.
 *
 * <p>The REQUIRED scenario (tasks.md 4.4): after an administrator creates a role, binds
 * it to a NEW user at creation time ({@code CreateUserRequest.roleIds} - the recorded
 * user-role creation path), and assigns the menus, that user logs in and {@code
 * /auth/me} carries the configured perms string - the {@code system:user:list} of
 * 用户管理 (menu 2). The assignment payload is the 用户权限 group (menu 14) PLUS its
 * page (menu 2): the copied {@code TreeUtils} assembly drops orphans, so a page
 * granted without its parent group yields perms but an empty menu tree - the test
 * asserts BOTH the perms string and the nested page node. (V10 parked the department
 * placeholder menus 3-6, so the former radiology payload moved to the console
 * subtree.) A second test proves the FULL-REPLACE semantics (re-assignment drops the
 * previous grants), and a third proves role rename, the duplicate-name rejection,
 * and that the logical role delete cascades to its {@code user_role}/{@code role_menu}
 * bindings. Shared long-lived database: unique timestamp-suffixed names,
 * logical-delete cleanup.</p>
 *
 * @author birdbro
 */
class AuthRoleMenuTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/system/login";
    private static final String USERS_URL = "/api/v1/system/users";
    private static final String ROLES_URL = "/api/v1/system/roles";
    private static final String MENUS_URL = "/api/v1/system/menus";

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
        JsonNode userPermGroup = menuWithMid(systemDirectory.path("children"), 14);
        assertNotNull(userPermGroup, "the 用户权限 group (menu 14) nests under the system directory (V5 IA)");
        assertNotNull(menuWithMid(userPermGroup.path("children"), 2),
                "user management (menu 2) nests under the 用户权限 group");
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

        // 3. Assign the menus to the role: the FULL ancestor chain 系统管理 (1) >
        //    用户权限 (14) > 用户管理 (2, perms system:user:list). TreeUtils drops
        //    orphans, so a grant missing an ancestor hides the whole subtree from the
        //    user's tree - a real grant carries every level down to the page.
        ResponseEntity<String> assignResponse = exchangeJson(HttpMethod.PUT,
                ROLES_URL + "/" + roleId + "/menus", "{\"menuIds\":[1,14,2]}", adminToken);
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

        // 5. /auth/me: the perms set CONTAINS the configured page string; the menus
        //    contain the nested 用户管理 page.
        ResponseEntity<String> meResponse = exchangeJson(HttpMethod.GET, "/api/v1/system/me", null, userToken);
        assertEquals(200, meResponse.getStatusCodeValue());
        JsonNode meBody = parse(meResponse);
        assertEquals("200", meBody.path("code").asText(), "the new user's /me must succeed: " + meBody);

        JsonNode perms = meBody.path("data").path("perms");
        assertTrue(perms.isArray(), "the perms set must be present");
        List<String> permStrings = new java.util.ArrayList<>();
        perms.forEach(perm -> permStrings.add(perm.asText()));
        assertTrue(permStrings.contains("system:user:list"),
                "the assigned page's perms string must reach /auth/me (tasks.md 4.4 verify): "
                        + permStrings);

        JsonNode menus = meBody.path("data").path("menus");
        JsonNode userPermGroup = menuWithMid(menus, 14);
        assertNotNull(userPermGroup, "the granted group must appear in the user's menu tree");
        assertEquals("用户权限", userPermGroup.path("title").asText());
        JsonNode userManagement = menuWithMid(userPermGroup.path("children"), 2);
        assertNotNull(userManagement, "用户管理 nests under the granted group");
        assertEquals("用户管理", userManagement.path("title").asText());

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

        // First grant: the 用户管理 page (2) alone - perms flow, the page is an orphan
        // in the tree walk (parent group 14 not granted) but the PERMS set still carries it.
        exchangeJson(HttpMethod.PUT, ROLES_URL + "/" + roleId + "/menus",
                "{\"menuIds\":[2]}", adminToken);
        JsonNode firstLogin = parse(postJson(LOGIN_URL,
                "{\"username\":\"" + username + "\",\"password\":\"audit-pass-123\"}"));
        assertEquals("200", firstLogin.path("code").asText());
        String firstToken = firstLogin.path("data").path("token").asText("");
        JsonNode firstMe = parse(exchangeJson(HttpMethod.GET, "/api/v1/system/me", null, firstToken));
        List<String> firstPerms = new java.util.ArrayList<>();
        firstMe.path("data").path("perms").forEach(perm -> firstPerms.add(perm.asText()));
        assertTrue(firstPerms.contains("system:user:list"),
                "the page string must flow after the first grant: " + firstPerms);

        // Full-replace re-assignment: ONLY menu 7 (机构管理, a different perms string) -
        // the previous grant is dropped, so system:user:list must vanish
        // (delete-then-insert semantics).
        ResponseEntity<String> reassignResponse = exchangeJson(HttpMethod.PUT,
                ROLES_URL + "/" + roleId + "/menus", "{\"menuIds\":[7]}", adminToken);
        assertEquals("200", parse(reassignResponse).path("code").asText(),
                "the re-assignment must succeed");

        JsonNode secondLogin = parse(postJson(LOGIN_URL,
                "{\"username\":\"" + username + "\",\"password\":\"audit-pass-123\"}"));
        assertEquals("200", secondLogin.path("code").asText());
        String secondToken = secondLogin.path("data").path("token").asText("");
        JsonNode secondMe = parse(exchangeJson(HttpMethod.GET, "/api/v1/system/me", null, secondToken));
        List<String> secondPerms = new java.util.ArrayList<>();
        secondMe.path("data").path("perms").forEach(perm -> secondPerms.add(perm.asText()));
        assertFalse(secondPerms.contains("system:user:list"),
                "full-replace semantics: re-assignment must DROP the previous grant - the "
                        + "user string must no longer flow: " + secondPerms);
        assertTrue(secondPerms.contains("system:org:list"),
                "the replacement grant's string must flow instead: " + secondPerms);

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
                "{\"menuIds\":[2]}", adminToken);

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
        JsonNode userMe = parse(exchangeJson(HttpMethod.GET, "/api/v1/system/me", null,
                userLogin.path("data").path("token").asText("")));
        List<String> userPerms = new java.util.ArrayList<>();
        userMe.path("data").path("perms").forEach(perm -> userPerms.add(perm.asText()));
        assertFalse(userPerms.contains("system:user:list"),
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
