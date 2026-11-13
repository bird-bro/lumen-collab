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
 * Method endpoint proof (exam-setting-api task 3.1; spec deltas exam-setting "Exam
 * Method Endpoints" / "Exam Item Columns" back-references / "Referenced-Row Delete
 * Guards"): the modality-scoped page with name/code keyword, the picker list in sort
 * order, duplicate-code rejection within the modality (same code legal in another),
 * unknown-modality rejection, and the item-binding delete guard. Timestamped fixtures,
 * hard-deleted afterwards (user ruling 2026-11-07).
 */
class AuthExamMethodApiTests extends SettingIntegrationTestBase {

    private static final String METHODS_URL = "/api/v1/setting/exam-methods";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void methodPageFiltersByModalityAndNameOrCodeKeyword() throws Exception {
        String token = fullPermToken();
        long ts = System.currentTimeMillis();
        int plain = createMethod("CT", "ut_exm_平扫_" + ts, "ut_exm_" + ts + "_p", 1);
        int enhanced = createMethod("CT", "ut_exm_增强_" + ts, "ut_exm_" + ts + "_e", 2);
        int usMethod = createMethod("US", "ut_exm_二维_" + ts, "ut_exm_" + ts + "_u", 1);

        // Modality scoping: the CT page must not carry the US row.
        JsonNode ctPage = parse(exchangeJson(HttpMethod.GET,
                METHODS_URL + "/page?pageNum=1&pageSize=50&modality=CT&keyword=" + ts,
                null, token));
        assertEquals("200", ctPage.path("code").asText(), "the method page must succeed");
        assertEquals(2, ctPage.path("data").path("total").asLong(-1),
                "the CT keyword page must carry exactly the two CT fixtures: " + ctPage);

        // Keyword matches CODE too (queryType=2 on the console).
        JsonNode codePage = parse(exchangeJson(HttpMethod.GET,
                METHODS_URL + "/page?pageNum=1&pageSize=50&modality=CT&keyword="
                        + ts + "_e", null, token));
        assertEquals(1, codePage.path("data").path("total").asLong(-1),
                "the code keyword must isolate the enhanced method: " + codePage);
        assertEquals(enhanced,
                codePage.path("data").path("records").get(0).path("id").asInt(-1),
                "the code hit must be the enhanced method");

        hardDeleteMethod(plain);
        hardDeleteMethod(enhanced);
        hardDeleteMethod(usMethod);
    }

    @Test
    void methodListFeedsTheItemFormInSortOrder() throws Exception {
        String token = fullPermToken();
        long ts = System.currentTimeMillis();
        int second = createMethod("MR", "ut_exm_乙_" + ts, "ut_exm_" + ts + "_b", 2);
        int first = createMethod("MR", "ut_exm_甲_" + ts, "ut_exm_" + ts + "_a", 1);

        JsonNode list = parse(exchangeJson(HttpMethod.GET,
                METHODS_URL + "/list?modality=MR&keyword=" + ts, null, token));
        assertEquals("200", list.path("code").asText(), "the method list must succeed");
        JsonNode rows = list.path("data");
        int firstIndex = indexOfCode(rows, "ut_exm_" + ts + "_a");
        int secondIndex = indexOfCode(rows, "ut_exm_" + ts + "_b");
        assertTrue(firstIndex >= 0 && secondIndex >= 0,
                "the picker list must carry both fixtures: " + rows);
        assertTrue(firstIndex < secondIndex,
                "the picker list must follow the sort column");

        hardDeleteMethod(first);
        hardDeleteMethod(second);
    }

    @Test
    void methodCreateRejectsDuplicateCodeAndUnknownModality() throws Exception {
        String token = fullPermToken();
        long ts = System.currentTimeMillis();
        int method = createMethod("CT", "ut_exm_对照_" + ts, "ut_exm_" + ts + "_dup", 1);

        ResponseEntity<String> duplicate = exchangeJson(HttpMethod.POST, METHODS_URL,
                body("{\"modality\":\"CT\",\"name\":\"ut_exm_撞码_" + ts
                        + "\",\"code\":\"ut_exm_" + ts + "_dup\",\"enable\":1}"), token);
        assertEquals("A0400", parse(duplicate).path("code").asText(),
                "a duplicate live code within the modality must reject A0400: " + duplicate);

        ResponseEntity<String> unknownModality = exchangeJson(HttpMethod.POST, METHODS_URL,
                body("{\"modality\":\"XX\",\"name\":\"ut_exm_野模态_" + ts
                        + "\",\"enable\":1}"), token);
        assertEquals("A0400", parse(unknownModality).path("code").asText(),
                "a modality absent from dict 1027 must reject A0400: " + unknownModality);

        // The same code IS legal in another modality - scoping is per modality.
        ResponseEntity<String> otherModality = exchangeJson(HttpMethod.POST, METHODS_URL,
                body("{\"modality\":\"US\",\"name\":\"ut_exm_对照_" + ts
                        + "\",\"code\":\"ut_exm_" + ts + "_dup\",\"enable\":1}"), token);
        assertEquals("200", parse(otherModality).path("code").asText(),
                "the same code in another modality must be accepted: " + otherModality);
        int usMethod = parse(otherModality).path("data").asInt(-1);

        hardDeleteMethod(method);
        hardDeleteMethod(usMethod);
    }

    @Test
    void methodDeleteIsGuardedByItemBindings() throws Exception {
        String token = fullPermToken();
        long ts = System.currentTimeMillis();
        int method = createMethod("CT", "ut_exm_守卫_" + ts, "ut_exm_" + ts + "_g", 1);
        int examId = jdbcTemplate.queryForObject(
                "INSERT INTO system.exam_item (modality, exam, code, body_id, enable) "
                        + "VALUES ('CT', 'ut_exi_方法守卫项目_" + ts + "', 'ut_exi_" + ts
                        + "_m', (SELECT COALESCE(MAX(id), 1) FROM system.exam_body), 1) "
                        + "RETURNING id", Integer.class);
        jdbcTemplate.update("INSERT INTO system.exam_item_method (exam_id, method_id) "
                + "VALUES (?, ?)", examId, method);

        ResponseEntity<String> blocked = exchangeJson(HttpMethod.DELETE,
                METHODS_URL + "/" + method, null, token);
        JsonNode blockedBody = parse(blocked);
        assertEquals("A0400", blockedBody.path("code").asText(),
                "a method bound to an item must refuse deletion: " + blocked);
        assertTrue(blockedBody.path("msg").asText().contains("项目"),
                "the refusal must name the item reference: " + blockedBody);

        jdbcTemplate.update("DELETE FROM system.exam_item_method WHERE exam_id = ?", examId);
        jdbcTemplate.update("DELETE FROM system.exam_item WHERE id = ?", examId);
        ResponseEntity<String> freed = exchangeJson(HttpMethod.DELETE,
                METHODS_URL + "/" + method, null, token);
        assertEquals("200", parse(freed).path("code").asText(),
                "deleting the last binding must free the method: " + freed);
    }

    // ===================== fixture + call helpers =====================

    private int createMethod(String modality, String name, String code, int sort)
            throws Exception {
        ResponseEntity<String> created = exchangeJson(HttpMethod.POST, METHODS_URL,
                body("{\"modality\":\"" + modality + "\",\"name\":\"" + name
                        + "\",\"code\":\"" + code + "\",\"sort\":" + sort + ",\"enable\":1}"),
                fullPermToken());
        JsonNode createdBody = parse(created);
        assertEquals("200", createdBody.path("code").asText(),
                "the fixture method must create cleanly: " + created);
        return createdBody.path("data").asInt(-1);
    }

    private int indexOfCode(JsonNode rows, String code) {
        for (int i = 0; i < rows.size(); i++) {
            if (code.equals(rows.get(i).path("code").asText())) {
                return i;
            }
        }
        return -1;
    }

    private void hardDeleteMethod(int id) {
        jdbcTemplate.update("DELETE FROM system.exam_item_method WHERE method_id = ?", id);
        jdbcTemplate.update("DELETE FROM system.exam_method WHERE id = ?", id);
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
