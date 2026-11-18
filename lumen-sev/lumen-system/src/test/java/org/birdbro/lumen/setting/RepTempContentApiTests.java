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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structured section-content proof (rep-template-structured-content batch 1; spec
 * deltas exam-setting "Structured Template Content" + "Report Template Columns" /
 * "Report Template Endpoints"): the page round-trip of the D2 shape with the D1
 * exclusivity in the STORED row (content is a REAL jsonb object, the prose pair
 * NULL), the D3/D8 field-naming rejections, the full-replace null conversion, and
 * the tree staying content-free. Timestamped fixtures, hard-deleted afterwards
 * (user ruling 2026-11-07 - the "ut模板" prefix family is already sweep-registered).
 */
class RepTempContentApiTests extends SettingIntegrationTestBase {

    private static final String TEMP_URL = "/api/v1/setting/rep-temps";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void structuredCreateRoundTripsContentAndNullsProseColumns() throws Exception {
        long ts = System.currentTimeMillis();
        String token = fullPermToken();
        int folder = createTemp(token, "ut模板目录_分节_" + ts, null);
        // The D2 shape verbatim: one measures-bearing section + a bare section -
        // nulls (finding/measures) must round-trip as null, never become [] or "".
        String content = "{\"version\":1,\"sections\":[{"
                + "\"name\":\"心脏\",\"finding\":\"节内所见文本\",\"measures\":["
                + "{\"code\":\"AO\",\"label\":\"主动脉根部内径\",\"unit\":\"mm\",\"normal\":\"21-37\"},"
                + "{\"code\":\"LAD\",\"label\":\"冠状动脉左前降支\",\"unit\":null,\"normal\":null}]},"
                + "{\"name\":\"心包\",\"finding\":null,\"measures\":null}],"
                + "\"impression\":\"超声影像学意见\"}";
        int temp = createTempWithContent(token, "ut模板_分节_" + ts, folder, content);

        // D1 in the STORED row: REAL jsonb object (not a double-encoded string),
        // the prose pair NULL.
        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT finding, impression, jsonb_typeof(content) AS content_type "
                        + "FROM system.rep_temp WHERE id = ?", temp);
        assertNull(stored.get("finding"), "content row must store finding NULL (D1)");
        assertNull(stored.get("impression"), "content row must store impression NULL (D1)");
        assertEquals("object", stored.get("content_type"),
                "content must land as a REAL jsonb object, not a quoted scalar");

        // The page payload round-trips the content object verbatim.
        JsonNode page = parse(exchangeJson(HttpMethod.GET,
                TEMP_URL + "/page?modality=CT&type=1&upId=" + folder, null, token));
        assertEquals(1, page.path("data").path("total").asLong(-1),
                "the fixture template must be the page's only row: " + page);
        JsonNode row = page.path("data").path("records").get(0);
        assertTrue(row.path("finding").isNull(),
                "the page payload must not surface prose finding on a content row");
        assertTrue(row.path("impression").isNull(),
                "the page payload must not surface prose impression on a content row");
        JsonNode round = row.path("content");
        assertEquals(1, round.path("version").asInt(-1), "version must round-trip");
        assertEquals("超声影像学意见", round.path("impression").asText(),
                "impression must round-trip verbatim");
        assertEquals(2, round.path("sections").size(), "both sections must round-trip");
        JsonNode heart = round.path("sections").get(0);
        assertEquals("心脏", heart.path("name").asText(), "section name must round-trip");
        assertEquals("节内所见文本", heart.path("finding").asText(), "section finding must round-trip");
        assertEquals(2, heart.path("measures").size(), "both measures must round-trip");
        assertEquals("AO", heart.path("measures").get(0).path("code").asText());
        assertEquals("主动脉根部内径", heart.path("measures").get(0).path("label").asText());
        assertEquals("mm", heart.path("measures").get(0).path("unit").asText());
        assertEquals("21-37", heart.path("measures").get(0).path("normal").asText());
        assertTrue(heart.path("measures").get(1).path("unit").isNull(),
                "a null measure unit must round-trip as null");
        assertTrue(heart.path("measures").get(1).path("normal").isNull(),
                "a null measure normal must round-trip as null");
        JsonNode bare = round.path("sections").get(1);
        assertEquals("心包", bare.path("name").asText(), "the bare section must round-trip");
        assertTrue(bare.path("finding").isNull(), "a null section finding stays null");
        assertTrue(bare.path("measures").isNull(), "a null measures list stays null (D2)");

        // The tree must stay content-free even with a structured row in scope.
        JsonNode tree = parse(exchangeJson(HttpMethod.GET,
                TEMP_URL + "/tree?modality=CT&type=1", null, token));
        assertTrue(!tree.path("data").toString().contains("content"),
                "tree nodes must not carry the content object");

        hardDeleteTemp(temp);
        hardDeleteTemp(folder);
    }

    @Test
    void invalidContentIsRejectedNamingTheField() throws Exception {
        long ts = System.currentTimeMillis();
        String token = fullPermToken();
        int folder = createTemp(token, "ut模板目录_拒节_" + ts, null);

        // Blank section name (Bean Validation cascade into the section).
        ResponseEntity<String> blankName = exchangeJson(HttpMethod.POST, TEMP_URL,
                body(templateBody("ut模板_空节名_" + ts, folder,
                        "{\"version\":1,\"sections\":[{\"name\":\"  \",\"measures\":[]}]}")),
                token);
        assertEquals("A0400", parse(blankName).path("code").asText(),
                "a blank section name must reject: " + blankName);
        assertTrue(parse(blankName).path("msg").asText().contains("name"),
                "the rejection must name the field: " + blankName);

        // Blank measure code.
        ResponseEntity<String> blankCode = exchangeJson(HttpMethod.POST, TEMP_URL,
                body(templateBody("ut模板_空码_" + ts, folder,
                        "{\"version\":1,\"sections\":[{\"name\":\"心脏\",\"measures\":["
                                + "{\"code\":\"\",\"label\":\"标签\"}]}]}")),
                token);
        assertEquals("A0400", parse(blankCode).path("code").asText(),
                "a blank measure code must reject: " + blankCode);
        assertTrue(parse(blankCode).path("msg").asText().contains("code"),
                "the rejection must name the field: " + blankCode);

        // Blank measure label.
        ResponseEntity<String> blankLabel = exchangeJson(HttpMethod.POST, TEMP_URL,
                body(templateBody("ut模板_空标_" + ts, folder,
                        "{\"version\":1,\"sections\":[{\"name\":\"心脏\",\"measures\":["
                                + "{\"code\":\"AO\",\"label\":\" \"}]}]}")),
                token);
        assertEquals("A0400", parse(blankLabel).path("code").asText(),
                "a blank measure label must reject: " + blankLabel);
        assertTrue(parse(blankLabel).path("msg").asText().contains("label"),
                "the rejection must name the field: " + blankLabel);

        // Empty sections array.
        ResponseEntity<String> noSections = exchangeJson(HttpMethod.POST, TEMP_URL,
                body(templateBody("ut模板_空节_" + ts, folder,
                        "{\"version\":1,\"sections\":[]}")),
                token);
        assertEquals("A0400", parse(noSections).path("code").asText(),
                "empty sections must reject: " + noSections);
        assertTrue(parse(noSections).path("msg").asText().contains("content.sections"),
                "the rejection must name the field: " + noSections);

        // version other than 1 (service-layer assert).
        ResponseEntity<String> wrongVersion = exchangeJson(HttpMethod.POST, TEMP_URL,
                body(templateBody("ut模板_错版_" + ts, folder,
                        "{\"version\":2,\"sections\":[{\"name\":\"心脏\"}]}")),
                token);
        assertEquals("A0400", parse(wrongVersion).path("code").asText(),
                "version != 1 must reject: " + wrongVersion);
        assertTrue(parse(wrongVersion).path("msg").asText().contains("content.version"),
                "the rejection must name the field: " + wrongVersion);

        // A directory node (flag=0) targeting content (D8).
        ResponseEntity<String> dirContent = exchangeJson(HttpMethod.POST, TEMP_URL,
                body("{\"type\":1,\"modality\":\"CT\",\"flag\":0,\"name\":\"ut模板目录_带节_"
                        + ts + "\",\"content\":{\"version\":1,"
                        + "\"sections\":[{\"name\":\"心脏\"}]}}"),
                token);
        assertEquals("A0400", parse(dirContent).path("code").asText(),
                "a directory node must not carry content (D8): " + dirContent);
        assertTrue(parse(dirContent).path("msg").asText().contains("content"),
                "the rejection must name the field: " + dirContent);

        hardDeleteTemp(folder);
    }

    @Test
    void nullContentUpdateConvertsStructuredToProse() throws Exception {
        long ts = System.currentTimeMillis();
        String token = fullPermToken();
        int folder = createTemp(token, "ut模板目录_转散文_" + ts, null);
        int temp = createTempWithContent(token, "ut模板_转散文_" + ts, folder,
                "{\"version\":1,\"sections\":[{\"name\":\"心脏\",\"measures\":[]}]}");
        assertEquals("object", jdbcTemplate.queryForObject(
                "SELECT jsonb_typeof(content) FROM system.rep_temp WHERE id = ?",
                String.class, temp), "the fixture must start structured");

        // Full-replace with EXPLICIT null content: structured -> prose (D9;
        // sections are dropped - destructive, spec-declared).
        ResponseEntity<String> updated = exchangeJson(HttpMethod.PUT,
                TEMP_URL + "/" + temp,
                body("{\"modality\":\"CT\",\"flag\":1,\"upId\":" + folder
                        + ",\"name\":\"ut模板_转散文改_" + ts + "\","
                        + "\"finding\":\"散文表现\",\"impression\":\"散文意见\","
                        + "\"content\":null}"),
                token);
        assertEquals("200", parse(updated).path("code").asText(),
                "the prose conversion update must succeed: " + updated);

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT finding, impression, jsonb_typeof(content) AS content_type "
                        + "FROM system.rep_temp WHERE id = ?", temp);
        assertEquals("散文表现", stored.get("finding"),
                "the prose pair must take over after the conversion");
        assertEquals("散文意见", stored.get("impression"),
                "the prose pair must take over after the conversion");
        assertNull(stored.get("content_type"),
                "content must be NULL after the structured->prose conversion (D1)");

        JsonNode page = parse(exchangeJson(HttpMethod.GET,
                TEMP_URL + "/page?modality=CT&type=1&upId=" + folder, null, token));
        JsonNode row = page.path("data").path("records").get(0);
        assertTrue(row.path("content").isNull(),
                "a prose template's page payload carries content: null");

        hardDeleteTemp(temp);
        hardDeleteTemp(folder);
    }

    // ===================== fixture + call helpers =====================

    private int createTemp(String token, String name, Integer upId) throws Exception {
        ResponseEntity<String> created = exchangeJson(HttpMethod.POST, TEMP_URL,
                body("{\"type\":1,\"modality\":\"CT\",\"flag\":0,\"name\":\"" + name + "\""
                        + (upId != null ? ",\"upId\":" + upId : "") + "}"), token);
        JsonNode createdBody = parse(created);
        assertEquals("200", createdBody.path("code").asText(),
                "the fixture folder must create cleanly: " + created);
        return createdBody.path("data").asInt(-1);
    }

    private int createTempWithContent(String token, String name, Integer upId,
            String content) throws Exception {
        ResponseEntity<String> created = exchangeJson(HttpMethod.POST, TEMP_URL,
                body("{\"type\":1,\"modality\":\"CT\",\"flag\":1,\"upId\":" + upId
                        + ",\"name\":\"" + name + "\",\"content\":" + content + "}"),
                token);
        JsonNode createdBody = parse(created);
        assertEquals("200", createdBody.path("code").asText(),
                "the fixture template must create cleanly: " + created);
        return createdBody.path("data").asInt(-1);
    }

    private String templateBody(String name, int upId, String content) {
        return "{\"type\":1,\"modality\":\"CT\",\"flag\":1,\"upId\":" + upId
                + ",\"name\":\"" + name + "\",\"content\":" + content + "}";
    }

    private void hardDeleteTemp(int id) {
        jdbcTemplate.update("DELETE FROM system.rep_temp WHERE id = ?", id);
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
