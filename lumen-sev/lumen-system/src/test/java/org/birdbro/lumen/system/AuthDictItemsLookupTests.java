package org.birdbro.lumen.system;

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
 * Dict items by-name lookup contract proof (role-type-dict-api tasks 2.1/2.4):
 * {@code GET /api/v1/system/dicts/detail/items/{catalogName}} returns the catalog's
 * LIVE, ENABLED details as a slim {@code {code, value}[]} sorted by {@code sort}
 * ascending, wrapped in the standard envelope. The endpoint is AUTHENTICATED-ONLY
 * (no permission string - the city-dict class, auth-rbac): a perm-less principal
 * gets 200 while the dict management family stays A0301-gated for them, and an
 * unauthenticated call is rejected. An unknown catalog resolves to an empty array -
 * never an error (coordination D2: a mis-named consumer cannot break a page).
 *
 * <p>URL note (empirical, root-caused during apply): the catalog-name path segment
 * must be handed to the client RAW (UTF-8 chars verbatim) - RestTemplate/TestRestTemplate
 * encode it correctly, while PRE-encoded {@code %XX} segments land on the security
 * entry point (envelope 203). The browser/axios consumers encode automatically; the
 * recorded contract is the RAW name.</p>
 *
 * <p>The 「角色类型」 fixtures come from the V23 seed (deterministic ids 1020 /
 * 1020001 / 1020002, coordination D1/D6). The shared long-lived database may carry
 * operator-added items in that catalog, so the seed assertions pin the PRESENCE and
 * RELATIVE ORDER of the two seeded codes - not the catalog's exact size. The
 * disabled-exclusion fixture is a dedicated throwaway catalog (registered
 * {@code CATAL_NAME_PREFIXES} family, hard-deleted in cleanup - user ruling
 * 2026-11-07).</p>
 *
 * @author birdbro
 */
class AuthDictItemsLookupTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/system/login";
    private static final String ITEMS_URL = "/api/v1/system/dicts/detail/items";
    private static final String DETAIL_LIST_URL = "/api/v1/system/dicts/detail/list";
    private static final String CATAL_URL = "/api/v1/system/dicts/catal";
    private static final String DETAIL_URL = "/api/v1/system/dicts/detail";
    private static final String ROLES_URL = "/api/v1/system/roles";
    private static final String USERS_URL = "/api/v1/system/users";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seededRoleTypeCatalogResolvesSlimAndSorted() throws Exception {
        String token = adminToken();

        ResponseEntity<String> response = exchangeJson(HttpMethod.GET,
                ITEMS_URL + "/角色类型", null, token);
        assertEquals(200, response.getStatusCodeValue());
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(),
                "the by-name lookup must answer the success envelope: " + body);

        JsonNode items = body.path("data");
        assertTrue(items.isArray(), "data must be the slim item array: " + body);

        // The V23 seeds ride the array in ascending sort order (10 系统角色 before
        // 11 RCP角色); operator-added rows may follow but never precede the seeds'
        // relative order. Every element is slim: exactly the code/value pair.
        int seedTen = -1;
        int seedEleven = -1;
        for (int i = 0; i < items.size(); i++) {
            JsonNode item = items.get(i);
            assertEquals(2, item.size(),
                    "every item carries exactly {code, value}: " + item);
            assertTrue(item.has("code") && item.has("value"),
                    "the slim shape is code+value: " + item);
            assertTrue(item.path("code").isNull() == false,
                    "seeded vocabulary items always carry a code: " + item);
            if (item.path("code").asInt(-1) == 10 && seedTen < 0) {
                seedTen = i;
            }
            if (item.path("code").asInt(-1) == 11 && seedEleven < 0) {
                seedEleven = i;
            }
        }
        assertTrue(seedTen >= 0, "the seeded code 10 (系统角色) must resolve: " + items);
        assertTrue(seedEleven >= 0, "the seeded code 11 (RCP角色) must resolve: " + items);
        assertTrue(seedTen < seedEleven,
                "sort order places 系统角色 before RCP角色: " + items);
        assertEquals("系统角色", items.get(seedTen).path("value").asText(),
                "the seed value rides the item verbatim");
        assertEquals("RCP角色", items.get(seedEleven).path("value").asText(),
                "the seed value rides the item verbatim");
    }

    @Test
    void unknownCatalogResolvesToEmptyArray() throws Exception {
        String token = adminToken();
        JsonNode body = parse(exchangeJson(HttpMethod.GET,
                ITEMS_URL + "/不存在目录_" + System.currentTimeMillis(),
                null, token));
        assertEquals("200", body.path("code").asText(),
                "an unknown catalog is NOT an error (coordination D2): " + body);
        assertTrue(body.path("data").isArray() && body.path("data").size() == 0,
                "the unknown catalog resolves to an empty array: " + body);
    }

    @Test
    void disabledDetailsAreExcluded() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();

        // Throwaway catalog + two details, one disabled via the management API
        // (registered CATAL_NAME_PREFIXES family; hard-deleted in cleanup).
        int catalId = createCatal(token, "明细目录_" + ts);
        int enabledId = createDetail(token, catalId, 1, "启用条目", 1);
        int disabledId = createDetail(token, catalId, 2, "停用条目", 2);
        // The null-code edge (contract Test Obligations): a detail without a code is
        // a display-only vocabulary entry - the projection keeps it with a null code.
        JsonNode nullCodeCreate = parse(exchangeJson(HttpMethod.POST, DETAIL_URL,
                "{\"caId\":" + catalId + ",\"value\":\"无码条目\",\"sort\":3,\"enable\":1}", token));
        assertEquals("200", nullCodeCreate.path("code").asText(),
                "the null-code detail create must succeed: " + nullCodeCreate);
        int nullCodeId = nullCodeCreate.path("data").asInt(-1);
        assertEquals("200", parse(exchangeJson(HttpMethod.PUT,
                DETAIL_URL + "/" + disabledId, "{\"enable\":0}", token))
                .path("code").asText(), "disabling the second detail must succeed");

        JsonNode body = parse(exchangeJson(HttpMethod.GET,
                ITEMS_URL + "/明细目录_" + ts, null, token));
        assertEquals("200", body.path("code").asText(),
                "the throwaway catalog lookup must succeed: " + body);
        JsonNode items = body.path("data");
        assertEquals(2, items.size(),
                "the disabled detail must be excluded, the null-code one stays: " + items);
        assertEquals("启用条目", items.get(0).path("value").asText(),
                "the enabled detail leads (sort 1): " + items);
        assertTrue(items.get(1).path("code").isNull(),
                "the null-code detail projects a null code: " + items.get(1));
        assertEquals("无码条目", items.get(1).path("value").asText(),
                "the null-code detail's value rides verbatim: " + items.get(1));

        cleanupCatalWithDetails(catalId, enabledId, disabledId, nullCodeId);
    }

    @Test
    void lookupNeedsNoPermissionStringButRejectsAnonymous() throws Exception {
        String adminToken = adminToken();
        long ts = System.currentTimeMillis();

        // A perm-less principal (role with no menus - the registered 无权角色_ family)
        // authenticates fine and reads the label lookup...
        int roleId = createRole(adminToken, "无权角色_" + ts);
        String username = "noperm_u_" + ts;
        int userId = createUserWithRoles(adminToken, username, "无权用户_" + ts, roleId);
        String userToken = loginToken(username);

        JsonNode items = parse(exchangeJson(HttpMethod.GET,
                ITEMS_URL + "/角色类型", null, userToken));
        assertEquals("200", items.path("code").asText(),
                "the lookup needs no permission string (city-dict class): " + items);

        // ...while the dict MANAGEMENT family stays page-gated for the same principal.
        JsonNode denied = parse(exchangeJson(HttpMethod.GET,
                DETAIL_LIST_URL + "?caId=1020", null, userToken));
        assertEquals("A0301", denied.path("code").asText(),
                "the dict page gate still applies to the management family: " + denied);

        // And an anonymous call is rejected outright - the copied OPS entry point
        // answers HTTP 200 with the envelope auth-failure code (203), not 401/403.
        ResponseEntity<String> anonymous = restTemplate.exchange(
                ITEMS_URL + "/角色类型", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class);
        JsonNode anonBody = parse(anonymous);
        assertEquals("203", anonBody.path("code").asText(),
                "an unauthenticated lookup must hit the auth entry point: " + anonBody);

        cleanupRoleAndUser(roleId, userId);
    }

    // ===== helpers (the sibling-suite house style) =====

    private String adminToken() throws Exception {
        ResponseEntity<String> loginResponse = postJson(LOGIN_URL, runAdminLoginBody());
        JsonNode loginBody = parse(loginResponse);
        assertEquals("200", loginBody.path("code").asText(), "admin login must hand out the token");
        return loginBody.path("data").path("token").asText("");
    }

    private int createRole(String token, String roleName) throws Exception {
        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, ROLES_URL,
                "{\"name\":\"" + roleName + "\",\"type\":11}", token);
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(),
                "the role create must succeed: " + createBody);
        return createBody.path("data").asInt(-1);
    }

    private int createUserWithRoles(String token, String username, String name, int roleId)
            throws Exception {
        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, USERS_URL,
                "{\"username\":\"" + username + "\",\"password\":\"" + encryptTestPassword("audit-pass-123") + "\","
                        + "\"name\":\"" + name + "\",\"roleIds\":[" + roleId + "]}", token);
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(),
                "the user create with roleIds must succeed: " + createBody);
        return createBody.path("data").asInt(-1);
    }

    private String loginToken(String username) throws Exception {
        ResponseEntity<String> loginResponse = postJson(LOGIN_URL, loginBody(username, "audit-pass-123"));
        JsonNode loginBody = parse(loginResponse);
        assertEquals("200", loginBody.path("code").asText(), "login must hand out the token");
        return loginBody.path("data").path("token").asText("");
    }

    private int createCatal(String token, String name) throws Exception {
        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, CATAL_URL,
                "{\"name\":\"" + name + "\",\"enable\":1,\"sort\":99}", token);
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(),
                "the catal create must succeed: " + createBody);
        return createBody.path("data").asInt(-1);
    }

    private int createDetail(String token, int caId, int code, String value, int sort)
            throws Exception {
        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, DETAIL_URL,
                "{\"caId\":" + caId + ",\"code\":" + code + ",\"value\":\"" + value
                        + "\",\"sort\":" + sort + ",\"enable\":1}", token);
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(),
                "the detail create must succeed: " + createBody);
        return createBody.path("data").asInt(-1);
    }

    /** HARD-delete cleanup (user ruling 2026-11-07): details before their catalog. */
    private void cleanupCatalWithDetails(int catalId, int... detailIds) {
        for (int detailId : detailIds) {
            jdbcTemplate.update("DELETE FROM public.dict_detail WHERE id = ?", detailId);
        }
        jdbcTemplate.update("DELETE FROM public.dict_catal WHERE id = ?", catalId);
    }

    private void cleanupRoleAndUser(int roleId, int userId) {
        jdbcTemplate.update("DELETE FROM system.role_menu WHERE role_id = ?", roleId);
        jdbcTemplate.update("DELETE FROM system.user_role WHERE role_id = ?", roleId);
        jdbcTemplate.update("DELETE FROM system.role_info WHERE id = ?", roleId);
        jdbcTemplate.update("DELETE FROM system.user_role WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM system.user_info WHERE id = ?", userId);
    }

    private ResponseEntity<String> postJson(String url, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    private ResponseEntity<String> exchangeJson(HttpMethod method, String url, String json, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token); // Constants.TOKEN_PREFIX
        return restTemplate.exchange(url, method, new HttpEntity<>(json, headers), String.class);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }
}
