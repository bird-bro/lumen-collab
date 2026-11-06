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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Menu management CRUD proof (menu-management-api, spec deltas api + auth-rbac): the
 * {@code /api/v1/system/menus} create/update/delete family over the existing tree
 * endpoint, driven through the real servlet context with the admin token (TestRestTemplate
 * against the RANDOM_PORT server of {@link AuthIntegrationTestBase}).
 *
 * <p>Spec scenarios covered: button-node create round-trip with the ADMIN AUTO-GRANT
 * (create inserts the role_menu row for role 1 - lumen keeps explicit grants, no
 * wildcard), F-leaf / cycle / unknown-parent / live-children rejections
 * ({@code A0400} envelopes), UPDATE never touching grants, and delete cleaning the
 * node's {@code role_menu} bindings so a deleted button stops granting its perms.</p>
 *
 * <p>Shared long-lived database: timestamp-suffixed titles and perms strings, logical
 * delete cleanup. The seeded console tree gives stable C-type parents (menu 9 =
 * 角色管理, menu 15 = 系统配置).</p>
 *
 * @author birdbro
 */
class AuthMenuManageTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/system/login";
    private static final String MENUS_URL = "/api/v1/system/menus";
    private static final String MENUS_TREE_URL = "/api/v1/system/menus/tree";

    /** A seeded C-type console page used as the parent for created test nodes. */
    private static final int ROLE_MENU_PAGE_ID = 9;
    /** A seeded M-type group directory used as a legal move target. */
    private static final int CONFIG_GROUP_ID = 15;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createButtonNodeRoundTripsAndAutoGrantsAdmin() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String title = "ut菜单按钮_" + ts;
        String perms = "ut.menu:delete:" + ts;

        // Spec scenario: create a button node under a menu -> id back, tree shows it,
        // admin role's grant contains the node (auto-grant, explicit-grant model).
        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, MENUS_URL,
                "{\"upId\":" + ROLE_MENU_PAGE_ID + ",\"type\":\"F\",\"title\":\"" + title
                        + "\",\"perms\":\"" + perms + "\",\"sort\":9,\"visible\":1}", token);
        assertEquals(200, createResponse.getStatusCodeValue(),
                "menu create must answer over HTTP 200 (envelope transport)");
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(),
                "the button-node create must succeed: " + createBody);
        int createdId = createBody.path("data").asInt(-1);
        assertTrue(createdId > 0, "the created menu id must come back (IdType.AUTO backfill)");

        JsonNode created = findNode(menuTree(token), createdId);
        assertNotNull(created, "the created button node must render in the tree");
        assertEquals("F", created.path("type").asText(), "the node must keep its type");
        assertEquals(perms, created.path("perms").asText(), "the perms string must round-trip");
        assertEquals(title, created.path("title").asText(), "the title must round-trip");

        Integer grants = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu WHERE role_id = 1 AND menu_id = ? AND deleted = 0",
                Integer.class, createdId);
        assertEquals(1, grants,
                "create must auto-grant the new node to the admin role (role 1, explicit "
                        + "role_menu model - no wildcard)");

        cleanupMenu(createdId);
    }

    @Test
    void createRejectsButtonParentAndUnknownParent() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();

        // An F node to act as the forbidden parent.
        int buttonId = createNode(token, ROLE_MENU_PAGE_ID, "F", "ut按钮父_" + ts, null);

        ResponseEntity<String> underButton = exchangeJson(HttpMethod.POST, MENUS_URL,
                "{\"upId\":" + buttonId + ",\"type\":\"C\",\"title\":\"ut按钮之子_"
                        + ts + "\",\"sort\":1,\"visible\":1}", token);
        assertEquals(200, underButton.getStatusCodeValue());
        assertEquals("A0400", parse(underButton).path("code").asText(),
                "a child under a button (F) parent must be rejected - buttons are leaves");

        ResponseEntity<String> unknownParent = exchangeJson(HttpMethod.POST, MENUS_URL,
                "{\"upId\":987654321,\"type\":\"C\",\"title\":\"ut孤儿_"
                        + ts + "\",\"sort\":1,\"visible\":1}", token);
        assertEquals(200, unknownParent.getStatusCodeValue());
        assertEquals("A0400", parse(unknownParent).path("code").asText(),
                "a non-live parent reference must be rejected service-side");

        cleanupMenu(buttonId);
    }

    @Test
    void moveRejectsSelfAndDescendantCycleAndFBackdoor() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();

        int parentA = createNode(token, ROLE_MENU_PAGE_ID, "C", "ut环节点A_" + ts, null);
        int childB = createNode(token, parentA, "C", "ut环节点B_" + ts, null);

        // upId = self
        ResponseEntity<String> selfMove = exchangeJson(HttpMethod.PUT, MENUS_URL + "/" + parentA,
                "{\"upId\":" + parentA + ",\"type\":\"C\",\"title\":\"ut环节点A_"
                        + ts + "\",\"sort\":1,\"visible\":1}", token);
        assertEquals("A0400", parse(selfMove).path("code").asText(),
                "moving a node under itself must be rejected");

        // upId = own descendant
        ResponseEntity<String> cycleMove = exchangeJson(HttpMethod.PUT, MENUS_URL + "/" + parentA,
                "{\"upId\":" + childB + ",\"type\":\"C\",\"title\":\"ut环节点A_"
                        + ts + "\",\"sort\":1,\"visible\":1}", token);
        assertEquals("A0400", parse(cycleMove).path("code").asText(),
                "moving a node under its own descendant must be rejected (tree integrity)");

        // back-door leaf violation: type flip to F while children are live
        ResponseEntity<String> fFlip = exchangeJson(HttpMethod.PUT, MENUS_URL + "/" + parentA,
                "{\"upId\":" + ROLE_MENU_PAGE_ID + ",\"type\":\"F\",\"title\":\"ut环节点A_"
                        + ts + "\",\"sort\":1,\"visible\":1}", token);
        assertEquals("A0400", parse(fFlip).path("code").asText(),
                "flipping a node with live children to button (F) must be rejected");

        // the tree stays intact after the rejected moves
        JsonNode nodeA = findNode(menuTree(token), parentA);
        assertNotNull(nodeA, "the rejected moves must not orphan or delete the node");
        assertEquals(ROLE_MENU_PAGE_ID, nodeA.path("upId").asInt(-1),
                "the node must still hang under its original parent");
        assertEquals(1, nodeA.path("children").size(),
                "the child must still be nested under node A");

        cleanupMenu(childB);
        cleanupMenu(parentA);
    }

    @Test
    void deleteRejectsParentWithChildrenThenLeafDeleteCleansBindings() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String perms = "ut.menu:drop:" + ts;

        int parentA = createNode(token, ROLE_MENU_PAGE_ID, "C", "ut删环节点_" + ts, null);
        int buttonChild = createNode(token, parentA, "F", "ut删按钮_" + ts, perms);

        // parent delete rejected while the child is live
        ResponseEntity<String> parentDelete = exchangeJson(HttpMethod.DELETE,
                MENUS_URL + "/" + parentA, null, token);
        assertEquals(200, parentDelete.getStatusCodeValue());
        assertEquals("A0400", parse(parentDelete).path("code").asText(),
                "deleting a node with live children must be rejected");
        Integer liveAfterReject = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE id = ? AND deleted = 0",
                Integer.class, parentA);
        assertEquals(1, liveAfterReject, "the rejected delete must leave the parent live");

        // leaf delete: soft-deletes the node AND its role_menu bindings
        ResponseEntity<String> leafDelete = exchangeJson(HttpMethod.DELETE,
                MENUS_URL + "/" + buttonChild, null, token);
        JsonNode leafBody = parse(leafDelete);
        assertEquals("200", leafBody.path("code").asText(),
                "the childless delete must succeed: " + leafBody);
        Integer menuLive = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE id = ? AND deleted = 0",
                Integer.class, buttonChild);
        assertEquals(0, menuLive, "the deleted leaf must be logically deleted");
        Integer bindingLive = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu WHERE menu_id = ? AND deleted = 0",
                Integer.class, buttonChild);
        assertEquals(0, bindingLive,
                "the delete must soft-clean the role_menu bindings - a deleted button "
                        + "must stop granting its perms");

        // now the parent deletes too
        ResponseEntity<String> parentRetry = exchangeJson(HttpMethod.DELETE,
                MENUS_URL + "/" + parentA, null, token);
        assertEquals("200", parse(parentRetry).path("code").asText(),
                "after the child is gone the parent delete must succeed");

        assertNull(findNode(menuTree(token), buttonChild),
                "the deleted button must no longer render in the tree");
    }

    @Test
    void updateNeverTouchesGrants() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();

        int nodeId = createNode(token, ROLE_MENU_PAGE_ID, "C", "ut改节点_" + ts, null);
        Integer grantsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu WHERE role_id = 1 AND menu_id = ? AND deleted = 0",
                Integer.class, nodeId);
        assertEquals(1, grantsBefore, "the create must have auto-granted the node");

        // rename + move: the grant must stay exactly one live row
        ResponseEntity<String> updateResponse = exchangeJson(HttpMethod.PUT, MENUS_URL + "/" + nodeId,
                "{\"upId\":" + CONFIG_GROUP_ID + ",\"type\":\"C\",\"title\":\"ut改节点改名_"
                        + ts + "\",\"sort\":3,\"visible\":0}", token);
        JsonNode updateBody = parse(updateResponse);
        assertEquals("200", updateBody.path("code").asText(),
                "the full-field update (rename + move) must succeed: " + updateBody);

        Integer grantsAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu WHERE role_id = 1 AND menu_id = ? AND deleted = 0",
                Integer.class, nodeId);
        assertEquals(1, grantsAfter, "update must not add, remove, or duplicate grants");

        JsonNode moved = findNode(menuTree(token), nodeId);
        assertNotNull(moved, "the moved node must still render");
        assertEquals(CONFIG_GROUP_ID, moved.path("upId").asInt(-1), "the move must apply");
        assertEquals("ut改节点改名_" + ts, moved.path("title").asText(), "the rename must apply");
        assertFalse(moved.path("visible").asBoolean(true), "visible=0 must round-trip as hidden");

        cleanupMenu(nodeId);
    }

    @Test
    void createValidationRejectionsAnswerA0400() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();

        ResponseEntity<String> blankTitle = exchangeJson(HttpMethod.POST, MENUS_URL,
                "{\"upId\":" + ROLE_MENU_PAGE_ID + ",\"type\":\"C\",\"title\":\"\",\"sort\":1}", token);
        assertEquals("A0400", parse(blankTitle).path("code").asText(),
                "a blank title must be rejected by bean validation");

        ResponseEntity<String> badType = exchangeJson(HttpMethod.POST, MENUS_URL,
                "{\"upId\":" + ROLE_MENU_PAGE_ID + ",\"type\":\"X\",\"title\":\"ut坏类型_"
                        + ts + "\",\"sort\":1}", token);
        assertEquals("A0400", parse(badType).path("code").asText(),
                "an unknown node type must be rejected by bean validation");
    }

    // ===== helpers =====

    /** Creates a node through the API and returns its id (unique title via the ts). */
    private int createNode(String token, int upId, String type, String title, String perms)
            throws Exception {
        StringBuilder body = new StringBuilder("{\"upId\":").append(upId)
                .append(",\"type\":\"").append(type)
                .append("\",\"title\":\"").append(title).append("\"");
        if (perms != null) {
            body.append(",\"perms\":\"").append(perms).append("\"");
        }
        body.append(",\"sort\":1,\"visible\":1}");
        ResponseEntity<String> response = exchangeJson(HttpMethod.POST, MENUS_URL, body.toString(), token);
        JsonNode bodyNode = parse(response);
        assertEquals("200", bodyNode.path("code").asText(), "test-fixture create must succeed: " + bodyNode);
        int id = bodyNode.path("data").asInt(-1);
        assertTrue(id > 0, "test-fixture create must return an id");
        return id;
    }

    /** The full menu tree through the admin token. */
    private JsonNode menuTree(String token) throws Exception {
        ResponseEntity<String> response = exchangeJson(HttpMethod.GET, MENUS_TREE_URL, null, token);
        assertEquals(200, response.getStatusCodeValue());
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(), "the tree query must succeed: " + body);
        return body.path("data");
    }

    /** Depth-first search for a node by mid. */
    private static JsonNode findNode(JsonNode nodes, int mid) {
        if (!nodes.isArray()) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (node.path("mid").asInt(-1) == mid) {
                return node;
            }
            JsonNode found = findNode(node.path("children"), mid);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Logical-delete cleanup for a created node and any of its role_menu rows. */
    private void cleanupMenu(int menuId) {
        jdbcTemplate.update("UPDATE system.role_menu SET deleted = 1 WHERE menu_id = ?", menuId);
        jdbcTemplate.update("UPDATE system.menu_info SET deleted = 1 WHERE id = ?", menuId);
    }

    private String adminToken() throws Exception {
        // run-scoped admin clone (base-class identity): the sign rows attribute to
        // RUN_ACCOUNT and are swept at class exit - the real admin never logs in here.
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
}
