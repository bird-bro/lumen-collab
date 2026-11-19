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
 *
 * <p>Batch 2 closes the review carry-overs (M1/M3): the structured
 * update-to-page round-trip over the wrapper typeHandler-mapping write path, the
 * update-route rejections, the explicit {@code impression: null} round-trip
 * (present-as-null, never an absent key), and the empty-measures-array edge.</p>
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
        String content = "{\"version\":2,\"sections\":[{"
                + "\"name\":\"心脏\",\"finding\":\"节内所见文本\",\"measures\":["
                + "{\"code\":\"AO\",\"label\":\"主动脉根部内径\",\"unit\":\"mm\",\"normal\":{\"kind\":\"range\",\"min\":21,\"max\":37}},"
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
        assertEquals(2, round.path("version").asInt(-1), "version must round-trip");
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
        assertEquals("range", heart.path("measures").get(0).path("normal").path("kind").asText(),
                "the v2 union kind must round-trip");
        assertEquals(0, java.math.BigDecimal.valueOf(heart.path("measures").get(0)
                .path("normal").path("min").asDouble(-1))
                .compareTo(java.math.BigDecimal.valueOf(21)),
                "the v2 range min must round-trip");
        assertEquals(0, java.math.BigDecimal.valueOf(heart.path("measures").get(0)
                .path("normal").path("max").asDouble(-1))
                .compareTo(java.math.BigDecimal.valueOf(37)),
                "the v2 range max must round-trip");
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
                        "{\"version\":2,\"sections\":[{\"name\":\"  \",\"measures\":[]}]}")),
                token);
        assertEquals("A0400", parse(blankName).path("code").asText(),
                "a blank section name must reject: " + blankName);
        assertTrue(parse(blankName).path("msg").asText().contains("name"),
                "the rejection must name the field: " + blankName);

        // Blank measure code.
        ResponseEntity<String> blankCode = exchangeJson(HttpMethod.POST, TEMP_URL,
                body(templateBody("ut模板_空码_" + ts, folder,
                        "{\"version\":2,\"sections\":[{\"name\":\"心脏\",\"measures\":["
                                + "{\"code\":\"\",\"label\":\"标签\"}]}]}")),
                token);
        assertEquals("A0400", parse(blankCode).path("code").asText(),
                "a blank measure code must reject: " + blankCode);
        assertTrue(parse(blankCode).path("msg").asText().contains("code"),
                "the rejection must name the field: " + blankCode);

        // Blank measure label.
        ResponseEntity<String> blankLabel = exchangeJson(HttpMethod.POST, TEMP_URL,
                body(templateBody("ut模板_空标_" + ts, folder,
                        "{\"version\":2,\"sections\":[{\"name\":\"心脏\",\"measures\":["
                                + "{\"code\":\"AO\",\"label\":\" \"}]}]}")),
                token);
        assertEquals("A0400", parse(blankLabel).path("code").asText(),
                "a blank measure label must reject: " + blankLabel);
        assertTrue(parse(blankLabel).path("msg").asText().contains("label"),
                "the rejection must name the field: " + blankLabel);

        // Empty sections array.
        ResponseEntity<String> noSections = exchangeJson(HttpMethod.POST, TEMP_URL,
                body(templateBody("ut模板_空节_" + ts, folder,
                        "{\"version\":2,\"sections\":[]}")),
                token);
        assertEquals("A0400", parse(noSections).path("code").asText(),
                "empty sections must reject: " + noSections);
        assertTrue(parse(noSections).path("msg").asText().contains("content.sections"),
                "the rejection must name the field: " + noSections);

        // version other than 1 (service-layer assert).
        ResponseEntity<String> wrongVersion = exchangeJson(HttpMethod.POST, TEMP_URL,
                body(templateBody("ut模板_错版_" + ts, folder,
                        "{\"version\":3,\"sections\":[{\"name\":\"心脏\"}]}")),
                token);
        assertEquals("A0400", parse(wrongVersion).path("code").asText(),
                "a version other than 2 must reject: " + wrongVersion);
        assertTrue(parse(wrongVersion).path("msg").asText().contains("content.version"),
                "the rejection must name the field: " + wrongVersion);

        // A directory node (flag=0) targeting content (D8).
        ResponseEntity<String> dirContent = exchangeJson(HttpMethod.POST, TEMP_URL,
                body("{\"type\":1,\"modality\":\"CT\",\"flag\":0,\"name\":\"ut模板目录_带节_"
                        + ts + "\",\"content\":{\"version\":2,"
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
                "{\"version\":2,\"sections\":[{\"name\":\"心脏\",\"measures\":[]}]}");
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

    @Test
    void structuredUpdateRoundTripsContentThroughWrapperMapping() throws Exception {
        long ts = System.currentTimeMillis();
        String token = fullPermToken();
        int folder = createTemp(token, "ut模板目录_改节_" + ts, null);
        int temp = createTempWithContent(token, "ut模板_改节_" + ts, folder,
                "{\"version\":2,\"sections\":[{\"name\":\"心脏\",\"measures\":[]}]}");

        // M1: the update-with-content branch is the ONLY wrapper
        // typeHandler-mapping jsonb write route - it must land the NEW content as
        // a REAL jsonb object (a failed mapping resolution would silently
        // double-encode it into jsonb_typeof='string'), with the prose pair NULL.
        ResponseEntity<String> updated = exchangeJson(HttpMethod.PUT,
                TEMP_URL + "/" + temp,
                body(updateBody("ut模板_改节二_" + ts, folder,
                        "{\"version\":2,\"sections\":[{"
                                + "\"name\":\"胃\",\"finding\":\"镜下所见文本\",\"measures\":["
                                + "{\"code\":\"ULO\",\"label\":\"溃疡面积\",\"unit\":\"mm2\","
                                + "\"normal\":null}]}],\"impression\":\"内镜影像学意见\"}")),
                token);
        assertEquals("200", parse(updated).path("code").asText(),
                "the structured update must succeed: " + updated);

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT finding, impression, jsonb_typeof(content) AS content_type "
                        + "FROM system.rep_temp WHERE id = ?", temp);
        assertNull(stored.get("finding"), "the update must keep finding NULL (D1)");
        assertNull(stored.get("impression"), "the update must keep impression NULL (D1)");
        assertEquals("object", stored.get("content_type"),
                "the wrapper-mapped update must store a REAL jsonb object, not a quoted scalar");

        // The page payload must carry the NEW content - the update really wrote.
        JsonNode page = parse(exchangeJson(HttpMethod.GET,
                TEMP_URL + "/page?modality=CT&type=1&upId=" + folder, null, token));
        assertEquals(1, page.path("data").path("total").asLong(-1),
                "the fixture template must be the page's only row: " + page);
        JsonNode row = page.path("data").path("records").get(0);
        assertTrue(row.path("finding").isNull(),
                "the page payload must not surface prose finding on the updated row");
        assertTrue(row.path("impression").isNull(),
                "the page payload must not surface prose impression on the updated row");
        JsonNode round = row.path("content");
        assertEquals(2, round.path("version").asInt(-1), "version must round-trip");
        assertEquals("内镜影像学意见", round.path("impression").asText(),
                "the NEW impression must round-trip");
        assertEquals(1, round.path("sections").size(), "the new section must round-trip");
        JsonNode stomach = round.path("sections").get(0);
        assertEquals("胃", stomach.path("name").asText(),
                "the NEW section name must round-trip");
        assertEquals("镜下所见文本", stomach.path("finding").asText(),
                "the section finding must round-trip");
        assertEquals(1, stomach.path("measures").size(), "the measure must round-trip");
        assertEquals("ULO", stomach.path("measures").get(0).path("code").asText());
        assertEquals("溃疡面积", stomach.path("measures").get(0).path("label").asText());
        assertEquals("mm2", stomach.path("measures").get(0).path("unit").asText());
        assertTrue(stomach.path("measures").get(0).path("normal").isNull(),
                "a null measure normal must round-trip as null");

        hardDeleteTemp(temp);
        hardDeleteTemp(folder);
    }

    @Test
    void updatePathRejectsInvalidContentNamingTheField() throws Exception {
        long ts = System.currentTimeMillis();
        String token = fullPermToken();
        int folder = createTemp(token, "ut模板目录_拒改_" + ts, null);
        int temp = createTempWithContent(token, "ut模板_拒改_" + ts, folder,
                "{\"version\":2,\"sections\":[{\"name\":\"心脏\",\"measures\":[]}]}");

        // Blank section name (Bean Validation cascade, PUT route).
        ResponseEntity<String> blankName = exchangeJson(HttpMethod.PUT,
                TEMP_URL + "/" + temp,
                body(updateBody("ut模板_拒改名_" + ts, folder,
                        "{\"version\":2,\"sections\":[{\"name\":\" \",\"measures\":[]}]}")),
                token);
        assertEquals("A0400", parse(blankName).path("code").asText(),
                "a blank section name must reject on update: " + blankName);
        assertTrue(parse(blankName).path("msg").asText().contains("name"),
                "the rejection must name the field: " + blankName);

        // Blank measure code.
        ResponseEntity<String> blankCode = exchangeJson(HttpMethod.PUT,
                TEMP_URL + "/" + temp,
                body(updateBody("ut模板_拒改码_" + ts, folder,
                        "{\"version\":2,\"sections\":[{\"name\":\"心脏\",\"measures\":["
                                + "{\"code\":\"\",\"label\":\"标签\"}]}]}")),
                token);
        assertEquals("A0400", parse(blankCode).path("code").asText(),
                "a blank measure code must reject on update: " + blankCode);
        assertTrue(parse(blankCode).path("msg").asText().contains("code"),
                "the rejection must name the field: " + blankCode);

        // Blank measure label.
        ResponseEntity<String> blankLabel = exchangeJson(HttpMethod.PUT,
                TEMP_URL + "/" + temp,
                body(updateBody("ut模板_拒改标_" + ts, folder,
                        "{\"version\":2,\"sections\":[{\"name\":\"心脏\",\"measures\":["
                                + "{\"code\":\"AO\",\"label\":\" \"}]}]}")),
                token);
        assertEquals("A0400", parse(blankLabel).path("code").asText(),
                "a blank measure label must reject on update: " + blankLabel);
        assertTrue(parse(blankLabel).path("msg").asText().contains("label"),
                "the rejection must name the field: " + blankLabel);

        // version other than 2 (service-layer assert, PUT route).
        ResponseEntity<String> wrongVersion = exchangeJson(HttpMethod.PUT,
                TEMP_URL + "/" + temp,
                body(updateBody("ut模板_拒改版_" + ts, folder,
                        "{\"version\":3,\"sections\":[{\"name\":\"心脏\"}]}")),
                token);
        assertEquals("A0400", parse(wrongVersion).path("code").asText(),
                "a version other than 2 must reject on update: " + wrongVersion);
        assertTrue(parse(wrongVersion).path("msg").asText().contains("content.version"),
                "the rejection must name the field: " + wrongVersion);

        // A directory node (flag=0) carrying content through update (D8, PUT route).
        ResponseEntity<String> dirContent = exchangeJson(HttpMethod.PUT,
                TEMP_URL + "/" + folder,
                body("{\"modality\":\"CT\",\"flag\":0,\"name\":\"ut模板目录_拒改节_"
                        + ts + "\",\"content\":{\"version\":2,"
                        + "\"sections\":[{\"name\":\"心脏\"}]}}"),
                token);
        assertEquals("A0400", parse(dirContent).path("code").asText(),
                "a directory node must not carry content on update (D8): " + dirContent);
        assertTrue(parse(dirContent).path("msg").asText().contains("content"),
                "the rejection must name the field: " + dirContent);

        hardDeleteTemp(temp);
        hardDeleteTemp(folder);
    }

    @Test
    void explicitNullImpressionRoundTripsAsJsonNull() throws Exception {
        long ts = System.currentTimeMillis();
        String token = fullPermToken();
        int folder = createTemp(token, "ut模板目录_空议_" + ts, null);
        int temp = createTempWithContent(token, "ut模板_空议_" + ts, folder,
                "{\"version\":2,\"sections\":[{\"name\":\"食道\",\"finding\":\"节内所见\"}],"
                        + "\"impression\":null}");

        // M3: an explicit impression:null must round-trip as a PRESENT JSON null -
        // the key stays in the payload, never silently dropped (非缺省).
        JsonNode page = parse(exchangeJson(HttpMethod.GET,
                TEMP_URL + "/page?modality=CT&type=1&upId=" + folder, null, token));
        assertEquals(1, page.path("data").path("total").asLong(-1),
                "the fixture template must be the page's only row: " + page);
        JsonNode content = page.path("data").path("records").get(0).path("content");
        assertTrue(content.has("impression"),
                "impression:null must stay a present key in the page payload: " + content);
        assertTrue(content.path("impression").isNull(),
                "the explicit null impression must round-trip as null");

        // The update route too: replace the content keeping impression null.
        ResponseEntity<String> updated = exchangeJson(HttpMethod.PUT,
                TEMP_URL + "/" + temp,
                body(updateBody("ut模板_空议改_" + ts, folder,
                        "{\"version\":2,\"sections\":[{\"name\":\"胃\",\"finding\":\"镜下所见\"}],"
                                + "\"impression\":null}")),
                token);
        assertEquals("200", parse(updated).path("code").asText(),
                "the impression-null update must succeed: " + updated);
        JsonNode pageAfter = parse(exchangeJson(HttpMethod.GET,
                TEMP_URL + "/page?modality=CT&type=1&upId=" + folder, null, token));
        JsonNode contentAfter = pageAfter.path("data").path("records").get(0).path("content");
        assertTrue(contentAfter.has("impression"),
                "impression:null must stay a present key after the update: " + contentAfter);
        assertTrue(contentAfter.path("impression").isNull(),
                "the explicit null impression must round-trip as null after the update");
        assertEquals("胃", contentAfter.path("sections").get(0).path("name").asText(),
                "the updated section must round-trip");

        hardDeleteTemp(temp);
        hardDeleteTemp(folder);
    }

    @Test
    void emptyMeasuresSectionRoundTripsAsArray() throws Exception {
        long ts = System.currentTimeMillis();
        String token = fullPermToken();
        int folder = createTemp(token, "ut模板目录_空测_" + ts, null);
        // 内镜节形态: an ENDO-style section whose measures list is an EMPTY ARRAY -
        // legal (delta edge), and it must round-trip as [], never collapse to null.
        int temp = createTempWithContent(token, "ut模板_空测_" + ts, folder,
                "{\"version\":2,\"sections\":[{\"name\":\"食道\",\"finding\":\"黏膜光滑\","
                        + "\"measures\":[]}],\"impression\":\"内镜影像学意见\"}");

        JsonNode page = parse(exchangeJson(HttpMethod.GET,
                TEMP_URL + "/page?modality=CT&type=1&upId=" + folder, null, token));
        assertEquals(1, page.path("data").path("total").asLong(-1),
                "the fixture template must be the page's only row: " + page);
        JsonNode measures = page.path("data").path("records").get(0).path("content")
                .path("sections").get(0).path("measures");
        assertTrue(measures.isArray(),
                "an empty measures array must round-trip as an ARRAY, not null: " + measures);
        assertEquals(0, measures.size(), "the measures array must stay empty");

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

    /** PUT body shape (TempUpdateRequest - no {@code type}; scope stays stored). */
    private String updateBody(String name, Integer upId, String content) {
        return "{\"modality\":\"CT\",\"flag\":1,\"name\":\"" + name + "\""
                + (upId != null ? ",\"upId\":" + upId : "")
                + ",\"content\":" + content + "}";
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
