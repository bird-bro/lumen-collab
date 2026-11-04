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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * City-reference query proof (dict-city-reference tasks 1.2 / 2.1 / 2.2, spec
 * scenarios): the read-only {@code /api/v1/system/dicts/city/**} endpoints driven
 * through the real servlet context with the admin token, plus the service-level
 * round-trip behind them. Covered:
 *
 * <ol>
 *   <li>{@code DictCityService.tree()} assembles the flat {@code public.dict_city} rows
 *       from {@code upId = 0} (TreeUtils, org-tree mechanism): a single 中国 root with
 *       the province/city/district levels nested four deep (spec: tree scenario);</li>
 *   <li>{@code DictCityService.list(level, upId)} - both filters optional: unfiltered
 *       returns the full 3751, {@code level} alone the 35 provinces, {@code upId} alone
 *       a city's districts (the spec: list scenario), both together their intersection;</li>
 *   <li>the same shapes over HTTP: {@code GET /tree} and {@code GET /list?level=}
 *       {@code /list?upId=};</li>
 *   <li>NO write path: POST/DELETE against the read-only mappings answer the "405"
 *       method-not-supported envelope and the frozen seed checksum stays 3751 (design
 *       D3 - updates arrive only as later seed migrations).</li>
 * </ol>
 *
 * @author birdbro
 */
class AuthDictCityTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/system/login";
    private static final String CITY_URL = "/api/v1/system/dicts/city";

    /** The lineage checksum the read-only table must keep across this whole class. */
    private static final int SEED_TOTAL = 3751;

    /** 北京市's districts under upId 110100 (the spec's list scenario anchor). */
    private static final int BEIJING_CITY_UP_ID = 110100;
    private static final int BEIJING_DISTRICTS = 16;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private org.birdbro.lumen.system.service.IDictCityService dictCityService;

    @Test
    void serviceTreeAssemblesChinaRootFourLevelsDeep() {
        List<org.birdbro.lumen.system.entity.DictCityEntity> tree = dictCityService.tree();
        assertEquals(1, tree.size(),
                "the tree must have exactly one root - the 中国 row (upId = 0)");
        org.birdbro.lumen.system.entity.DictCityEntity root = tree.get(0);
        assertEquals(100000L, root.getId(), "the root carries the lineage id 100000");
        assertEquals("中国", root.getName(), "the root display name");
        assertEquals(0L, root.getUpId(), "the root's upId is the 0 convention");

        // Four-level nesting: 中国 -> 北京 -> 北京市 -> 东城区 (spec tree scenario).
        org.birdbro.lumen.system.entity.DictCityEntity beijing = byId(root.getChildren(), 110000L);
        assertEquals("北京", beijing.getName(), "the province child of the root");
        org.birdbro.lumen.system.entity.DictCityEntity beijingCity =
                byId(beijing.getChildren(), 110100L);
        assertEquals("北京市", beijingCity.getName(), "the city nested under the province");
        assertEquals(BEIJING_DISTRICTS, beijingCity.getChildren().size(),
                "北京市 nests all 16 lineage districts");
        assertEquals("东城区", byId(beijingCity.getChildren(), 110101L).getName(),
                "the district level resolves as leaf children");
    }

    @Test
    void serviceListFiltersByLevelAndUpIdIndependently() {
        assertEquals(SEED_TOTAL, dictCityService.list(null, null).size(),
                "no filters return the whole frozen seed");
        List<org.birdbro.lumen.system.entity.DictCityEntity> provinces =
                dictCityService.list(1, null);
        assertEquals(35, provinces.size(), "level = 1 alone returns the 35 provinces");
        assertTrue(provinces.stream().allMatch(row -> row.getLevel() == 1),
                "every level-filtered row stays at the requested level");

        List<org.birdbro.lumen.system.entity.DictCityEntity> districts =
                dictCityService.list(null, BEIJING_CITY_UP_ID);
        assertEquals(BEIJING_DISTRICTS, districts.size(),
                "upId = 110100 alone returns 北京市's districts");
        assertTrue(districts.stream().allMatch(
                        row -> row.getUpId() == BEIJING_CITY_UP_ID && row.getLevel() == 3),
                "every upId-filtered row is one of the city's level-3 districts");
        assertTrue(districts.stream().noneMatch(row -> row.getId() == BEIJING_CITY_UP_ID),
                "the city node itself must not appear among its own children");

        assertEquals(BEIJING_DISTRICTS, dictCityService.list(3, BEIJING_CITY_UP_ID).size(),
                "the two filters intersect (districts are level 3)");
        assertEquals(0, dictCityService.list(1, BEIJING_CITY_UP_ID).size(),
                "a level/upId pair with no intersection returns empty");
    }

    @Test
    void treeEndpointReturnsTheNestedChinaRoot() throws Exception {
        String token = adminToken();
        JsonNode body = get(CITY_URL + "/tree", token);
        assertEquals("200", body.path("code").asText(),
                "the city tree must succeed over the envelope: " + body);
        JsonNode data = body.path("data");
        assertTrue(data.isArray() && data.size() == 1,
                "the envelope data is the one-root tree array");
        JsonNode root = data.get(0);
        assertEquals("中国", root.path("name").asText(), "the tree root over HTTP");
        assertTrue(root.path("children").isArray() && root.path("children").size() == 35,
                "the root nests the 35 provinces over HTTP");
        JsonNode beijingCity = root.path("children").get(0).path("children").get(0);
        assertEquals("北京市", beijingCity.path("name").asText(),
                "the city level nests through HTTP the same way");
        assertEquals(BEIJING_DISTRICTS, beijingCity.path("children").size(),
                "the district level nests through HTTP the same way");
    }

    @Test
    void listEndpointFiltersADistrictsListAndProvincesByLevel() throws Exception {
        String token = adminToken();

        // Spec list scenario: the 北京市 city node lists ONLY its level-3 districts.
        JsonNode districts = get(CITY_URL + "/list?upId=" + BEIJING_CITY_UP_ID, token);
        assertEquals("200", districts.path("code").asText(),
                "the upId-filtered list must succeed: " + districts);
        JsonNode rows = districts.path("data");
        assertEquals(BEIJING_DISTRICTS, rows.size(), "16 districts under 北京市");
        for (JsonNode row : rows) {
            assertEquals(3, row.path("level").asInt(-1),
                    "only level-3 districts return: " + row);
            assertEquals(BEIJING_CITY_UP_ID, row.path("upId").asInt(-1),
                    "every row anchors to the requested parent: " + row);
        }
        assertTrue(containsName(rows, "东城区"), "the first lineage district is present");

        // level = 1 alone returns ONLY the provinces (spec: 只回省级).
        JsonNode provinces = get(CITY_URL + "/list?level=1", token);
        assertEquals("200", provinces.path("code").asText(),
                "the level-filtered list must succeed: " + provinces);
        assertEquals(35, provinces.path("data").size(), "35 provinces at level 1");
        for (JsonNode row : provinces.path("data")) {
            assertEquals(1, row.path("level").asInt(-1),
                    "no root (level 0) or city/district rows leak into level=1: " + row);
        }
        assertTrue(containsName(provinces.path("data"), "北京"),
                "the 北京 province row is present");
    }

    @Test
    void swaggerSpecExposesTheReadOnlyCityEndpoints() throws Exception {
        // Task 2.2 swagger smoke: the @Api-tagged controller appears in the OAS spec -
        // and ONLY with its two GET paths (no write operation, design D3).
        String token = adminToken();
        JsonNode spec = get("/v3/api-docs", token);
        JsonNode paths = spec.path("paths");
        assertTrue(paths.path(CITY_URL + "/tree").has("get"),
                "the city tree endpoint must appear in the swagger spec: "
                + paths.fieldNames());
        assertTrue(paths.path(CITY_URL + "/list").has("get"),
                "the city list endpoint must appear in the swagger spec");
        assertTrue(!paths.path(CITY_URL + "/tree").has("post")
                        && !paths.path(CITY_URL + "/list").has("post")
                        && !paths.path(CITY_URL + "/tree").has("delete")
                        && !paths.path(CITY_URL + "/list").has("delete"),
                "no write operation is documented for the read-only table");
    }

    @Test
    void noWriteEndpointExistsForTheReferenceTable() throws Exception {
        String token = adminToken();

        // POST/PUT/DELETE against the GET-only mappings must never reach a write path:
        // the dispatcher answers the method-not-supported envelope (design D3).
        JsonNode postTree = parse(exchangeJson(HttpMethod.POST, CITY_URL + "/tree", "{}", token));
        assertEquals("405", postTree.path("code").asText(),
                "POST /tree must be rejected as method-not-supported: " + postTree);
        JsonNode postList = parse(exchangeJson(HttpMethod.POST, CITY_URL + "/list", "{}", token));
        assertEquals("405", postList.path("code").asText(),
                "POST /list must be rejected as method-not-supported: " + postList);
        JsonNode deleteList = parse(exchangeJson(HttpMethod.DELETE, CITY_URL + "/list", null, token));
        assertEquals("405", deleteList.path("code").asText(),
                "DELETE /list must be rejected as method-not-supported: " + deleteList);

        // The frozen seed checksum survived every rejected attempt (no write path, D3).
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.dict_city", Integer.class);
        assertEquals(SEED_TOTAL, total.intValue(),
                "the seed must stay untouched - the table has NO write endpoint");
    }

    // ---- helpers (the AuthDictCrudTests idiom) ----

    private org.birdbro.lumen.system.entity.DictCityEntity byId(
            List<org.birdbro.lumen.system.entity.DictCityEntity> nodes, long id) {
        return nodes.stream().filter(node -> node.getId() == id).findFirst()
                .orElseThrow(() -> new AssertionError("node " + id + " must be nested"));
    }

    private boolean containsName(JsonNode rows, String name) {
        for (JsonNode row : rows) {
            if (name.equals(row.path("name").asText())) {
                return true;
            }
        }
        return false;
    }

    private JsonNode get(String url, String token) throws Exception {
        return parse(exchangeJson(HttpMethod.GET, url, null, token));
    }

    private String adminToken() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> loginResponse = restTemplate.exchange(LOGIN_URL,
                HttpMethod.POST, new HttpEntity<>("{\"username\":\"admin\","
                        + "\"password\":\"admin123\"}", headers), String.class);
        JsonNode loginBody = parse(loginResponse);
        assertEquals("200", loginBody.path("code").asText(), "admin login must hand out the token");
        String token = loginBody.path("data").path("token").asText("");
        assertTrue(!token.isEmpty(), "the admin token must be present");
        return token;
    }

    private ResponseEntity<String> exchangeJson(HttpMethod method, String url, String json,
                                                String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set("Authorization", "Bearer " + token); // Constants.TOKEN_PREFIX
        }
        return restTemplate.exchange(url, method,
                new HttpEntity<>(json, headers), String.class);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }
}
