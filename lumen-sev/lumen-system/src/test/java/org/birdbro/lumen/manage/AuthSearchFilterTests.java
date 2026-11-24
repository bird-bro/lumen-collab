package org.birdbro.lumen.manage;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The saved-search persistence proof (us-registration-redesign-api tasks 5.1-5.2;
 * specs/api "Saved Search Filter Endpoints" + specs/system-data "Search Filter Table" -
 * 拍板 07-A): the generic uid-owned REST over /api/v1/system/search-filters -
 * authenticated-only WITHOUT a perm family (the dict-lookup precedent), the query jsonb
 * passthrough, the ONE-default-per-(uid,pageKey) full-replace, and the cross-user
 * invisibility. Fixtures hard-delete the marker-keyed rows (the shared-server ruling).
 *
 * @author birdbro
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthSearchFilterTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/system/login";
    private static final String FILTERS_URL = "/api/v1/system/search-filters";
    /** The page-key + name marker family (the sweep keys on both). */
    private static final String PAGE_KEY = "us3-filter-probe";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String token;

    private Integer runUid;

    @AfterAll
    void sweepFixtures() {
        // hard-delete the marker family (live OR soft-deleted) - the ruling
        jdbcTemplate.update("DELETE FROM system.search_filter WHERE page_key = ?", PAGE_KEY);
    }

    private String token() throws Exception {
        if (token == null) {
            ResponseEntity<String> loginResponse = exchangeJson(HttpMethod.POST, LOGIN_URL,
                    loginBody(RUN_ACCOUNT, ADMIN_TEST_PASSWORD), null);
            JsonNode login = parse(loginResponse);
            assertEquals("200", login.path("code").asText(),
                    "the run-admin login must hand out the token: " + login);
            token = login.path("data").path("token").asText("");
            runUid = jdbcTemplate.queryForObject(
                    "SELECT id FROM system.user_info WHERE username = ? AND deleted = 0",
                    Integer.class, RUN_ACCOUNT);
        }
        return token;
    }

    @Test
    void searchFilterTableLandsWithTheHouseShape() {
        for (String column : new String[]{"uid", "page_key", "name", "query",
                "is_default", "deleted", "create_by", "create_time", "update_by", "update_time"}) {
            Integer found = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = 'system' AND table_name = 'search_filter' "
                            + "AND column_name = ?",
                    Integer.class, column);
            assertEquals(1, found.intValue(),
                    "system.search_filter must carry the column " + column);
        }
        List<String> indexDefs = jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'system' "
                        + "AND tablename = 'search_filter' AND indexdef LIKE '%UNIQUE%'",
                String.class);
        assertTrue(indexDefs.stream().anyMatch(def -> def.contains("is_default")
                        && def.contains("deleted = 0")),
                "the one-default-per-uid+page partial unique index must exist: " + indexDefs);
    }

    @Test
    void searchFilterCrudDefaultReplaceAndUidScoping() throws Exception {
        String bearer = token();

        // create: the query jsonb rides verbatim, the default flag lands on
        JsonNode create = parse(exchangeJson(HttpMethod.POST, FILTERS_URL,
                "{\"pageKey\":\"" + PAGE_KEY + "\",\"name\":\"us3默认筛选项\","
                        + "\"query\":{\"status\":[\"REGISTERED\"],\"patientName\":\"张\"},"
                        + "\"isDefault\":true}", bearer));
        assertEquals("200", create.path("code").asText(),
                "the create must succeed: " + create);
        int firstId = create.path("data").path("id").asInt(-1);
        assertTrue(firstId > 0, "the create returns the filter id: " + create);
        assertEquals(Boolean.TRUE, create.path("data").path("isDefault").asBoolean(false),
                "the created filter carries the requested default flag");

        // uid ownership binds at insert (never client-bindable)
        Integer ownerUid = jdbcTemplate.queryForObject(
                "SELECT uid FROM system.search_filter WHERE id = ?", Integer.class, firstId);
        assertEquals(runUid, ownerUid, "the row binds the CALLER's uid");

        // a second filter + set-default full-replace: the first loses its default
        JsonNode second = parse(exchangeJson(HttpMethod.POST, FILTERS_URL,
                "{\"pageKey\":\"" + PAGE_KEY + "\",\"name\":\"us3备选筛选项\","
                        + "\"query\":{\"status\":[\"BOOKED\"]}}", bearer));
        int secondId = second.path("data").path("id").asInt(-1);
        assertTrue(secondId > 0, "the second filter persists: " + second);
        JsonNode setDefault = parse(exchangeJson(HttpMethod.PUT,
                FILTERS_URL + "/" + secondId + "/default", null, bearer));
        assertEquals("200", setDefault.path("code").asText(),
                "the set-default must succeed: " + setDefault);
        Integer firstDefault = jdbcTemplate.queryForObject(
                "SELECT is_default FROM system.search_filter WHERE id = ?",
                Integer.class, firstId);
        Integer secondDefault = jdbcTemplate.queryForObject(
                "SELECT is_default FROM system.search_filter WHERE id = ?",
                Integer.class, secondId);
        assertEquals(0, firstDefault.intValue(), "the previous default turned OFF");
        assertEquals(1, secondDefault.intValue(), "the new default turned ON");

        // update renames, never crosses the uid
        JsonNode update = parse(exchangeJson(HttpMethod.PUT, FILTERS_URL + "/" + firstId,
                "{\"pageKey\":\"" + PAGE_KEY + "\",\"name\":\"us3改名筛选项\","
                        + "\"query\":{\"patientName\":\"李\"}}", bearer));
        assertEquals("200", update.path("code").asText(), "the update must succeed: " + update);
        assertNotEquals(Boolean.TRUE, update.path("data").path("isDefault").asBoolean(true),
                "the update path must not resurrect the default flag");

        // cross-user invisibility: a foreign-uid row never renders in the list
        jdbcTemplate.update(
                "INSERT INTO system.search_filter (uid, page_key, name, query, is_default) "
                        + "VALUES (987654321, ?, 'us3外来筛选项', '{\"x\":1}'::jsonb, 0)", PAGE_KEY);
        JsonNode list = parse(exchangeJson(HttpMethod.GET,
                FILTERS_URL + "?pageKey=" + PAGE_KEY, null, bearer));
        assertEquals("200", list.path("code").asText(), "the list must succeed: " + list);
        assertEquals(2, list.path("data").size(),
                "only the CALLER's two rows render (the foreign-uid row is invisible): " + list);

        // delete is soft
        JsonNode delete = parse(exchangeJson(HttpMethod.DELETE,
                FILTERS_URL + "/" + secondId, null, bearer));
        assertEquals("204", delete.path("code").asText(),
                "the void delete answers the house no-content envelope: " + delete);
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM system.search_filter WHERE id = ?",
                Integer.class, secondId);
        assertEquals(1, deleted.intValue(), "the row SOFT-deletes");
        JsonNode listAfterDelete = parse(exchangeJson(HttpMethod.GET,
                FILTERS_URL + "?pageKey=" + PAGE_KEY, null, bearer));
        assertEquals(1, listAfterDelete.path("data").size(),
                "the soft-deleted row leaves the list");

        // authenticated-only: a tokenless call answers the entry point's 203
        JsonNode tokenless = parse(exchangeJson(HttpMethod.GET,
                FILTERS_URL + "?pageKey=" + PAGE_KEY, null, null));
        assertEquals("203", tokenless.path("code").asText(),
                "a tokenless list answers the entry point's 203: " + tokenless);
    }

    // ---------------------------------------------------------------- helpers

    private ResponseEntity<String> exchangeJson(HttpMethod method, String url, String json,
            String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set("Authorization", "Bearer " + token);
        }
        return restTemplate.exchange(url, method, new HttpEntity<>(json, headers),
                String.class);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }
}
