package org.birdbro.lumen.system;

import java.util.List;
import java.util.Set;

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

import org.birdbro.common.core.pojo.user.UserLogin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server-side permission enforcement matrix (server-side-perm-enforcement tasks 1.1
 * and 2.1, spec deltas auth-rbac + errors): page-level method security over the seven
 * management families, city staying authenticated-only, the {@code A0301} deny
 * envelope over the HTTP-200 transport, and the `"203"` entry-point precedence.
 *
 * <p>TDD matrix — RED-first: while {@code UserLogin.getAuthorities()} returns null and
 * no method security exists, every DENY assertion below fails (the endpoint answers
 * 200); the pass-through assertions (admin, city, tokenless-203) are regression guards
 * that pass before AND after. The stale-session scenario is covered at the POJO level
 * (a null permission set must yield empty authorities, never null) plus the
 * perm-less-user integration case (the observable equivalent).</p>
 *
 * <p>Shared long-lived database: unique timestamp-suffixed names, logical-delete
 * cleanup.</p>
 *
 * @author birdbro
 */
class AuthPermEnforcementTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/system/login";
    private static final String USERS_URL = "/api/v1/system/users";
    private static final String ROLES_URL = "/api/v1/system/roles";
    private static final String MENUS_TREE_URL = "/api/v1/system/menus/tree";
    private static final String MENUS_URL = "/api/v1/system/menus";
    private static final String ORGS_TREE_URL = "/api/v1/system/orgs/tree";
    private static final String DEPS_LIST_URL = "/api/v1/system/org-deps/list";
    private static final String DETAIL_LIST_URL = "/api/v1/system/dicts/detail/list?caId=1000";
    private static final String LOGS_URL = "/api/v1/system/logs?pageNum=1&pageSize=10";
    private static final String CITY_TREE_URL = "/api/v1/system/dicts/city/tree";
    private static final String ORGS_URL = "/api/v1/system/orgs";
    private static final String DEPS_URL = "/api/v1/system/org-deps";
    private static final String CATAL_URL = "/api/v1/system/dicts/catal";
    private static final String DETAIL_URL = "/api/v1/system/dicts/detail";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** POJO level: authorities derive from the permission set, empty-safe, never null. */
    @Test
    void authoritiesDeriveFromPermissionSetAndAreNeverNull() {
        UserLogin empty = new UserLogin();
        empty.setPermissions(null);
        assertNotNull(empty.getAuthorities(),
                "null permissions must map to EMPTY authorities, never null (V-fix)");
        assertTrue(empty.getAuthorities().isEmpty(), "a null set yields no authorities");

        UserLogin granted = new UserLogin();
        granted.setPermissions(Set.of("system:user:list"));
        assertEquals(1, granted.getAuthorities().size(),
                "each permission string becomes one granted authority");
        assertEquals("system:user:list",
                granted.getAuthorities().iterator().next().getAuthority());
    }

    /** Core deny: a role with NO menus => empty permission set => A0301 on every family. */
    @Test
    void permLessUserIsDeniedOnEveryFamily() throws Exception {
        String adminToken = adminToken();
        long ts = System.currentTimeMillis();
        String roleName = "无权角色_" + ts;
        String username = "noperm_u_" + ts;

        int roleId = createRole(adminToken, roleName);
        int userId = createUserWithRoles(adminToken, username, roleName, roleId);

        String userToken = loginToken(username);
        List<String> probeUrls = List.of(
                USERS_URL + "/list?pageNum=1&pageSize=10",
                MENUS_TREE_URL,
                ORGS_TREE_URL,
                DEPS_LIST_URL,
                DETAIL_LIST_URL,
                LOGS_URL);
        for (String url : probeUrls) {
            JsonNode body = parse(exchangeJson(HttpMethod.GET, url, null, userToken));
            assertEquals("A0301", body.path("code").asText(),
                    "a perm-less authenticated call must be denied on " + url + ": " + body);
        }
        // The roles family is write-only - probe it through a valid create body: the
        // denial must fire BEFORE the controller (the role must not be created).
        JsonNode rolesBody = parse(exchangeJson(HttpMethod.POST, ROLES_URL,
                "{\"name\":\"denied_role_" + ts + "\",\"type\":11}", userToken));
        assertEquals("A0301", rolesBody.path("code").asText(),
                "a perm-less role create must be denied: " + rolesBody);
        Integer deniedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_info WHERE name = ?", Integer.class,
                "denied_role_" + ts);
        assertEquals(Integer.valueOf(0), deniedCount,
                "the denied create must not reach the controller (no role row)");

        cleanupRoleAndUser(roleId, userId);
    }

    /** Pass-through: the administrator (owns every menu) reads every read-family. */
    @Test
    void adminPassesReadFamiliesAndCity() throws Exception {
        String token = adminToken();
        List<String> probeUrls = List.of(
                USERS_URL + "/list?pageNum=1&pageSize=10",
                MENUS_TREE_URL,
                ORGS_TREE_URL,
                DEPS_LIST_URL,
                DETAIL_LIST_URL,
                LOGS_URL,
                CITY_TREE_URL);
        for (String url : probeUrls) {
            JsonNode body = parse(exchangeJson(HttpMethod.GET, url, null, token));
            assertEquals("200", body.path("code").asText(),
                    "the admin pass-through must hold on " + url + ": " + body);
        }
    }

    /** Cross-family: a page perm grants exactly its own family - nothing leaks sideways. */
    @Test
    void crossFamilyPermDoesNotLeak() throws Exception {
        String adminToken = adminToken();
        long ts = System.currentTimeMillis();
        String roleName = "单页角色_" + ts;
        String username = "single_u_" + ts;

        int roleId = createRole(adminToken, roleName);
        // 系统管理(1) > 用户权限(14) > 用户管理(2): the full ancestor chain, whose only
        // perms string is system:user:list.
        exchangeJson(HttpMethod.PUT, ROLES_URL + "/" + roleId + "/menus",
                "{\"menuIds\":[1,14,2]}", adminToken);
        int userId = createUserWithRoles(adminToken, username, roleName, roleId);

        String userToken = loginToken(username);
        assertEquals("200", parse(exchangeJson(HttpMethod.GET,
                USERS_URL + "/list?pageNum=1&pageSize=10", null, userToken)).path("code").asText(),
                "the granted family must pass");
        assertEquals("A0301", parse(exchangeJson(HttpMethod.GET,
                MENUS_TREE_URL, null, userToken)).path("code").asText(),
                "a sibling family must deny: system:user:list must not read menus");
        assertEquals("A0301", parse(exchangeJson(HttpMethod.GET,
                LOGS_URL, null, userToken)).path("code").asText(),
                "a sibling family must deny: system:user:list must not read logs");

        cleanupRoleAndUser(roleId, userId);
    }

    /** City reference data stays authenticated-only - any valid token reads it. */
    @Test
    void cityStaysAuthenticatedOnly() throws Exception {
        String adminToken = adminToken();
        long ts = System.currentTimeMillis();
        String roleName = "无权城市角色_" + ts;
        String username = "city_u_" + ts;

        int roleId = createRole(adminToken, roleName);
        int userId = createUserWithRoles(adminToken, username, roleName, roleId);

        String userToken = loginToken(username);
        JsonNode body = parse(exchangeJson(HttpMethod.GET, CITY_TREE_URL, null, userToken));
        assertEquals("200", body.path("code").asText(),
                "city reference queries stay authenticated-only (no page perm): " + body);

        cleanupRoleAndUser(roleId, userId);
    }

    /**
     * Menu write family (menu-management-api task 3.2): the three lifecycle endpoints
     * sit under the SAME class-level {@code system:menu:list} gate as the tree read -
     * a perm-less authenticated user is denied on POST/PUT/DELETE, and the denial fires
     * BEFORE the controller (no node row appears). The probe target is a throwaway
     * admin-created node so an accidental gate pass cannot damage the seeds.
     */
    @Test
    void menuWriteFamilyIsPageGated() throws Exception {
        String adminToken = adminToken();
        long ts = System.currentTimeMillis();
        String roleName = "无权菜单角色_" + ts;
        String username = "menuperm_u_" + ts;

        // throwaway probe node (admin-created, cleaned up at the end)
        ResponseEntity<String> fixtureResponse = exchangeJson(HttpMethod.POST, MENUS_URL,
                "{\"upId\":9,\"type\":\"F\",\"title\":\"ut门禁探针_" + ts
                        + "\",\"perms\":\"ut.gate:probe:" + ts + "\",\"sort\":9,\"visible\":1}",
                adminToken);
        int probeId = parse(fixtureResponse).path("data").asInt(-1);
        assertTrue(probeId > 0, "the admin fixture create must succeed");

        int roleId = createRole(adminToken, roleName);
        int userId = createUserWithRoles(adminToken, username, roleName, roleId);
        String userToken = loginToken(username);

        JsonNode createBody = parse(exchangeJson(HttpMethod.POST, MENUS_URL,
                "{\"upId\":9,\"type\":\"F\",\"title\":\"ut门禁按钮_" + ts
                        + "\",\"perms\":\"ut.gate:deny:" + ts + "\",\"sort\":1,\"visible\":1}",
                userToken));
        assertEquals("A0301", createBody.path("code").asText(),
                "a perm-less menu create must be denied by the class-level gate: " + createBody);
        Integer created = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE title = ?", Integer.class,
                "ut门禁按钮_" + ts);
        assertEquals(Integer.valueOf(0), created,
                "the denied create must not reach the controller (no node row)");

        JsonNode updateBody = parse(exchangeJson(HttpMethod.PUT, MENUS_URL + "/" + probeId,
                "{\"upId\":9,\"type\":\"F\",\"title\":\"ut门禁探针改_"
                        + ts + "\",\"sort\":1,\"visible\":1}", userToken));
        assertEquals("A0301", updateBody.path("code").asText(),
                "a perm-less menu update must be denied by the class-level gate: " + updateBody);

        JsonNode deleteBody = parse(exchangeJson(HttpMethod.DELETE, MENUS_URL + "/" + probeId,
                null, userToken));
        assertEquals("A0301", deleteBody.path("code").asText(),
                "a perm-less menu delete must be denied by the class-level gate: " + deleteBody);
        Integer stillLive = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE id = ? AND deleted = 0",
                Integer.class, probeId);
        assertEquals(Integer.valueOf(1), stillLive,
                "the denied delete must leave the probe node live");

        jdbcTemplate.update("DELETE FROM system.role_menu WHERE menu_id = ?", probeId);
        jdbcTemplate.update("DELETE FROM system.menu_info WHERE id = ?", probeId);
        cleanupRoleAndUser(roleId, userId);
    }

    /** Entry-point precedence: a tokenless request still answers "203", never "A0301". */
    @Test
    void tokenlessStillAnswers203() throws Exception {
        JsonNode body = parse(exchangeJson(HttpMethod.GET,
                USERS_URL + "/list?pageNum=1&pageSize=10", null, null));
        assertEquals("203", body.path("code").asText(),
                "tokenless must stay the entry point's 203: " + body);
    }

    /**
     * Button-level enforcement matrix (button-perm-enforcement-api task 2.1, spec delta
     * auth-rbac ADDED "Button-Level Permission Enforcement"): EVERY console write
     * endpoint denies a role holding only the PAGE perm (A0301, nothing written) and
     * passes a role holding only the BUTTON perm - whose list read stays class-gate
     * denied (D2: the button node is the ACTION authority, the page node the VISIBILITY
     * authority). One fixture role + user are reused; the grant is FULL-REPLACEd per
     * phase and the fixture re-logs in (the session perm set solidifies at login).
     * The perms/endpoint inventory mirrors the coordination table
     * (openspec/coordination/button-perm-enforcement.md) - a missing pin or a drifted
     * string fails here, not in production.
     */
    @Test
    void buttonPermsGateEveryConsoleWriteEndpoint() throws Exception {
        String adminToken = adminToken();
        long ts = System.currentTimeMillis();

        // throwaway targets the button-only PASS side consumes (DELETE) or mutates (PUT)
        int thrUserId = createUserWithRoles(adminToken, "btnmatrix_t_" + ts, "矩阵目标用户", 1);
        int thrPutRoleId = createRole(adminToken, "按钮矩阵PUT目标_" + ts);
        int thrDelRoleId = createRole(adminToken, "按钮矩阵DEL目标_" + ts);
        int thrAssignRoleId = createRole(adminToken, "按钮矩阵ASSIGN目标_" + ts);
        int thrNodeId = adminCreateMenu(adminToken, 9, "ut矩阵节点_" + ts, null);
        int thrDelNodeId = adminCreateMenu(adminToken, 9, "ut矩阵删节点_" + ts, null);
        int thrOrgId = adminCreateOrg(adminToken, ts);
        int thrDelOrgId = adminCreateOrg(adminToken, ts);
        int matrixCampusId = adminCreateOrg(adminToken, ts); // departments need a live CAMPUS owner (V16)
        int thrDepId = adminCreateDep(adminToken, matrixCampusId);
        int thrDelDepId = adminCreateDep(adminToken, matrixCampusId);
        int thrCatalId = adminCreateCatal(adminToken, ts);
        int thrDelCatalId = adminCreateCatal(adminToken, ts);
        int thrDetailId = adminCreateDetail(adminToken, thrCatalId, ts);
        int thrDelDetailId = adminCreateDetail(adminToken, thrDelCatalId, ts);

        int roleId = createRole(adminToken, "按钮矩阵角色_" + ts);
        String username = "btnmatrix_u_" + ts;
        createUserWithRoles(adminToken, username, "按钮矩阵用户", roleId);

        // users family
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 14, 2}, "system:user:create",
                "POST /users",
                token -> exchangeJson(HttpMethod.POST, USERS_URL,
                        "{\"username\":\"btnmatrix_w_" + ts + "\",\"password\":\"audit-pass-123\","
                                + "\"name\":\"矩阵直建用户\",\"roleIds\":[]}", token));
        // D2 semantics (spec scenario "Button perm alone authorizes the write"): the
        // role currently holds ONLY the bare create-button node - its list read stays
        // class-gate denied.
        assertEquals("A0301", parse(exchangeJson(HttpMethod.GET,
                USERS_URL + "/list?pageNum=1&pageSize=10", null, loginToken(username)))
                .path("code").asText(),
                "a button-only role's list read must stay class-gate denied");
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.user_info WHERE username = ? AND deleted = 0",
                Integer.class, "btnmatrix_deny_" + ts).intValue(),
                "the denied create must not reach the controller");
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 14, 2}, "system:user:reset-pwd",
                "PUT /users/{id}/password",
                token -> exchangeJson(HttpMethod.PUT, USERS_URL + "/" + thrUserId + "/password",
                        "{\"newPassword\":\"NewPass1!23\"}", token));
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 14, 2}, "system:user:enable",
                "PUT /users/{id}/enable",
                token -> exchangeJson(HttpMethod.PUT, USERS_URL + "/" + thrUserId + "/enable",
                        "{\"enable\":0}", token));

        // roles family
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 14, 9}, "system:role:create",
                "POST /roles",
                token -> exchangeJson(HttpMethod.POST, ROLES_URL,
                        "{\"name\":\"按钮矩阵直建_" + ts + "\",\"type\":11}", token));
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 14, 9}, "system:role:update",
                "PUT /roles/{id}",
                token -> exchangeJson(HttpMethod.PUT, ROLES_URL + "/" + thrPutRoleId,
                        "{\"name\":\"按钮矩阵改名_" + ts + "\"}", token));
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 14, 9}, "system:role:delete",
                "DELETE /roles/{id}",
                token -> exchangeJson(HttpMethod.DELETE, ROLES_URL + "/" + thrDelRoleId, null, token));
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 14, 9}, "system:role:assign",
                "PUT /roles/{id}/menus",
                token -> exchangeJson(HttpMethod.PUT, ROLES_URL + "/" + thrAssignRoleId + "/menus",
                        "{\"menuIds\":[1]}", token));

        // menus family
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 15, 10}, "system:menu:create",
                "POST /menus",
                token -> exchangeJson(HttpMethod.POST, MENUS_URL,
                        "{\"upId\":9,\"type\":\"F\",\"title\":\"ut矩阵按钮_" + ts
                                + "\",\"perms\":\"ut.mtx:" + ts + "\",\"sort\":1,\"visible\":1}", token));
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 15, 10}, "system:menu:update",
                "PUT /menus/{id}",
                token -> exchangeJson(HttpMethod.PUT, MENUS_URL + "/" + thrNodeId,
                        "{\"upId\":9,\"type\":\"F\",\"title\":\"ut矩阵节点改_" + ts
                                + "\",\"perms\":\"ut.mtx:" + ts + "\",\"sort\":2,\"visible\":1}", token));
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 15, 10}, "system:menu:delete",
                "DELETE /menus/{id}",
                token -> exchangeJson(HttpMethod.DELETE, MENUS_URL + "/" + thrDelNodeId, null, token));

        // orgs family
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 13, 7}, "system:org:create",
                "POST /orgs",
                token -> exchangeJson(HttpMethod.POST, ORGS_URL,
                        "{\"upId\":1,\"name\":\"按钮矩阵院区_" + ts + "\"}", token));
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 13, 7}, "system:org:update",
                "PUT /orgs/{id}",
                token -> exchangeJson(HttpMethod.PUT, ORGS_URL + "/" + thrOrgId,
                        "{\"name\":\"按钮矩阵机构改_" + ts + "\"}", token));
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 13, 7}, "system:org:delete",
                "DELETE /orgs/{id}",
                token -> exchangeJson(HttpMethod.DELETE, ORGS_URL + "/" + thrDelOrgId, null, token));

        // deps family (matrixCampusId - a freshly created live campus - made at the top)
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 13, 8}, "system:dep:create",
                "POST /org-deps",
                token -> exchangeJson(HttpMethod.POST, DEPS_URL,
                        "{\"orgId\":" + matrixCampusId + ",\"code\":\"ut_mtx_dep_" + ts
                                + "\",\"name\":\"按钮矩阵科室_" + ts + "\"}", token));
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 13, 8}, "system:dep:update",
                "PUT /org-deps/{id}",
                token -> exchangeJson(HttpMethod.PUT, DEPS_URL + "/" + thrDepId,
                        "{\"name\":\"按钮矩阵科室改_" + ts + "\"}", token));
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 13, 8}, "system:dep:delete",
                "DELETE /org-deps/{id}",
                token -> exchangeJson(HttpMethod.DELETE, DEPS_URL + "/" + thrDelDepId, null, token));

        // dicts family (two resources)
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 15, 11}, "system:dict-catal:create",
                "POST /dicts/catal",
                token -> exchangeJson(HttpMethod.POST, CATAL_URL,
                        "{\"name\":\"按钮矩阵目录_" + ts + "\",\"sort\":9}", token));
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 15, 11}, "system:dict-catal:update",
                "PUT /dicts/catal/{id}",
                token -> exchangeJson(HttpMethod.PUT, CATAL_URL + "/" + thrCatalId,
                        "{\"name\":\"按钮矩阵目录改_" + ts + "\"}", token));
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 15, 11}, "system:dict-catal:delete",
                "DELETE /dicts/catal/{id}",
                token -> exchangeJson(HttpMethod.DELETE, CATAL_URL + "/" + thrDelCatalId, null, token));
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 15, 11}, "system:dict-detail:create",
                "POST /dicts/detail",
                token -> exchangeJson(HttpMethod.POST, DETAIL_URL,
                        "{\"caId\":" + thrCatalId + ",\"code\":9101,\"value\":\"按钮矩阵值_"
                                + ts + "\",\"sort\":9}", token));
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 15, 11}, "system:dict-detail:update",
                "PUT /dicts/detail/{id}",
                token -> exchangeJson(HttpMethod.PUT, DETAIL_URL + "/" + thrDetailId,
                        "{\"value\":\"按钮矩阵值改_" + ts + "\"}", token));
        expectDenyThenPass(adminToken, roleId, username, new int[]{1, 15, 11}, "system:dict-detail:delete",
                "DELETE /dicts/detail/{id}",
                token -> exchangeJson(HttpMethod.DELETE, DETAIL_URL + "/" + thrDelDetailId, null, token));

        // cleanup: everything the matrix created or touched - HARD deletes (user ruling
        // 2026-11-07: soft-deleted sweep rows pile up and poison bug triage), bindings
        // before owners so the V1 FKs never trip (user_role/user_info last among users;
        // role_menu before role_info and menu_info; org_dep before org_info).
        jdbcTemplate.update("DELETE FROM system.role_menu WHERE role_id IN (SELECT id FROM system.role_info WHERE name LIKE '按钮矩阵%')");
        jdbcTemplate.update("DELETE FROM system.role_menu WHERE menu_id IN (SELECT id FROM system.menu_info WHERE title LIKE 'ut矩阵%' OR title LIKE '按钮矩阵%')");
        jdbcTemplate.update("DELETE FROM system.user_role WHERE role_id IN (SELECT id FROM system.role_info WHERE name LIKE '按钮矩阵%')");
        jdbcTemplate.update("DELETE FROM system.user_role WHERE user_id IN (SELECT id FROM system.user_info WHERE username LIKE 'btnmatrix\\_%' ESCAPE '\\')");
        jdbcTemplate.update("DELETE FROM system.role_info WHERE name LIKE '按钮矩阵%'");
        jdbcTemplate.update("DELETE FROM system.menu_info WHERE title LIKE 'ut矩阵%' OR title LIKE '按钮矩阵%' OR perms LIKE 'ut.mtx%'");
        jdbcTemplate.update("DELETE FROM system.org_dep WHERE org_id IN (SELECT id FROM system.org_info WHERE name LIKE '按钮矩阵院区%')");
        jdbcTemplate.update("DELETE FROM system.org_dep WHERE name LIKE '按钮矩阵%' OR code LIKE 'ut_mtx_dep_%'");
        jdbcTemplate.update("DELETE FROM system.org_info WHERE name LIKE '按钮矩阵%'");
        jdbcTemplate.update("DELETE FROM public.dict_detail WHERE ca_id IN (SELECT id FROM public.dict_catal WHERE name LIKE '按钮矩阵%')");
        jdbcTemplate.update("DELETE FROM public.dict_detail WHERE value LIKE '按钮矩阵%'");
        jdbcTemplate.update("DELETE FROM public.dict_catal WHERE name LIKE '按钮矩阵%'");
        jdbcTemplate.update("DELETE FROM system.user_info WHERE username LIKE 'btnmatrix\\_%' ESCAPE '\\'");
    }

    /** The deny-then-pass proof for ONE write endpoint (see the matrix javadoc). */
    private void expectDenyThenPass(String adminToken, int roleId, String username,
                                    int[] pageChain, String buttonPerms, String describe,
                                    ThrowingCall call) throws Exception {
        // 1) page-only grant: the write must DENY (A0301) and write nothing
        assignMenus(adminToken, roleId, pageChain);
        JsonNode denied = parse(call.apply(loginToken(username)));
        assertEquals("A0301", denied.path("code").asText(),
                "a page-only role must be DENIED on " + describe + ": " + denied);

        // 2) button-only grant (the bare button node - no ancestors): the write must
        //    PASS (the perms set flows through role_menu -> menu_info without any tree
        //    requirement), proving the button node is the action authority (D2).
        int buttonId = buttonId(buttonPerms);
        assignMenus(adminToken, roleId, new int[]{buttonId});
        JsonNode passed = parse(call.apply(loginToken(username)));
        assertEquals("200", passed.path("code").asText(),
                "a button-only role must PASS " + describe + ": " + passed);
    }

    /** The live menu id carrying the given perms string (the V15 seed's node). */
    private int buttonId(String perms) {
        Integer id = jdbcTemplate.queryForObject(
                "SELECT id FROM system.menu_info WHERE perms = ? AND type = 'F' AND deleted = 0",
                Integer.class, perms);
        assertNotNull(id, "the seeded button node must exist: " + perms);
        return id;
    }

    private void assignMenus(String adminToken, int roleId, int[] menuIds) throws Exception {
        StringBuilder json = new StringBuilder("{\"menuIds\":[");
        for (int i = 0; i < menuIds.length; i++) {
            if (i > 0) json.append(',');
            json.append(menuIds[i]);
        }
        json.append("]}");
        JsonNode body = parse(exchangeJson(HttpMethod.PUT, ROLES_URL + "/" + roleId + "/menus",
                json.toString(), adminToken));
        assertEquals("200", body.path("code").asText(), "the grant assignment must succeed: " + body);
    }

    private int adminCreateMenu(String token, int upId, String title, String perms) throws Exception {
        String permsPart = perms == null ? "" : ",\"perms\":\"" + perms + "\"";
        JsonNode body = parse(exchangeJson(HttpMethod.POST, MENUS_URL,
                "{\"upId\":" + upId + ",\"type\":\"F\",\"title\":\"" + title + "\"" + permsPart
                        + ",\"sort\":9,\"visible\":1}", token));
        assertEquals("200", body.path("code").asText(), "the fixture node create must succeed: " + body);
        return body.path("data").asInt(-1);
    }

    private int adminCreateOrg(String token, long ts) throws Exception {
        long seq = System.nanoTime(); // names must be unique among live rows
        // V16 model: upId=1 (the hospital root) creates a CAMPUS (code server-generated)
        JsonNode body = parse(exchangeJson(HttpMethod.POST, ORGS_URL,
                "{\"upId\":1,\"name\":\"按钮矩阵院区T_" + seq + "\"}", token));
        assertEquals("200", body.path("code").asText(), "the fixture org create must succeed: " + body);
        return body.path("data").asInt(-1);
    }

    private int adminCreateDep(String token, long ts) throws Exception {
        // unique code per campus: department codes repeat across campuses but not within one (A0712)
        long codeSeq = System.nanoTime();
        int campusId = jdbcTemplate.queryForObject(
                "SELECT id FROM system.org_info WHERE up_id = 1 AND deleted = 0 "
                        + "AND is_default = 1 ORDER BY id LIMIT 1", Integer.class);
        JsonNode body = parse(exchangeJson(HttpMethod.POST, DEPS_URL,
                "{\"orgId\":" + campusId + ",\"code\":\"ut_mtx_dep_" + codeSeq + "\",\"name\":\"按钮矩阵科室T_"
                        + codeSeq + "\"}", token));
        assertEquals("200", body.path("code").asText(), "the fixture dep create must succeed: " + body);
        return body.path("data").asInt(-1);
    }

    private int adminCreateCatal(String token, long ts) throws Exception {
        long seq = System.nanoTime(); // catal names are unique among live rows (A0714)
        JsonNode body = parse(exchangeJson(HttpMethod.POST, CATAL_URL,
                "{\"name\":\"按钮矩阵目录T_" + seq + "\",\"sort\":9}", token));
        assertEquals("200", body.path("code").asText(), "the fixture catal create must succeed: " + body);
        return body.path("data").asInt(-1);
    }

    private int adminCreateDetail(String token, int caId, long ts) throws Exception {
        JsonNode body = parse(exchangeJson(HttpMethod.POST, DETAIL_URL,
                "{\"caId\":" + caId + ",\"code\":9101,\"value\":\"按钮矩阵值T_" + ts + "\",\"sort\":9}", token));
        assertEquals("200", body.path("code").asText(), "the fixture detail create must succeed: " + body);
        return body.path("data").asInt(-1);
    }

    /** One HTTP call with the given token, throwing checked exceptions. */
    private interface ThrowingCall {
        ResponseEntity<String> apply(String token) throws Exception;
    }

    // ---------- helpers (house style, mirroring AuthRoleMenuTests) ----------

    private String adminToken() throws Exception {
        return loginToken(RUN_ACCOUNT);
    }

    private String loginToken(String username) throws Exception {
        ResponseEntity<String> loginResponse = postJson(LOGIN_URL,
                "{\"username\":\"" + username + "\",\"password\":\""
                        + (RUN_ACCOUNT.equals(username) ? ADMIN_TEST_PASSWORD : "audit-pass-123") + "\"}");
        JsonNode loginBody = parse(loginResponse);
        assertEquals("200", loginBody.path("code").asText(), "login must hand out the token");
        String token = loginBody.path("data").path("token").asText("");
        assertTrue(!token.isEmpty(), "the token must be present");
        return token;
    }

    private int createRole(String token, String roleName) throws Exception {
        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, ROLES_URL,
                "{\"name\":\"" + roleName + "\",\"type\":11}", token);
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

    /** HARD-delete cleanup (user ruling 2026-11-07), bindings before owners (V1 FKs). */
    private void cleanupRoleAndUser(int roleId, int userId) {
        jdbcTemplate.update("DELETE FROM system.role_menu WHERE role_id = ?", roleId);
        jdbcTemplate.update("DELETE FROM system.user_role WHERE role_id = ?", roleId);
        jdbcTemplate.update("DELETE FROM system.role_info WHERE id = ?", roleId);
        jdbcTemplate.update("DELETE FROM system.user_role WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM system.user_info WHERE id = ?", userId);
    }

    private ResponseEntity<String> postJson(String url, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    private ResponseEntity<String> exchangeJson(HttpMethod method, String url, String json, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set("Authorization", "Bearer " + token); // Constants.TOKEN_PREFIX
        }
        return restTemplate.exchange(url, method, new HttpEntity<>(json, headers), String.class);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }
}
