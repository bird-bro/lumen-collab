package org.birdbro.lumen.manage;

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

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Operation-log wiring proof (system-data tasks 3.3 / 4.4, spec scenario 6): an annotated
 * write endpoint ({@code POST /api/v1/system/orgs} carrying the copied {@code @Log}) must
 * append a {@code public.log_info} row through the copied {@code LogAspect} →
 * {@code AsyncManager} machinery - asynchronously, so the assertion POLLS (see
 * {@link AuthAsyncPoll}).
 *
 * <p>The row must carry the operator identity (uid = the run-scoped admin's id, account
 * the run-scoped admin username - the JWT principal the aspect reads), the annotation's
 * title/type, and the correct method/url.
 * The request sends a real-browser User-Agent on purpose: the copied aspect parses the
 * agent string (browser/os/engine columns) and the TestRestTemplate default
 * {@code Java/...} agent has no parseable browser version.</p>
 *
 * @author birdbro
 */
class AuthOperationLogTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/system/login";
    private static final String ORGS_URL = "/api/v1/system/orgs";
    private static final String CHROME_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void annotatedOrgCreateAppendsLogInfoRowWithOperatorIdentity() throws Exception {
        String token = adminToken();
        String orgName = "日志机构_" + System.currentTimeMillis(); // unique - shared DB

        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, ORGS_URL,
                "{\"upId\":1,\"type\":1,\"name\":\"" + orgName + "\"}", token);
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(), "the annotated org create must succeed: " + createBody);
        int createdId = createBody.path("data").asInt(-1);
        assertTrue(createdId > 0, "the created org id must come back");

        // Scenario 6: the async log_info row appears (uid = the operator), carrying the
        // annotation title and the correct method/url - polled, not slept-for.
        AtomicBoolean rowPresent = new AtomicBoolean(false);
        AuthAsyncPoll.until(() -> {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.log_info WHERE uid = ? AND url = ? "
                    + "AND method = 'POST' AND title = '机构管理' AND param LIKE ?",
                    Integer.class, runUserId(), ORGS_URL, "%" + orgName + "%");
            rowPresent.set(count != null && count > 0);
            return rowPresent.get();
        }, "the @Log write for POST /api/v1/system/orgs (run-admin uid, title 机构管理, param "
                + orgName + ") never landed in public.log_info");

        // The matching row also carries the success record flag (0 INFO) and the account.
        Integer record = jdbcTemplate.queryForObject(
                "SELECT record FROM public.log_info WHERE uid = ? AND url = ? "
                + "AND method = 'POST' AND title = '机构管理' AND param LIKE ?",
                Integer.class, runUserId(), ORGS_URL, "%" + orgName + "%");
        assertEquals(Integer.valueOf(0), record, "a successful call must be logged as record 0 (INFO)");

        jdbcTemplate.update("DELETE FROM system.org_info WHERE id = ?", createdId);
    }

    private String adminToken() throws Exception {
        ResponseEntity<String> loginResponse = postJson(LOGIN_URL, runAdminLoginBody());
        JsonNode loginBody = parse(loginResponse);
        assertEquals("200", loginBody.path("code").asText(), "admin login must hand out the token");
        String token = loginBody.path("data").path("token").asText("");
        assertTrue(!token.isEmpty(), "the admin token must be present");
        return token;
    }

    /** Posts JSON with the admin token AND the real-browser User-Agent the aspect parses. */
    private ResponseEntity<String> exchangeJson(HttpMethod method, String url, String json, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token); // Constants.TOKEN_PREFIX
        headers.set("User-Agent", CHROME_UA);
        return restTemplate.exchange(url, method, new HttpEntity<>(json, headers), String.class);
    }

    private ResponseEntity<String> postJson(String url, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }
}
