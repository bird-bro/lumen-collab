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
 * Normal-range v2 union proof (rep-temp-normal-range-sev task 2; spec delta
 * exam-setting "Structured Template Content" v2): the tagged-union normal round-trips
 * verbatim at version 2 (two-sided / single-sided ranges with decimals, the text
 * variant), and every rule violation rejects A0400 naming the field - inverted
 * bounds, both-ends-null, blank or over-64 text, an unknown kind, a v1-shaped write
 * (version 1 or a plain-string normal). Timestamped fixtures, hard-deleted afterwards
 * (user ruling 2026-11-07 - the 「ut模板」 prefix family is already sweep-registered).
 *
 * @author birdbro
 */
class RepTempNormalRangeApiTests extends SettingIntegrationTestBase {

    private static final String TEMP_URL = "/api/v1/setting/rep-temps";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void unionNormalRoundTripsVerbatimAtVersion2() throws Exception {
        long ts = System.currentTimeMillis();
        String token = fullPermToken();
        int folder = createTemp(token, "ut模板目录_v2区间_" + ts);
        // The v2 union verbatim: two-sided decimal range + both single-sided shapes
        // (null ends stay PRESENT keys) + the text variant + a null normal.
        String content = "{\"version\":2,\"sections\":[{\"name\":\"心脏\",\"finding\":\"节内所见\","
                + "\"measures\":["
                + "{\"code\":\"AO\",\"label\":\"主动脉根部内径\",\"unit\":\"mm\","
                + "\"normal\":{\"kind\":\"range\",\"min\":19.5,\"max\":40}},"
                + "{\"code\":\"LAD\",\"label\":\"冠状动脉左前降支\",\"unit\":null,"
                + "\"normal\":{\"kind\":\"range\",\"min\":null,\"max\":5}},"
                + "{\"code\":\"LV\",\"label\":\"左室内径\",\"unit\":null,"
                + "\"normal\":{\"kind\":\"range\",\"min\":3,\"max\":null}},"
                + "{\"code\":\"MR\",\"label\":\"二尖瓣反流\",\"unit\":null,"
                + "\"normal\":{\"kind\":\"text\",\"text\":\"阴性\"}},"
                + "{\"code\":\"PE\",\"label\":\"心包积液\",\"unit\":null,\"normal\":null}"
                + "]}],\"impression\":\"超声影像学意见\"}";
        int temp = createTempWithContent(token, "ut模板_v2区间_" + ts, folder, content);

        // Stored row: REAL jsonb object, prose pair NULL (D1 unchanged), version 2.
        JsonNode round = pageContent(token, folder, temp);
        assertEquals(2, round.path("version").asInt(-1), "the stored version must be 2");
        assertEquals("超声影像学意见", round.path("impression").asText(),
                "impression must round-trip verbatim");
        JsonNode measures = round.path("sections").get(0).path("measures");
        JsonNode range = measures.get(0).path("normal");
        assertEquals("range", range.path("kind").asText(), "the two-sided range kind");
        assertEquals(19.5, range.path("min").asDouble(-1), 1e-9, "decimal min round-trips");
        assertEquals(40, range.path("max").asInt(-1), "two-sided max round-trips");
        JsonNode maxOnly = measures.get(1).path("normal");
        assertTrue(maxOnly.path("min").isNull(), "a null end stays null");
        assertEquals(5, maxOnly.path("max").asInt(-1), "the max-only end round-trips");
        JsonNode minOnly = measures.get(2).path("normal");
        assertEquals(3, minOnly.path("min").asInt(-1), "the min-only end round-trips");
        assertTrue(minOnly.path("max").isNull(), "a null end stays null");
        JsonNode text = measures.get(3).path("normal");
        assertEquals("text", text.path("kind").asText(), "the text variant kind");
        assertEquals("阴性", text.path("text").asText(), "the text variant round-trips");
        assertTrue(measures.get(4).path("normal").isNull(),
                "a null normal stays null (no variant wrapper)");

        hardDelete(temp);
        hardDelete(folder);
    }

    @Test
    void rangeRuleViolationsRejectNamingTheField() throws Exception {
        long ts = System.currentTimeMillis();
        String token = fullPermToken();
        int folder = createTemp(token, "ut模板目录_v2拒区_" + ts);

        // Inverted bounds: min > max.
        ResponseEntity<String> inverted = createContent(token, "ut模板_v2倒序_" + ts, folder,
                "{\"version\":2,\"sections\":[{\"name\":\"心脏\",\"measures\":["
                        + "{\"code\":\"AO\",\"label\":\"主动脉\",\"normal\":"
                        + "{\"kind\":\"range\",\"min\":40,\"max\":19}}]}]}");
        assertEquals("A0400", parse(inverted).path("code").asText(),
                "min > max must reject: " + inverted);
        assertTrue(parse(inverted).path("msg").asText().contains("正常范围最小值不能大于最大值"),
                "the rejection must name the rule: " + inverted);

        // Both ends null.
        ResponseEntity<String> empty = createContent(token, "ut模板_v2空区_" + ts, folder,
                "{\"version\":2,\"sections\":[{\"name\":\"心脏\",\"measures\":["
                        + "{\"code\":\"AO\",\"label\":\"主动脉\",\"normal\":"
                        + "{\"kind\":\"range\",\"min\":null,\"max\":null}}]}]}");
        assertEquals("A0400", parse(empty).path("code").asText(),
                "both-ends-null must reject: " + empty);
        assertTrue(parse(empty).path("msg").asText().contains("正常范围区间两端不能同时为空"),
                "the rejection must name the rule: " + empty);

        hardDelete(folder);
    }

    @Test
    void textAndKindViolationsRejectNamingTheField() throws Exception {
        long ts = System.currentTimeMillis();
        String token = fullPermToken();
        int folder = createTemp(token, "ut模板目录_v2拒文_" + ts);

        // Blank text.
        ResponseEntity<String> blank = createContent(token, "ut模板_v2空文_" + ts, folder,
                "{\"version\":2,\"sections\":[{\"name\":\"心脏\",\"measures\":["
                        + "{\"code\":\"AO\",\"label\":\"主动脉\",\"normal\":"
                        + "{\"kind\":\"text\",\"text\":\"   \"}}]}]}");
        assertEquals("A0400", parse(blank).path("code").asText(),
                "blank text must reject: " + blank);
        assertTrue(parse(blank).path("msg").asText().contains("正常范围文本不能为空"),
                "the rejection must name the rule: " + blank);

        // Over-64 text.
        String longText = "长".repeat(65);
        ResponseEntity<String> tooLong = createContent(token, "ut模板_v2长文_" + ts, folder,
                "{\"version\":2,\"sections\":[{\"name\":\"心脏\",\"measures\":["
                        + "{\"code\":\"AO\",\"label\":\"主动脉\",\"normal\":"
                        + "{\"kind\":\"text\",\"text\":\"" + longText + "\"}}]}]}");
        assertEquals("A0400", parse(tooLong).path("code").asText(),
                "over-64 text must reject: " + tooLong);
        assertTrue(parse(tooLong).path("msg").asText().contains("正常范围文本长度不能超过 64"),
                "the rejection must name the rule: " + tooLong);

        // Unknown kind.
        ResponseEntity<String> badKind = createContent(token, "ut模板_v2错类_" + ts, folder,
                "{\"version\":2,\"sections\":[{\"name\":\"心脏\",\"measures\":["
                        + "{\"code\":\"AO\",\"label\":\"主动脉\",\"normal\":"
                        + "{\"kind\":\"interval\",\"min\":1,\"max\":2}}]}]}");
        assertEquals("A0400", parse(badKind).path("code").asText(),
                "an unknown kind must reject: " + badKind);
        assertTrue(parse(badKind).path("msg").asText().contains("正常范围 kind"),
                "the rejection must name the rule: " + badKind);

        hardDelete(folder);
    }

    @Test
    void v1ShapedWritesAreRejected() throws Exception {
        long ts = System.currentTimeMillis();
        String token = fullPermToken();
        int folder = createTemp(token, "ut模板目录_v2拒v1_" + ts);

        // version 1 (the migrated-away shape) - the service must refuse it. The body
        // binds cleanly (null normal) so the A0400 comes from the version pin itself.
        ResponseEntity<String> v1 = createContent(token, "ut模板_v2v1版_" + ts, folder,
                "{\"version\":1,\"sections\":[{\"name\":\"心脏\",\"measures\":["
                        + "{\"code\":\"AO\",\"label\":\"主动脉\",\"normal\":null}]}]}");
        assertEquals("A0400", parse(v1).path("code").asText(),
                "a v1-shaped version must reject: " + v1);
        assertTrue(parse(v1).path("msg").asText().contains("content.version"),
                "the rejection must name the field: " + v1);
        Integer rows1 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.rep_temp WHERE name LIKE 'ut模板_v2v1版_%'",
                Integer.class);
        assertEquals(0, rows1.intValue(), "no row may land for a v1 version");

        // version 3 - also outside the pinned version.
        ResponseEntity<String> v3 = createContent(token, "ut模板_v2v3版_" + ts, folder,
                "{\"version\":3,\"sections\":[{\"name\":\"心脏\",\"measures\":["
                        + "{\"code\":\"AO\",\"label\":\"主动脉\",\"normal\":"
                        + "{\"kind\":\"range\",\"min\":1,\"max\":2}}]}]}");
        assertEquals("A0400", parse(v3).path("code").asText(),
                "a version other than 2 must reject: " + v3);
        assertTrue(parse(v3).path("msg").asText().contains("content.version"),
                "the rejection must name the field: " + v3);

        // Plain-string normal (the v1 union shape) - Jackson binding refuses the
        // string into the union object BEFORE the service layer (A0427 request-parse
        // family); no row may land.
        ResponseEntity<String> stringNormal = createContent(token, "ut模板_v2串形_" + ts, folder,
                "{\"version\":2,\"sections\":[{\"name\":\"心脏\",\"measures\":["
                        + "{\"code\":\"AO\",\"label\":\"主动脉\",\"normal\":\"19-40\"}]}]}");
        assertTrue(!"200".equals(parse(stringNormal).path("code").asText()),
                "a plain-string normal must be rejected at binding: " + stringNormal);
        Integer rows2 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.rep_temp WHERE name LIKE 'ut模板_v2串形_%'",
                Integer.class);
        assertEquals(0, rows2.intValue(), "no row may land for a string normal");

        hardDelete(folder);
    }

    // ===================== fixture + call helpers =====================

    private int createTemp(String token, String name) throws Exception {
        ResponseEntity<String> created = exchangeJson(HttpMethod.POST, TEMP_URL,
                body("{\"type\":1,\"modality\":\"CT\",\"flag\":0,\"name\":\"" + name + "\"}"), token);
        JsonNode createdBody = parse(created);
        assertEquals("200", createdBody.path("code").asText(),
                "the fixture folder must create cleanly: " + created);
        return createdBody.path("data").asInt(-1);
    }

    private ResponseEntity<String> createContent(String token, String name, int upId,
            String content) throws Exception {
        return exchangeJson(HttpMethod.POST, TEMP_URL,
                body("{\"type\":1,\"modality\":\"CT\",\"flag\":1,\"upId\":" + upId
                        + ",\"name\":\"" + name + "\",\"content\":" + content + "}"), token);
    }

    private int createTempWithContent(String token, String name, int upId,
            String content) throws Exception {
        ResponseEntity<String> created = createContent(token, name, upId, content);
        JsonNode createdBody = parse(created);
        assertEquals("200", createdBody.path("code").asText(),
                "the fixture template must create cleanly: " + created);
        return createdBody.path("data").asInt(-1);
    }

    private JsonNode pageContent(String token, int folder, int temp) throws Exception {
        JsonNode page = parse(exchangeJson(HttpMethod.GET,
                TEMP_URL + "/page?modality=CT&type=1&upId=" + folder, null, token));
        assertEquals(1, page.path("data").path("total").asLong(-1),
                "the fixture template must be the page's only row: " + page);
        JsonNode row = page.path("data").path("records").get(0);
        assertEquals(temp, row.path("id").asInt(-1), "the fixture row id must match");
        return row.path("content");
    }

    private void hardDelete(int id) {
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
