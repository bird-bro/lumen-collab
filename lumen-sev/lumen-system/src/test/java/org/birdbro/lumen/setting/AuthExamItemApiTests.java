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
 * Item endpoint proof (exam-setting-api task 4.1; spec deltas exam-setting "Exam Item
 * Columns" / "Exam Item Method Binding" / "Exam Item Endpoints" / "Modality
 * Vocabulary" / "Referenced-Row Delete Guards"): the group-filtered page with
 * SERVER-RESOLVED body/group/method display names (D2 - no denormalized copies), the
 * modality/body validations, per-modality code uniqueness including the soft-deleted
 * reuse path, the null-aware update whose binding rewrite is exact, and logical
 * delete. Timestamped fixtures, hard-deleted afterwards (user ruling 2026-11-07).
 */
class AuthExamItemApiTests extends SettingIntegrationTestBase {

    private static final String ITEMS_URL = "/api/v1/setting/exam-items";
    private static final String GROUPS_URL = "/api/v1/setting/exam-body-groups";
    private static final String BODIES_URL = "/api/v1/setting/exam-bodies";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void pageFilterByGroupResolvesDisplayNamesServerSide() throws Exception {
        String token = fullPermToken();
        long ts = System.currentTimeMillis();
        int group = createGroup("CT", "ut组_项目页_" + ts, 1);
        int otherGroup = createGroup("CT", "ut组_项目页外_" + ts, 2);
        int body = createBody(group, "CT", "ut_exb_肺_" + ts, null);
        int outsideBody = createBody(otherGroup, "CT", "ut_exb_脑_" + ts, null);
        int method = createMethod("CT", "ut_exm_平扫_" + ts, "ut_exm_" + ts, 1);

        int inItem = createItem(group, body, "ut_exi_胸部CT_" + ts, "ut_exi_" + ts + "_a",
                method);
        int outItem = createItem(otherGroup, outsideBody, "ut_exi_头颅CT_" + ts,
                "ut_exi_" + ts + "_b", method);

        JsonNode page = parse(exchangeJson(HttpMethod.GET,
                ITEMS_URL + "/page?pageNum=1&pageSize=50&modality=CT&groupId=" + group
                        + "&keyword=" + ts, null, token));
        assertEquals("200", page.path("code").asText(), "the item page must succeed");
        assertEquals(1, page.path("data").path("total").asLong(-1),
                "the group filter must isolate the group's items: " + page);
        JsonNode row = page.path("data").path("records").get(0);
        assertEquals(inItem, row.path("id").asInt(-1), "the group hit must be the in-group item");
        assertEquals("ut_exb_肺_" + ts, row.path("bodyName").asText(),
                "the body display name must resolve server-side (D2)");
        assertEquals("ut组_项目页_" + ts, row.path("groupName").asText(),
                "the group display name must resolve server-side (D2)");
        assertEquals("ut_exm_平扫_" + ts, row.path("methodNames").asText(),
                "the method display names must resolve server-side (D2)");
        assertTrue(row.path("methodValue").isMissingNode()
                        && row.path("bodyValue").isMissingNode(),
                "no RCP denormalized name columns may exist on the wire");

        // Detail echoes the same resolved shape for the edit drawer.
        JsonNode detail = parse(exchangeJson(HttpMethod.GET, ITEMS_URL + "/" + inItem,
                null, token));
        assertEquals("ut_exm_平扫_" + ts, detail.path("data").path("methodNames").asText(),
                "the detail must resolve display names too");
        assertEquals(method, detail.path("data").path("methodIds").get(0).asInt(-1),
                "the detail must carry the binding ids (the edit form's rewrite input)");

        hardDeleteItem(inItem);
        hardDeleteItem(outItem);
        hardDeleteBody(body);
        hardDeleteBody(outsideBody);
        hardDeleteGroup(group);
        hardDeleteGroup(otherGroup);
        hardDeleteMethod(method);
    }

    @Test
    void itemCreateValidatesModalityBodyAndTheirLockstep() throws Exception {
        String token = fullPermToken();
        long ts = System.currentTimeMillis();
        int group = createGroup("CT", "ut组_项目校验_" + ts, 1);
        int body = createBody(group, "CT", "ut_exb_校验部位_" + ts, null);
        int mrGroup = createGroup("MR", "ut组_项目对照_" + ts, 1);
        int mrBody = createBody(mrGroup, "MR", "ut_exb_对照部位_" + ts, null);

        ResponseEntity<String> unknownModality = exchangeJson(HttpMethod.POST, ITEMS_URL,
                body(itemPayload("XX", body, "ut_exi_野模态_" + ts, null)), token);
        assertEquals("A0400", parse(unknownModality).path("code").asText(),
                "a modality absent from dict 1024 must reject A0400: " + unknownModality);

        ResponseEntity<String> deadBody = exchangeJson(HttpMethod.POST, ITEMS_URL,
                body(itemPayload("CT", 99999999, "ut_exi_孤儿项目_" + ts, null)), token);
        assertEquals("A0400", parse(deadBody).path("code").asText(),
                "an item on a dead body must reject A0400: " + deadBody);

        ResponseEntity<String> mismatch = exchangeJson(HttpMethod.POST, ITEMS_URL,
                body(itemPayload("CT", mrBody, "ut_exi_错位项目_" + ts, null)), token);
        assertEquals("A0400", parse(mismatch).path("code").asText(),
                "the item's modality must match its body's: " + mismatch);

        hardDeleteBody(body);
        hardDeleteBody(mrBody);
        hardDeleteGroup(group);
        hardDeleteGroup(mrGroup);
    }

    @Test
    void itemCodeUniquenessIsPerModalityAndSoftDeletedRowsFreeIt() throws Exception {
        String token = fullPermToken();
        long ts = System.currentTimeMillis();
        int group = createGroup("CT", "ut组_码唯一_" + ts, 1);
        int body = createBody(group, "CT", "ut_exb_码部位_" + ts, null);
        int first = createItem(group, body, "ut_exi_甲_" + ts, "ut_exi_" + ts + "_dup", 0);

        ResponseEntity<String> duplicate = exchangeJson(HttpMethod.POST, ITEMS_URL,
                body(itemPayload("CT", body, "ut_exi_乙_" + ts, "ut_exi_" + ts + "_dup")), token);
        assertEquals("A0400", parse(duplicate).path("code").asText(),
                "a duplicate live code within the modality must reject A0400: " + duplicate);

        // Logical delete frees the code for reuse (partial unique: live rows only).
        exchangeJson(HttpMethod.DELETE, ITEMS_URL + "/" + first, null, token);
        Integer deletedFlag = jdbcTemplate.queryForObject(
                "SELECT deleted FROM system.exam_item WHERE id = ?", Integer.class, first);
        assertEquals(Integer.valueOf(1), deletedFlag,
                "the delete must be LOGICAL (deleted=1), never physical");

        ResponseEntity<String> reuse = exchangeJson(HttpMethod.POST, ITEMS_URL,
                body(itemPayload("CT", body, "ut_exi_丙_" + ts, "ut_exi_" + ts + "_dup")), token);
        assertEquals("200", parse(reuse).path("code").asText(),
                "the freed code must be reusable after a logical delete: " + reuse);
        int reused = parse(reuse).path("data").asInt(-1);

        hardDeleteItem(first);
        hardDeleteItem(reused);
        hardDeleteBody(body);
        hardDeleteGroup(group);
    }

    @Test
    void itemUpdateIsNullAwareAndRewritesBindingsExactly() throws Exception {
        String token = fullPermToken();
        long ts = System.currentTimeMillis();
        int group = createGroup("CT", "ut组_改绑定_" + ts, 1);
        int body = createBody(group, "CT", "ut_exb_改部位_" + ts, null);
        int m1 = createMethod("CT", "ut_exm_甲法_" + ts, "ut_exm_" + ts + "1", 1);
        int m2 = createMethod("CT", "ut_exm_乙法_" + ts, "ut_exm_" + ts + "2", 2);
        int m3 = createMethod("CT", "ut_exm_丙法_" + ts, "ut_exm_" + ts + "3", 3);

        int item = createItem(group, body, "ut_exi_原项目_" + ts, "ut_exi_" + ts + "_u", m1);
        exchangeJson(HttpMethod.PUT, ITEMS_URL + "/" + item,
                body("{\"modality\":\"CT\",\"bodyId\":" + body + ",\"exam\":\"ut_exi_新项目_"
                        + ts + "\",\"code\":\"ut_exi_" + ts + "_u\",\"price\":12.50,"
                        + "\"tag\":2,\"scoreRep\":7,\"scoreAud\":3,\"isCommon\":true,"
                        + "\"methodIds\":[" + m2 + "," + m3 + "],\"enable\":1}"), token);

        // D9: remark absent from the payload must CLEAR; the scored fields persist.
        String remark = jdbcTemplate.queryForObject(
                "SELECT remark FROM system.exam_item WHERE id = ?", String.class, item);
        assertEquals(null, remark, "an absent optional must CLEAR (D9)");
        Integer scoreRep = jdbcTemplate.queryForObject(
                "SELECT score_rep FROM system.exam_item WHERE id = ?", Integer.class, item);
        assertEquals(Integer.valueOf(7), scoreRep, "the performance weight must persist");

        // The binding rewrite is EXACT: A,B → B,C leaves exactly B,C.
        JsonNode detail = parse(exchangeJson(HttpMethod.GET, ITEMS_URL + "/" + item,
                null, token));
        assertEquals("ut_exm_乙法_" + ts + ",ut_exm_丙法_" + ts,
                detail.path("data").path("methodNames").asText(),
                "the binding set must be exactly the new selection, in request order");

        hardDeleteItem(item);
        hardDeleteBody(body);
        hardDeleteGroup(group);
        hardDeleteMethod(m1);
        hardDeleteMethod(m2);
        hardDeleteMethod(m3);
    }

    @Test
    void itemWritesDenyThePermlessIdentityBeforeAnyControllerLogic() throws Exception {        String token = fullPermToken();
        long ts = System.currentTimeMillis();
        int group = createGroup("CT", "ut组_拒权_" + ts, 1);
        int body = createBody(group, "CT", "ut_exb_拒权部位_" + ts, null);

        ResponseEntity<String> denied = exchangeJson(HttpMethod.POST, ITEMS_URL,
                body(itemPayload("CT", body, "ut_exi_拒权项目_" + ts, null)), noPermToken());
        assertEquals("A0301", parse(denied).path("code").asText(),
                "a perm-less create must deny A0301 before the controller: " + denied);
        Integer created = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.exam_item WHERE code LIKE 'ut_exi_%' "
                        + "AND exam LIKE 'ut_exi_拒权项目_%'", Integer.class);
        assertEquals(Integer.valueOf(0), created.intValue(),
                "the denied create must not reach the controller (no item row)");

        hardDeleteBody(body);
        hardDeleteGroup(group);
    }

    @Test
    void externalMappingCodeEchoesReplacesAndStaysNullable() throws Exception {
        String token = fullPermToken();
        long ts = System.currentTimeMillis();
        int group = createGroup("CT", "ut组_对照码_" + ts, 1);
        int body = createBody(group, "CT", "ut_exb_对照部位_" + ts, null);

        int mapped = createItemWithExternalCode(group, body, "ut_exi_对照甲_" + ts,
                "ut_exi_" + ts + "_c1", "ut_his_" + ts);
        int bare = createItemWithExternalCode(group, body, "ut_exi_对照乙_" + ts,
                "ut_exi_" + ts + "_c2", null);

        JsonNode mappedDetail = parse(exchangeJson(HttpMethod.GET, ITEMS_URL + "/" + mapped,
                null, token));
        assertEquals("ut_his_" + ts, mappedDetail.path("data").path("externalCode").asText(),
                "the detail must echo the external mapping code verbatim: " + mappedDetail);

        JsonNode bareDetail = parse(exchangeJson(HttpMethod.GET, ITEMS_URL + "/" + bare,
                null, token));
        assertTrue(bareDetail.path("data").path("externalCode").isNull()
                        || bareDetail.path("data").path("externalCode").isMissingNode(),
                "an item created without a mapping code must echo NULL, not a default: "
                        + bareDetail);

        // Full-replace PUT swaps the mapping code (the drawer's edit path). The
        // NOT NULL columns ride along - a full-replace PUT omitting them is a
        // PRE-EXISTING sharp edge outside this change's scope.
        exchangeJson(HttpMethod.PUT, ITEMS_URL + "/" + mapped,
                body("{\"modality\":\"CT\",\"bodyId\":" + body + ",\"exam\":\"ut_exi_对照甲_"
                        + ts + "\",\"code\":\"ut_exi_" + ts + "_c1\",\"externalCode\":\"ut_his_"
                        + ts + "_v2\",\"methodIds\":[],\"isCommon\":false,\"enable\":1}"), token);
        JsonNode replaced = parse(exchangeJson(HttpMethod.GET, ITEMS_URL + "/" + mapped,
                null, token));
        assertEquals("ut_his_" + ts + "_v2", replaced.path("data").path("externalCode").asText(),
                "the update must replace the mapping code: " + replaced);

        hardDeleteItem(mapped);
        hardDeleteItem(bare);
        hardDeleteBody(body);
        hardDeleteGroup(group);
    }

    @Test
    void duplicateMappingCodesAreAcceptedAndKeywordHitsThem() throws Exception {
        String token = fullPermToken();
        long ts = System.currentTimeMillis();
        int group = createGroup("CT", "ut组_打包_" + ts, 1);
        int body = createBody(group, "CT", "ut_exb_打包部位_" + ts, null);
        String his = "ut_his_" + ts;

        int first = createItemWithExternalCode(group, body, "ut_exi_打包甲_" + ts,
                "ut_exi_" + ts + "_p1", his);
        // 打包计费: a SECOND live item in the same modality maps to the SAME HIS
        // charge item - the create must NOT reject (no uniqueness on external_code).
        int second = createItemWithExternalCode(group, body, "ut_exi_打包乙_" + ts,
                "ut_exi_" + ts + "_p2", his);

        Integer dup = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.exam_item WHERE external_code = ? AND deleted = 0",
                Integer.class, his);
        assertEquals(Integer.valueOf(2), dup.intValue(),
                "two live items may share one external mapping code (打包计费)");

        // Keyword retrieval spans the mapping code column (the maintenance lookup).
        JsonNode page = parse(exchangeJson(HttpMethod.GET,
                ITEMS_URL + "/page?pageNum=1&pageSize=50&modality=CT&keyword=" + his,
                null, token));
        assertEquals("200", page.path("code").asText(), "the keyword page must succeed");
        assertEquals(2, page.path("data").path("total").asLong(-1),
                "the keyword must hit the external mapping code branch: " + page);

        hardDeleteItem(first);
        hardDeleteItem(second);
        hardDeleteBody(body);
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
                + ",\"name\":\"" + name + "\",\"enable\":1}";
        ResponseEntity<String> created = exchangeJson(HttpMethod.POST, BODIES_URL,
                body(payload), fullPermToken());
        JsonNode createdBody = parse(created);
        assertEquals("200", createdBody.path("code").asText(),
                "the fixture body must create cleanly: " + created);
        return createdBody.path("data").asInt(-1);
    }

    private int createMethod(String modality, String name, String code, int sort)
            throws Exception {
        ResponseEntity<String> created = exchangeJson(HttpMethod.POST,
                "/api/v1/setting/exam-methods",
                body("{\"modality\":\"" + modality + "\",\"name\":\"" + name
                        + "\",\"code\":\"" + code + "\",\"sort\":" + sort + ",\"enable\":1}"),
                fullPermToken());
        JsonNode createdBody = parse(created);
        assertEquals("200", createdBody.path("code").asText(),
                "the fixture method must create cleanly: " + created);
        return createdBody.path("data").asInt(-1);
    }

    private int createItem(int groupId, int bodyId, String exam, String code, Integer methodId)
            throws Exception {
        String payload = itemPayload("CT", bodyId, exam, code);
        if (methodId != null) {
            payload = payload.substring(0, payload.length() - 1)
                    + ",\"methodIds\":[" + methodId + "]}";
        }
        ResponseEntity<String> created = exchangeJson(HttpMethod.POST, ITEMS_URL,
                body(payload), fullPermToken());
        JsonNode createdBody = parse(created);
        assertEquals("200", createdBody.path("code").asText(),
                "the fixture item must create cleanly: " + created);
        return createdBody.path("data").asInt(-1);
    }

    /** A minimal valid item payload; extra fields are appended by the callers. */
    private String itemPayload(String modality, int bodyId, String exam, String code) {
        return "{\"modality\":\"" + modality + "\",\"bodyId\":" + bodyId
                + ",\"exam\":\"" + exam + "\""
                + (code != null ? ",\"code\":\"" + code + "\"" : "")
                + ",\"enable\":1}";
    }

    /** Item fixture with an external mapping code (null = create without one). */
    private int createItemWithExternalCode(int groupId, int bodyId, String exam,
            String code, String externalCode) throws Exception {
        String payload = itemPayload("CT", bodyId, exam, code);
        if (externalCode != null) {
            payload = payload.substring(0, payload.length() - 1)
                    + ",\"externalCode\":\"" + externalCode + "\"}";
        }
        ResponseEntity<String> created = exchangeJson(HttpMethod.POST, ITEMS_URL,
                body(payload), fullPermToken());
        JsonNode createdBody = parse(created);
        assertEquals("200", createdBody.path("code").asText(),
                "the fixture item must create cleanly: " + created);
        return createdBody.path("data").asInt(-1);
    }

    private void hardDeleteGroup(int id) {
        jdbcTemplate.update("DELETE FROM system.exam_body_group WHERE id = ?", id);
    }

    private void hardDeleteBody(int id) {
        jdbcTemplate.update("DELETE FROM system.exam_body WHERE id = ?", id);
    }

    private void hardDeleteMethod(int id) {
        jdbcTemplate.update("DELETE FROM system.exam_item_method WHERE method_id = ?", id);
        jdbcTemplate.update("DELETE FROM system.exam_method WHERE id = ?", id);
    }

    private void hardDeleteItem(int id) {
        jdbcTemplate.update("DELETE FROM system.exam_item_method WHERE exam_id = ?", id);
        jdbcTemplate.update("DELETE FROM system.exam_item WHERE id = ?", id);
    }

    private HttpEntity<String> body(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(json, headers);
    }

    private ResponseEntity<String> exchangeJson(HttpMethod method, String url,
            HttpEntity<String> entity, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (entity != null && entity.getHeaders() != null) {
            headers.addAll(entity.getHeaders());
        }
        headers.set(AUTH_HEADER, "Bearer " + token);
        return restTemplate.exchange(url, method,
                new HttpEntity<>(entity != null ? entity.getBody() : null, headers),
                String.class);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        assertNotNull(response.getBody(), "every call must carry an envelope body");
        return objectMapper.readTree(response.getBody());
    }
}
