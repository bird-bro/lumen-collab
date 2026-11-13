package org.birdbro.lumen.setting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import org.birdbro.lumen.setting.mapper.ExamItemMethodMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item+binding ATOMICITY proof (exam-setting-api task 4.1, spec delta exam-setting
 * "Exam Item Method Binding" scenario "Item update rewrites method bindings atomically"):
 * the item row and its binding rewrite live in ONE transaction - a failure injected
 * mid-rewrite (the binding mapper's insert throws on its SECOND call) must leave BOTH
 * the item and the pre-update binding set at the pre-update state. The real database
 * carries the proof: the first (successful) binding insert of the failed transaction
 * must be ROLLED BACK, not left behind.
 *
 * <p>{@code @MockBean} swaps only the binding mapper; item rows flow through the real
 * mapper - so a partial write is provably recoverable on the shared test server.</p>
 *
 * @author birdbro
 */
class AuthExamItemTransactionTests extends SettingIntegrationTestBase {

    private static final String ITEMS_URL = "/api/v1/setting/exam-items";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ExamItemMethodMapper examItemMethodMapper;

    /** Two methods: the first insert succeeds, the second injects the failure. */
    private int failures;

    @Test
    void failedBindingRewriteRollsBackTheWholeItemUpdate() throws Exception {
        long ts = System.currentTimeMillis();
        int group = createGroup("CT", "ut组_事务_" + ts, 1);
        int body = createBody(group, "CT", "ut_exb_事务部位_" + ts);
        int m1 = createMethod("CT", "ut_exm_甲法_" + ts, "ut_exm_" + ts + "1", 1);
        int m2 = createMethod("CT", "ut_exm_乙法_" + ts, "ut_exm_" + ts + "2", 2);
        int item = createItem(group, body, "ut_exi_事务项目_" + ts, "ut_exi_" + ts, m1);
        Integer priceBefore = jdbcTemplate.queryForObject(
                "SELECT (price IS NULL)::int FROM system.exam_item WHERE id = ?",
                Integer.class, item);
        Integer bindingsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.exam_item_method WHERE exam_id = ?",
                Integer.class, item);

        // Inject: the second binding insert blows up AFTER the first landed.
        Mockito.when(examItemMethodMapper.insert(Mockito.any())).thenAnswer(invocation -> {
            failures++;
            if (failures >= 2) {
                throw new IllegalStateException("injected binding failure (transaction proof)");
            }
            return 1;
        });

        ResponseEntity<String> failed = exchangeJson(HttpMethod.PUT, ITEMS_URL + "/" + item,
                body("{\"modality\":\"CT\",\"bodyId\":" + body + ",\"exam\":\"ut_exi_改项目_"
                        + ts + "\",\"methodIds\":[" + m1 + "," + m2 + "],\"enable\":1}"),
                fullPermToken());
        assertTrue(!"200".equals(parse(failed).path("code").asText()),
                "the injected failure must surface: " + failed);

        // The item row and its bindings are EXACTLY the pre-update state.
        String exam = jdbcTemplate.queryForObject(
                "SELECT exam FROM system.exam_item WHERE id = ?", String.class, item);
        assertEquals("ut_exi_事务项目_" + ts,
                exam, "the failed update must leave the item row untouched");
        Integer bindingsAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.exam_item_method WHERE exam_id = ?",
                Integer.class, item);
        assertEquals(bindingsBefore, bindingsAfter,
                "the failed rewrite must leave the binding set untouched (rolled back)");

        hardDeleteItem(item);
        hardDeleteBody(body);
        hardDeleteGroup(group);
        hardDeleteMethod(m1);
        hardDeleteMethod(m2);
    }

    // ===================== fixture + call helpers =====================

    private int createGroup(String modality, String name, int sort) throws Exception {
        ResponseEntity<String> created = exchangeJson(HttpMethod.POST,
                "/api/v1/setting/exam-body-groups",
                body("{\"modality\":\"" + modality + "\",\"name\":\"" + name
                        + "\",\"sort\":" + sort + ",\"enable\":1}"), fullPermToken());
        JsonNode createdBody = parse(created);
        assertEquals("200", createdBody.path("code").asText(),
                "the fixture group must create cleanly: " + created);
        return createdBody.path("data").asInt(-1);
    }

    private int createBody(int groupId, String modality, String name) throws Exception {
        ResponseEntity<String> created = exchangeJson(HttpMethod.POST,
                "/api/v1/setting/exam-bodies",
                body("{\"modality\":\"" + modality + "\",\"groupId\":" + groupId
                        + ",\"name\":\"" + name + "\",\"enable\":1}"), fullPermToken());
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

    private int createItem(int groupId, int bodyId, String exam, String code, int methodId)
            throws Exception {
        ResponseEntity<String> created = exchangeJson(HttpMethod.POST, ITEMS_URL,
                body("{\"modality\":\"CT\",\"bodyId\":" + bodyId + ",\"exam\":\"" + exam
                        + "\",\"code\":\"" + code + "\",\"methodIds\":[" + methodId
                        + "],\"enable\":1}"), fullPermToken());
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
