package org.birdbro.lumen.auth;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dictionary endpoint proof (system-data tasks 3.1 / 4.2, spec scenarios 1-4): the
 * {@code /api/v1/auth/dicts/catal} + {@code /api/v1/auth/dicts/detail} CRUD driven through
 * the real servlet context with the admin token. Covered:
 *
 * <ol>
 *   <li>catalog create lands in {@code public.dict_catal} with {@code create_by} = the
 *       operator (scenario 1), and a duplicate live code is rejected with the
 *       {@code A0714} envelope, data null (scenario 2);</li>
 *   <li>detail create under a live catalog anchors BOTH {@code ca_id} and {@code ca_code}
 *       (scenario 3) - the ca_code is server-resolved from the catalog, so a stale request
 *       value can never land;</li>
 *   <li>{@code GET detail/list?caCode=} returns ONLY that catalog's live details ordered by
 *       {@code sort} (scenario 4), and a logical delete removes it from the list;</li>
 *   <li>the partial unique index discipline: a logically deleted catalog's code is
 *       reusable (the dead row never blocks re-creating the value).</li>
 * </ol>
 *
 * <p>Shared long-lived database: every code/value carries a timestamp suffix and created
 * rows are logically deleted afterwards.</p>
 *
 * @author birdbro
 */
class AuthDictCrudTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String CATAL_URL = "/api/v1/auth/dicts/catal";
    private static final String DETAIL_URL = "/api/v1/auth/dicts/detail";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void catalCreateLandsRowWithOperatorAuditAndRejectsDuplicateCode() throws Exception {
        String token = adminToken();
        String code = "b2.cat." + System.currentTimeMillis(); // unique - shared DB
        String name = "批量二目录_" + code;

        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, CATAL_URL,
                "{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}", token);
        assertEquals(200, createResponse.getStatusCodeValue(),
                "dict endpoints answer over HTTP 200 (envelope transport)");
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(),
                "the catalog create must succeed: " + createBody);
        int createdId = createBody.path("data").asInt(-1);
        assertTrue(createdId > 2, "the created catalog id must come back past the seeded ids 1/2 (V3 setval)");

        // Scenario 1 verdict against the real table: the row is in public.dict_catal with
        // the operator audit (create_by = admin 1) and the enable column default 1.
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT code, name, enable, deleted, create_by FROM public.dict_catal WHERE id = ?",
                createdId);
        assertEquals(code, row.get("code"), "the catalog code anchor");
        assertEquals(name, row.get("name"), "the catalog display name");
        assertEquals(1, ((Number) row.get("enable")).intValue(),
                "an omitted enable must fall to the lumen 1/0 live default");
        assertEquals(0, ((Number) row.get("deleted")).intValue(), "the new row must be live");
        assertEquals(1, ((Number) row.get("create_by")).intValue(),
                "create_by must be the admin id 1 (MetaObjectConfig fill via the bridge filter)");

        // Scenario 2: the same live code is rejected as a duplicate (A0714, data null).
        ResponseEntity<String> duplicateResponse = exchangeJson(HttpMethod.POST, CATAL_URL,
                "{\"code\":\"" + code + "\",\"name\":\"重复目录_" + code + "\"}", token);
        JsonNode duplicateBody = parse(duplicateResponse);
        assertEquals("A0714", duplicateBody.path("code").asText(),
                "a duplicate live catalog code must map to the A0714 envelope: " + duplicateBody);
        assertFalse(duplicateBody.hasNonNull("data"),
                "the A-class rejection carries data null (fastjson2 omits the null field)");

        // The catalog list endpoint exposes the created row.
        ResponseEntity<String> listResponse = exchangeJson(HttpMethod.GET,
                CATAL_URL + "/list?pageNum=1&pageSize=50&name=" + name, null, token);
        JsonNode listBody = parse(listResponse);
        assertEquals("200", listBody.path("code").asText(), "the catalog list must succeed: " + listBody);
        assertTrue(listBody.path("data").path("records").isArray(),
                "the paginated catalog list must carry a records array");
        boolean found = false;
        for (JsonNode item : listBody.path("data").path("records")) {
            found = found || code.equals(item.path("code").asText());
        }
        assertTrue(found, "the created catalog must appear in the name-filtered list");

        cleanupCatal(createdId);
    }

    @Test
    void detailCreateAnchorsBothCaIdAndCaCodeAndListsByCaCodeSorted() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String code = "b2.det." + ts;

        int catalId = createCatal(token, code, "明细目录_" + ts);

        // Two details under the fresh catalog: the first carries a WRONG ca_code on purpose -
        // the anchor is server-resolved from the live catalog (double-anchor consistency).
        ResponseEntity<String> firstResponse = exchangeJson(HttpMethod.POST, DETAIL_URL,
                "{\"caId\":" + catalId + ",\"caCode\":\"WRONG." + ts + "\",\"value\":\"v2\","
                        + "\"label\":\"乙_" + ts + "\",\"sort\":2}", token);
        JsonNode firstBody = parse(firstResponse);
        assertEquals("200", firstBody.path("code").asText(), "the first detail create must succeed: " + firstBody);
        int firstId = firstBody.path("data").asInt(-1);
        assertTrue(firstId > 2, "the created detail id must come back past the seeded ids 1/2");

        ResponseEntity<String> secondResponse = exchangeJson(HttpMethod.POST, DETAIL_URL,
                "{\"caId\":" + catalId + ",\"value\":\"v1\",\"label\":\"甲_" + ts + "\",\"sort\":1}", token);
        JsonNode secondBody = parse(secondResponse);
        assertEquals("200", secondBody.path("code").asText(), "the second detail create must succeed: " + secondBody);
        int secondId = secondBody.path("data").asInt(-1);

        // Scenario 3: both anchors persisted, and the request's stale ca_code never landed.
        assertEquals(code, jdbcTemplate.queryForObject(
                "SELECT ca_code FROM public.dict_detail WHERE id = ?", String.class, firstId),
                "ca_code must be server-resolved from the live catalog (the WRONG request value must not land)");
        assertEquals(catalId, ((Number) jdbcTemplate.queryForObject(
                "SELECT ca_id FROM public.dict_detail WHERE id = ?", Number.class, firstId)).intValue(),
                "ca_id must anchor the created detail to the catalog");

        // Scenario 4: the by-code list returns ONLY this catalog's live details, sorted by sort.
        ResponseEntity<String> listResponse = exchangeJson(HttpMethod.GET,
                DETAIL_URL + "/list?caCode=" + code, null, token);
        JsonNode listBody = parse(listResponse);
        assertEquals("200", listBody.path("code").asText(), "the by-code detail list must succeed: " + listBody);
        List<String> orderedLabels = new ArrayList<>();
        listBody.path("data").forEach(item -> orderedLabels.add(item.path("label").asText()));
        assertEquals(java.util.Arrays.asList("甲_" + ts, "乙_" + ts), orderedLabels,
                "the list must be the catalog's live details ordered by sort (1 then 2): " + orderedLabels);

        // The (ca_code, value) duplicate is the detail-level uniqueness - rejected A0714.
        ResponseEntity<String> duplicateResponse = exchangeJson(HttpMethod.POST, DETAIL_URL,
                "{\"caId\":" + catalId + ",\"value\":\"v1\",\"label\":\"重复甲_" + ts + "\"}", token);
        JsonNode duplicateBody = parse(duplicateResponse);
        assertEquals("A0714", duplicateBody.path("code").asText(),
                "a duplicate (ca_code, value) detail must map to the A0714 envelope: " + duplicateBody);

        // A detail under an unknown catalog is a request-parameter error.
        ResponseEntity<String> orphanResponse = exchangeJson(HttpMethod.POST, DETAIL_URL,
                "{\"caId\":987654321,\"label\":\"孤儿明细_" + ts + "\"}", token);
        assertEquals("A0400", parse(orphanResponse).path("code").asText(),
                "a detail without a live catalog must be rejected service-side");

        // Logical delete through the endpoint removes the detail from the live list.
        ResponseEntity<String> deleteResponse = exchangeJson(HttpMethod.DELETE,
                DETAIL_URL + "/" + secondId, null, token);
        assertEquals("200", parse(deleteResponse).path("code").asText(),
                "the detail delete must succeed: " + parse(deleteResponse));
        ResponseEntity<String> afterDelete = exchangeJson(HttpMethod.GET,
                DETAIL_URL + "/list?caCode=" + code, null, token);
        List<String> remaining = new ArrayList<>();
        parse(afterDelete).path("data").forEach(item -> remaining.add(item.path("label").asText()));
        assertEquals(java.util.Collections.singletonList("乙_" + ts), remaining,
                "the logically deleted detail must vanish from the live by-code list");

        jdbcTemplate.update("UPDATE public.dict_detail SET deleted = 1 WHERE id IN (?, ?)",
                firstId, secondId);
        cleanupCatal(catalId);
    }

    @Test
    void deletedCatalogCodeIsReusable() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        String code = "b2.reuse." + ts;

        int firstId = createCatal(token, code, "复用目录一_" + ts);
        ResponseEntity<String> deleteResponse = exchangeJson(HttpMethod.DELETE,
                CATAL_URL + "/" + firstId, null, token);
        assertEquals("200", parse(deleteResponse).path("code").asText(),
                "the catalog delete must succeed: " + parse(deleteResponse));

        // Partial unique index discipline: uk_dict_catal_code is partial on deleted = 0, so
        // the dead row never blocks re-creating the code - and the service-level duplicate
        // check counts live rows only.
        ResponseEntity<String> reCreateResponse = exchangeJson(HttpMethod.POST, CATAL_URL,
                "{\"code\":\"" + code + "\",\"name\":\"复用目录二_" + ts + "\"}", token);
        JsonNode reCreateBody = parse(reCreateResponse);
        assertEquals("200", reCreateBody.path("code").asText(),
                "a deleted catalog's code must be reusable: " + reCreateBody);
        int secondId = reCreateBody.path("data").asInt(-1);
        assertTrue(secondId > 0 && secondId != firstId, "the re-created catalog gets a fresh id");

        jdbcTemplate.update("UPDATE public.dict_catal SET deleted = 1 WHERE id = ?", secondId);
        cleanupCatal(firstId);
    }

    /** Creates a catalog through the endpoint and returns its id (no cleanup here). */
    private int createCatal(String token, String code, String name) throws Exception {
        ResponseEntity<String> response = exchangeJson(HttpMethod.POST, CATAL_URL,
                "{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}", token);
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(), "the catalog create must succeed: " + body);
        int id = body.path("data").asInt(-1);
        assertTrue(id > 2, "the created catalog id must come back");
        return id;
    }

    private void cleanupCatal(int id) {
        jdbcTemplate.update("UPDATE public.dict_catal SET deleted = 1 WHERE id = ?", id);
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
