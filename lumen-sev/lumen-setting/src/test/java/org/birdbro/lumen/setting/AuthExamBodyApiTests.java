package org.birdbro.lumen.setting;

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
 * Body group + body endpoint proof (exam-setting-api tasks 2.1-2.2; spec deltas
 * exam-setting "Body Group and Body Endpoints" / "Referenced-Row Delete Guards" /
 * "Modality Vocabulary" / "Exam Body Columns"): modality-scoped group lists, the
 * up_id-nested body tree, group→body cascade inputs, the split-model validations
 * (group existence, same-modality group, same-group parent), D9 null-aware updates,
 * and the delete guards that name their blocking reference. Timestamped fixtures,
 * hard-deleted afterwards (user ruling 2026-11-07).
 */
class AuthExamBodyApiTests extends SettingIntegrationTestBase {

    private static final String GROUPS_URL = "/api/v1/setting/exam-body-groups";
    private static final String BODIES_URL = "/api/v1/setting/exam-bodies";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void groupListIsModalityScopedAndSorted() throws Exception {
        String token = fullPermToken();
        long ts = System.currentTimeMillis();
        int chest = createGroup("CT", "ut组_胸部_" + ts, 2);
        int head = createGroup("CT", "ut组_头颅_" + ts, 1);
        int usGroup = createGroup("US", "ut组_腹部_" + ts, 1);

        JsonNode list = parse(exchangeJson(HttpMethod.GET,
                GROUPS_URL + "/list?modality=CT", null, token));
        assertEquals("200", list.path("code").asText(), "the group list must succeed");
        // Shared long-lived database: contains-style assertions, never exact sizes.
        JsonNode names = list.path("data");
        int headIndex = indexOfName(names, "ut组_头颅_" + ts);
        int chestIndex = indexOfName(names, "ut组_胸部_" + ts);
        assertTrue(headIndex >= 0 && chestIndex >= 0,
                "the CT list must carry both fixture groups: " + names);
        assertTrue(headIndex < chestIndex,
                "the list must follow the sort column, not the insert order");
        assertEquals(-1, indexOfName(names, "ut组_腹部_" + ts),
                "the CT list must NOT carry the US group");

        hardDeleteGroup(chest);
        hardDeleteGroup(head);
        hardDeleteGroup(usGroup);
    }

    @Test
    void groupCreateRejectsDuplicateNameInModalityAndUnknownModality() throws Exception {
        String token = fullPermToken();
        long ts = System.currentTimeMillis();
        int group = createGroup("CT", "ut组_唯一_" + ts, 1);

        ResponseEntity<String> duplicate = exchangeJson(HttpMethod.POST, GROUPS_URL,
                body("{\"modality\":\"CT\",\"name\":\"ut组_唯一_"
                        + ts + "\",\"sort\":2,\"enable\":1}"), token);
        assertEquals("A0400", parse(duplicate).path("code").asText(),
                "a duplicate live group name within the modality must reject A0400: "
                        + duplicate);

        ResponseEntity<String> unknownModality = exchangeJson(HttpMethod.POST, GROUPS_URL,
                body("{\"modality\":\"XX\",\"name\":\"ut组_野模态_"
                        + ts + "\",\"sort\":1,\"enable\":1}"), token);
        assertEquals("A0400", parse(unknownModality).path("code").asText(),
                "a modality absent from dict 1027 must reject A0400: " + unknownModality);

        // The same name IS legal in another modality - scoping is per modality.
        ResponseEntity<String> otherModality = exchangeJson(HttpMethod.POST, GROUPS_URL,
                body("{\"modality\":\"MR\",\"name\":\"ut组_唯一_"
                        + ts + "\",\"sort\":2,\"enable\":1}"), token);
        assertEquals("200", parse(otherModality).path("code").asText(),
                "the same name in another modality must be accepted: " + otherModality);
        int mrGroup = parse(otherModality).path("data").asInt(-1);
        assertTrue(mrGroup > 0, "create must return the database-generated id");

        hardDeleteGroup(group);
        hardDeleteGroup(mrGroup);
    }

    @Test
    void groupDeleteIsGuardedByItsBodies() throws Exception {
        String token = fullPermToken();
        long ts = System.currentTimeMillis();
        int group = createGroup("CT", "ut组_守卫_" + ts, 1);
        int bodyId = createBody(group, "CT", "ut_exb_守卫部位_" + ts, null);

        ResponseEntity<String> blocked = exchangeJson(HttpMethod.DELETE,
                GROUPS_URL + "/" + group, null, token);
        JsonNode blockedBody = parse(blocked);
        assertEquals("A0400", blockedBody.path("code").asText(),
                "a group with live bodies must refuse deletion: " + blocked);
        assertTrue(blockedBody.path("msg").asText().contains("部位"),
                "the refusal must name the blocking reference: " + blockedBody);

        hardDeleteBody(bodyId);
        ResponseEntity<String> freed = exchangeJson(HttpMethod.DELETE,
                GROUPS_URL + "/" + group, null, token);
        assertEquals("200", parse(freed).path("code").asText(),
                "deleting the last body must free the group: " + freed);
    }

    @Test
    void bodyTreeNestsByUpIdWithinTheGroupScope() throws Exception {
        String token = fullPermToken();
        long ts = System.currentTimeMillis();
        int group = createGroup("CT", "ut组_树_" + ts, 1);
        int parent = createBody(group, "CT", "ut_exb_胸部_" + ts, null);
        int lung = createBody(group, "CT", "ut_exb_肺_" + ts, parent);
        int usGroup = createGroup("US", "ut组_肝胆_" + ts, 1);
        int usBody = createBody(usGroup, "US", "ut_exb_肝_" + ts, null);

        JsonNode tree = parse(exchangeJson(HttpMethod.GET,
                BODIES_URL + "/list?modality=CT&groupId=" + group, null, token));
        assertEquals("200", tree.path("code").asText(), "the body tree must succeed");
        JsonNode nodes = tree.path("data");
        assertEquals(1, nodes.size(),
                "the group-scoped tree must return only that group's roots");
        assertEquals("ut_exb_胸部_" + ts, nodes.get(0).path("name").asText(),
                "the root must be the up_id-less body");
        assertEquals(1, nodes.get(0).path("children").size(),
                "the child must nest under its parent");
        assertEquals("ut_exb_肺_" + ts,
                nodes.get(0).path("children").get(0).path("name").asText(),
                "the child node must carry its own fields");

        JsonNode usTree = parse(exchangeJson(HttpMethod.GET,
                BODIES_URL + "/list?modality=US", null, token));
        assertTrue(usTree.path("data").toString().contains("ut_exb_肝_" + ts),
                "the modality-scoped (group-less) list must cover the modality's bodies");

        hardDeleteBody(lung);
        hardDeleteBody(parent);
        hardDeleteBody(usBody);
        hardDeleteGroup(group);
        hardDeleteGroup(usGroup);
    }

    @Test
    void bodyCreateValidatesGroupExistenceModalityAndParentScope() throws Exception {
        String token = fullPermToken();
        long ts = System.currentTimeMillis();
        int group = createGroup("CT", "ut组_校验_" + ts, 1);
        int mrGroup = createGroup("MR", "ut组_对照_" + ts, 1);
        int parent = createBody(group, "CT", "ut_exb_父部位_" + ts, null);
        int mrBody = createBody(mrGroup, "MR", "ut_exb_对照部位_" + ts, null);

        ResponseEntity<String> deadGroup = exchangeJson(HttpMethod.POST, BODIES_URL,
                body("{\"modality\":\"CT\",\"groupId\":99999999,\"name\":\"ut_exb_孤儿_"
                        + ts + "\",\"enable\":1}"), token);
        assertEquals("A0400", parse(deadGroup).path("code").asText(),
                "a body on a dead group must reject A0400: " + deadGroup);

        ResponseEntity<String> modalityMismatch = exchangeJson(HttpMethod.POST, BODIES_URL,
                body("{\"modality\":\"MR\",\"groupId\":" + group
                        + ",\"name\":\"ut_exb_错模态_" + ts + "\",\"enable\":1}"), token);
        assertEquals("A0400", parse(modalityMismatch).path("code").asText(),
                "the body's modality must match its group's: " + modalityMismatch);

        ResponseEntity<String> deadParent = exchangeJson(HttpMethod.POST, BODIES_URL,
                body("{\"modality\":\"CT\",\"groupId\":" + group + ",\"upId\":99999999"
                        + ",\"name\":\"ut_exb_错父_" + ts + "\",\"enable\":1}"), token);
        assertEquals("A0400", parse(deadParent).path("code").asText(),
                "a parent that misses every body must reject A0400: " + deadParent);

        ResponseEntity<String> foreignParent = exchangeJson(HttpMethod.POST, BODIES_URL,
                body("{\"modality\":\"CT\",\"groupId\":" + group + ",\"upId\":" + mrBody
                        + ",\"name\":\"ut_exb_跨组父_" + ts + "\",\"enable\":1}"), token);
        assertEquals("A0400", parse(foreignParent).path("code").asText(),
                "the parent must belong to the SAME group: " + foreignParent);

        hardDeleteBody(parent);
        hardDeleteBody(mrBody);
        hardDeleteGroup(group);
        hardDeleteGroup(mrGroup);
    }

    @Test
    void bodyUpdateIsNullAwareAndDeleteIsGuardedByChildrenAndItems() throws Exception {
        String token = fullPermToken();
        long ts = System.currentTimeMillis();
        int group = createGroup("CT", "ut组_改删_" + ts, 1);
        int parent = createBody(group, "CT", "ut_exb_父_" + ts, null);
        int child = createBody(group, "CT", "ut_exb_子_" + ts, parent);

        // D9 null-aware full-replace: an update without code CLEARS it.
        ResponseEntity<String> updated = exchangeJson(HttpMethod.PUT, BODIES_URL + "/" + child,
                body("{\"modality\":\"CT\",\"groupId\":" + group + ",\"upId\":" + parent
                        + ",\"name\":\"ut_exb_子_" + ts + "\",\"sort\":3,\"enable\":1}"), token);
        assertEquals("200", parse(updated).path("code").asText(),
                "the null-aware update must succeed: " + updated);
        String code = jdbcTemplate.queryForObject(
                "SELECT code FROM setting.exam_body WHERE id = ?", String.class, child);
        assertEquals(null, code, "an absent code must CLEAR (D9), not survive");

        // The child blocks the parent's deletion and names the reference.
        ResponseEntity<String> blocked = exchangeJson(HttpMethod.DELETE,
                BODIES_URL + "/" + parent, null, token);
        assertEquals("A0400", parse(blocked).path("code").asText(),
                "a body with live children must refuse deletion: " + blocked);

        // An item bound to the body blocks deletion even with no children.
        hardDeleteBody(child);
        int item = createItemFixture(group, parent, ts);
        ResponseEntity<String> itemBlocked = exchangeJson(HttpMethod.DELETE,
                BODIES_URL + "/" + parent, null, token);
        JsonNode itemBlockedBody = parse(itemBlocked);
        assertEquals("A0400", itemBlockedBody.path("code").asText(),
                "a body referenced by an item must refuse deletion: " + itemBlocked);
        assertTrue(itemBlockedBody.path("msg").asText().contains("项目"),
                "the refusal must name the item reference: " + itemBlockedBody);

        hardDeleteItem(item);
        hardDeleteBody(parent);
        hardDeleteGroup(group);
    }

    // ===================== fixture + call helpers =====================

    private int createGroup(String modality, String name, int sort) throws Exception {
        ResponseEntity<String> created = exchangeJson(HttpMethod.POST, GROUPS_URL,
                body("{\"modality\":\"" + modality + "\",\"name\":\"" + name
                        + "\",\"sort\":" + sort + ",\"enable\":1}"), fullPermToken());
        JsonNode createdBody = parse(created);
        assertEquals("200", createdBody.path("code").asText(),
                "the fixture group must create cleanly: " + created);
        return createdBody.path("data").asInt(-1);
    }

    private int createBody(int groupId, String modality, String name, Integer upId)
            throws Exception {
        String payload = "{\"modality\":\"" + modality + "\",\"groupId\":" + groupId
                + (upId != null ? ",\"upId\":" + upId : "")
                + ",\"name\":\"" + name + "\",\"code\":\"" + name + "\",\"enable\":1}";
        ResponseEntity<String> created = exchangeJson(HttpMethod.POST, BODIES_URL,
                body(payload), fullPermToken());
        JsonNode createdBody = parse(created);
        assertEquals("200", createdBody.path("code").asText(),
                "the fixture body must create cleanly: " + created);
        return createdBody.path("data").asInt(-1);
    }

    /** Direct item-row fixture (batch-2 tests need the binding, not the item API). */
    private int createItemFixture(int groupId, int bodyId, long ts) {
        jdbcTemplate.update("INSERT INTO setting.exam_item (modality, exam, code, body_id, "
                        + "enable) VALUES ('CT', ?, ?, ?, 1)",
                "ut_exi_守卫项目_" + ts, "ut_exi_" + ts, bodyId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM setting.exam_item WHERE code = ?", Integer.class,
                "ut_exi_" + ts);
    }

    private int indexOfName(JsonNode nodes, String name) {
        for (int i = 0; i < nodes.size(); i++) {
            if (name.equals(nodes.get(i).path("name").asText())) {
                return i;
            }
        }
        return -1;
    }

    private void hardDeleteGroup(int id) {
        jdbcTemplate.update("DELETE FROM setting.exam_body_group WHERE id = ?", id);
    }

    private void hardDeleteBody(int id) {
        jdbcTemplate.update("DELETE FROM setting.exam_body WHERE id = ?", id);
    }

    private void hardDeleteItem(int id) {
        jdbcTemplate.update("DELETE FROM setting.exam_item_method WHERE exam_id = ?", id);
        jdbcTemplate.update("DELETE FROM setting.exam_item WHERE id = ?", id);
    }

    private HttpEntity<String> body(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(json, headers);
    }

    private ResponseEntity<String> exchangeJson(HttpMethod method, String url,
            HttpEntity<String> entity, String token) {
        return restTemplate.exchange(url, method, withToken(entity, token), String.class);
    }

    private HttpEntity<String> withToken(HttpEntity<String> entity, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (entity != null && entity.getHeaders() != null) {
            headers.addAll(entity.getHeaders());
        }
        headers.set(AUTH_HEADER, "Bearer " + token);
        return new HttpEntity<>(entity != null ? entity.getBody() : null, headers);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        assertNotNull(response.getBody(), "every call must carry an envelope body");
        return objectMapper.readTree(response.getBody());
    }
}
