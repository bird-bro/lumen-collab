package org.birdbro.lumen.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.birdbro.common.core.pojo.LogInfo;
import org.birdbro.common.utils.DateUtils;
import org.birdbro.framework.log.service.ILogInfoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Log query endpoint proof (system-data task 3.2 / audit-log-unify-api b3 re-target,
 * spec delta § Log Query Endpoint): {@code GET /api/v1/system/logs} pages
 * {@code public.log_info} with the optional {@code type} INTEGER-code/{@code uid}/
 * {@code dateYm} filters and the {@code action_time DESC} ordering — the single
 * unified audit endpoint (both event families; the former String free-text type
 * filter is gone with V25's re-type and a non-integer value now fails as an A-class
 * parameter error).
 *
 * <p>The rows are seeded through the framework {@code ILogInfoService} with controlled
 * action times / uids / annotation labels (the label→code translation happens in the
 * insert), so the assertions are deterministic against the shared long-lived database
 * (unique per-run markers, keyed assertions - never global row counts). The pagination
 * edge (empty result) is covered too.</p>
 *
 * @author birdbro
 */
class AuthLogQueryTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/system/login";
    private static final String LOGS_URL = "/api/v1/system/logs";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ILogInfoService logInfoService;

    @Test
    void logsEndpointPagesFiltersAndOrdersLogInfoRows() throws Exception {
        long ts = System.currentTimeMillis();
        String marker = "b3q.log." + ts; // unique per run - shared DB
        int uidA = (int) (100000000 + ts % 100000000);
        int uidB = (int) (200000000 + ts % 100000000);
        int currentYm = Integer.parseInt(DateUtils.dateTimeNow("yyyyMM"));

        // Three controlled rows: A1 (newest), A2 (1 s older), B (uid B) - the insert stamps
        // date_ym with the current month and translates the annotation label to its code.
        assertTrue(logInfoService.insert(LogInfo.builder()
                .uid(uidA).account(marker + "-a1").title("b3q标题A1").type("新增")
                .actionTime(new Date(ts)).name(marker + "-a1")
                .build()), "the first seeded log row must insert");
        assertTrue(logInfoService.insert(LogInfo.builder()
                .uid(uidA).account(marker + "-a2").title("b3q标题A2").type("修改")
                .actionTime(new Date(ts - 1000)).name(marker + "-a2")
                .build()), "the second seeded log row must insert");
        assertTrue(logInfoService.insert(LogInfo.builder()
                .uid(uidB).account(marker + "-b").title("b3q标题B").type("删除")
                .actionTime(new Date(ts - 2000)).name(marker + "-b")
                .build()), "the third seeded log row must insert");

        String token = adminToken();

        // Pagination: page 1 with pageSize 2 returns exactly two records and a total of 3+.
        ResponseEntity<String> pageResponse = exchangeJson(HttpMethod.GET,
                LOGS_URL + "?page=1&pageSize=2", token);
        JsonNode pageBody = parse(pageResponse);
        assertEquals(200, pageResponse.getStatusCodeValue(),
                "the logs endpoint answers over HTTP 200 (envelope transport)");
        assertEquals("200", pageBody.path("code").asText(), "the logs query must succeed: " + pageBody);
        assertEquals(2, pageBody.path("data").path("records").size(),
                "page 1 with pageSize 2 must carry exactly two records");
        assertTrue(pageBody.path("data").path("total").asLong(-1) >= 3,
                "the total must count at least the three seeded rows: " + pageBody.path("data").path("total"));

        // uid filter: EXACTLY the two rows of uidA, newest first (actionTime DESC).
        ResponseEntity<String> uidResponse = exchangeJson(HttpMethod.GET,
                LOGS_URL + "?page=1&pageSize=10&uid=" + uidA, token);
        JsonNode uidBody = parse(uidResponse);
        assertEquals("200", uidBody.path("code").asText(), "the uid-filtered query must succeed: " + uidBody);
        List<String> namesInOrder = new ArrayList<>();
        uidBody.path("data").path("records")
                .forEach(item -> namesInOrder.add(item.path("name").asText()));
        assertEquals(2, uidBody.path("data").path("total").asLong(-1),
                "the uid filter must match exactly the two seeded rows");
        assertEquals(java.util.Arrays.asList(marker + "-a1", marker + "-a2"), namesInOrder,
                "the uid-filtered records must be ordered actionTime DESC (newest first): " + namesInOrder);

        // Integer-code type filter: only the requested code's rows come back
        // (1 = 新增 via the insert's label translation).
        ResponseEntity<String> typeResponse = exchangeJson(HttpMethod.GET,
                LOGS_URL + "?page=1&pageSize=10&type=1", token);
        JsonNode typeBody = parse(typeResponse);
        assertEquals("200", typeBody.path("code").asText(), "the type-filtered query must succeed: " + typeBody);
        boolean onlyCode1 = true;
        int withMarker = 0;
        for (JsonNode item : typeBody.path("data").path("records")) {
            onlyCode1 = onlyCode1 && item.path("type").asInt(-1) == 1;
            if ((marker + "-a1").equals(item.path("account").asText())) {
                withMarker++;
            }
        }
        assertTrue(onlyCode1 && withMarker >= 1,
                "every type=1 record must carry code 1 and the seeded 新增 row must be among them");

        // dateYm filter (combined with uid): the current month holds all of uidA's rows.
        ResponseEntity<String> ymResponse = exchangeJson(HttpMethod.GET,
                LOGS_URL + "?page=1&pageSize=10&uid=" + uidA + "&dateYm=" + currentYm, token);
        JsonNode ymBody = parse(ymResponse);
        assertEquals("200", ymBody.path("code").asText(), "the dateYm-filtered query must succeed: " + ymBody);
        assertEquals(2, ymBody.path("data").path("total").asLong(-1),
                "the current-month + uid filter must match exactly the two seeded rows");

        // Pagination edge: a filter combination with no rows answers an empty page.
        ResponseEntity<String> emptyResponse = exchangeJson(HttpMethod.GET,
                LOGS_URL + "?page=1&pageSize=10&uid=" + uidA + "&account=" + marker + "-no-such", token);
        JsonNode emptyBody = parse(emptyResponse);
        assertEquals("200", emptyBody.path("code").asText(), "the empty-result query must still succeed");
        assertEquals(0, emptyBody.path("data").path("records").size(),
                "a filter with no matching rows must produce an empty records array");
        assertEquals(0, emptyBody.path("data").path("total").asLong(-1),
                "the empty result total must be 0");

        sweep(marker);
    }

    /** Hard-deletes this run's seeded rows (smoke-test sweep ruling — never soft-delete). */
    private void sweep(String marker) {
        try {
            java.util.Map<String, String> required = AuthTestEnv.load().require(
                    AuthTestEnv.PG_HOST, AuthTestEnv.PG_PORT, AuthTestEnv.PG_USER, AuthTestEnv.PG_PASSWORD);
            AuthTestEnv env = AuthTestEnv.load();
            String url = "jdbc:postgresql://" + required.get(AuthTestEnv.PG_HOST) + ":"
                    + required.get(AuthTestEnv.PG_PORT) + "/"
                    + env.optional(AuthTestEnv.DB, AuthTestEnv.DEFAULT_DB)
                    + "?currentSchema=system,public";
            try (java.sql.Connection connection = java.sql.DriverManager.getConnection(url,
                    required.get(AuthTestEnv.PG_USER), required.get(AuthTestEnv.PG_PASSWORD));
                    java.sql.Statement statement = connection.createStatement()) {
                statement.execute("DELETE FROM public.log_info WHERE account LIKE '"
                        + marker.replace("_", "\\_") + "%' ESCAPE '\\'");
            }
        } catch (java.sql.SQLException | IllegalStateException ignored) {
            // sweep best effort - the @AfterAll base sweep re-purges the b3q family
        }
    }

    private String adminToken() throws Exception {
        ResponseEntity<String> loginResponse = postJson(LOGIN_URL, runAdminLoginBody());
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

    private ResponseEntity<String> exchangeJson(HttpMethod method, String url, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token); // Constants.TOKEN_PREFIX
        return restTemplate.exchange(url, method, new HttpEntity<>(headers), String.class);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }
}
