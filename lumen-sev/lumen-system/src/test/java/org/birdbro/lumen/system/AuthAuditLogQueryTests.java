package org.birdbro.lumen.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.birdbro.common.core.pojo.LogInfo;
import org.birdbro.common.core.pojo.LogSign;
import org.birdbro.common.utils.DateUtils;
import org.birdbro.framework.log.service.ILogInfoService;
import org.birdbro.framework.log.service.ILogSignService;
import org.junit.jupiter.api.AfterEach;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unified audit-log query proof (audit-log-unify-api tasks 3.1; spec delta
 * `specs/system-data` § Log Query Endpoint MODIFIED): the SINGLE
 * {@code GET /api/v1/system/logs} serves BOTH event families with the widened
 * filter set ({@code type} Integer, {@code uid}, {@code account}, {@code dateYm}),
 * the {@code action_time DESC, id DESC} ordering, and the D5 credential exclusion -
 * no response row ever carries the {@code session} column. The sign-log sibling
 * {@code GET /sign} is GONE (404) and a non-integer {@code type} value is rejected
 * by the copied machinery as an A-class parameter error (A0421, the handler's
 * actual MethodArgumentTypeMismatchException mapping - characterization per the
 * parent's ruling: assert what the copied errors machinery produces).
 *
 * <p>Rows are seeded through the framework services with per-run unique markers
 * (shared long-lived DB - keyed assertions, never global counts); sign rows go
 * through the b2-retargeted {@code ILogSignService.insert} so both the operation
 * and the sign write path are exercised end-to-end. Fixtures hard-DELETE in the
 * finally block (smoke-test sweep ruling).</p>
 *
 * @author birdbro
 */
class AuthAuditLogQueryTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/system/login";
    private static final String LOGS_URL = "/api/v1/system/logs";
    private static final String SIGN_URL = "/api/v1/system/logs/sign";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ILogInfoService logInfoService;

    @Autowired
    private ILogSignService logSignService;

    private final List<Integer> seededIds = new ArrayList<>();

    @AfterEach
    void sweepSeedRows() {
        // Smoke-test sweep ruling (2026-11-07): fixture sweep is HARD DELETE, never
        // soft-delete (removeById would flip deleted=1 and leave the rows behind).
        // Marker-keyed JDBC delete covers both families (log_info holds everything
        // since V25) keyed on the per-run marker prefixes b3.audit.*/b3.filter.*
        // and the b3-op-row/b3 title shapes.
        try {
            AuthTestEnv env = AuthTestEnv.load();
            java.util.Map<String, String> required = env.require(
                    AuthTestEnv.PG_HOST, AuthTestEnv.PG_PORT, AuthTestEnv.PG_USER, AuthTestEnv.PG_PASSWORD);
            String url = "jdbc:postgresql://" + required.get(AuthTestEnv.PG_HOST) + ":"
                    + required.get(AuthTestEnv.PG_PORT) + "/"
                    + env.optional(AuthTestEnv.DB, AuthTestEnv.DEFAULT_DB)
                    + "?currentSchema=system,public";
            try (java.sql.Connection connection = java.sql.DriverManager.getConnection(url,
                    required.get(AuthTestEnv.PG_USER), required.get(AuthTestEnv.PG_PASSWORD));
                    java.sql.Statement statement = connection.createStatement()) {
                String accountPattern = markerPrefix().replace("_", "\\_") + "%";
                statement.execute("DELETE FROM public.log_info WHERE account LIKE '"
                        + accountPattern + "' ESCAPE '\\'");
                statement.execute("DELETE FROM public.log_info WHERE title LIKE 'b3-%' ESCAPE '\\'");
                statement.execute("DELETE FROM public.log_info WHERE descr LIKE 'b3 %' ESCAPE '\\'");
            }
        } catch (java.sql.SQLException | IllegalStateException ignored) {
            // sweep best effort; the @AfterAll base sweep re-purges the family prefixes
        }
        seededIds.clear();
    }

    @Test
    void unifiedEndpointServesBothFamiliesWithSessionExcluded() throws Exception {
        long ts = System.currentTimeMillis();
        String anchor = "11111111-2222-3333-4444-555555555555";
        String marker = "b3.audit." + ts;
        int currentYm = Integer.parseInt(DateUtils.dateTimeNow("yyyyMM"));

        // One operation row and one sign row under the SAME account - the account
        // filter must match BOTH families (sign-page parity).
        assertTrue(logInfoService.insert(LogInfo.builder()
                .uid(1).account(marker).title("b3-op-row").type("新增")
                .actionTime(new Date(ts)).name(marker)
                .build()), "the operation row must insert");
        assertTrue(logSignService.insert(LogSign.builder()
                .msg("Sign-IN").descr("b3 sign row").type(11)
                .uid(1).account(marker).token(anchor)
                .actionTime(new Date(ts - 1000))
                .build()), "the sign row must insert (retargeted to log_info)");

        String token = adminToken();

        // Account filter matches BOTH families.
        ResponseEntity<String> accountResponse = exchangeJson(HttpMethod.GET,
                LOGS_URL + "?page=1&pageSize=10&account=" + marker, token);
        JsonNode accountBody = parse(accountResponse);
        assertEquals("200", accountBody.path("code").asText(),
                "the account-filtered query must succeed: " + accountBody);
        assertEquals(2, accountBody.path("data").path("total").asLong(-1),
                "the account filter must match BOTH event families: " + accountBody.path("data").path("total"));
        captureSeededIds(accountBody);

        // Sign rows show their Integer event code; op rows their translated code.
        boolean sawOpCode = false;
        boolean sawSignCode = false;
        for (JsonNode item : accountBody.path("data").path("records")) {
            int type = item.path("type").asInt(-1);
            sawOpCode = sawOpCode || type == 1;
            sawSignCode = sawSignCode || type == 11;
            // D5: the credential anchor never reaches the wire.
            assertFalse(item.has("session") && !item.path("session").isNull(),
                    "no audit row may carry the session column: " + item);
        }
        assertTrue(sawOpCode, "the operation row must carry its translated code 1");
        assertTrue(sawSignCode, "the sign row must carry its event code 11");
    }

    @Test
    void integerTypeFilterAndOrderingWork() throws Exception {
        long ts = System.currentTimeMillis();
        String marker = "b3.filter." + ts;

        assertTrue(logInfoService.insert(LogInfo.builder()
                .uid(1).account(marker).title(marker + "-old").type("修改")
                .actionTime(new Date(ts - 2000)).name(marker + "-old")
                .build()), "the older op row must insert");
        assertTrue(logSignService.insert(LogSign.builder()
                .msg("Sign-IN").descr(marker).type(11)
                .uid(1).account(marker).token("anchor-" + ts)
                .actionTime(new Date(ts - 1000))
                .build()), "the failure sign row must insert");
        assertTrue(logSignService.insert(LogSign.builder()
                .msg("Sign-OUT").descr(marker).type(12)
                .uid(1).account(marker).token("anchor-" + ts)
                .actionTime(new Date(ts))
                .build()), "the sign-out row must insert");

        String token = adminToken();

        // type=11: only the failure row (Integer filter, code-level).
        ResponseEntity<String> typeResponse = exchangeJson(HttpMethod.GET,
                LOGS_URL + "?page=1&pageSize=10&type=11", token);
        JsonNode typeBody = parse(typeResponse);
        assertEquals("200", typeBody.path("code").asText(),
                "the Integer type filter must succeed: " + typeBody);
        boolean only11 = true;
        int failuresWithMarker = 0;
        for (JsonNode item : typeBody.path("data").path("records")) {
            only11 = only11 && item.path("type").asInt(-1) == 11;
            if (marker.equals(item.path("account").asText())) {
                failuresWithMarker++;
            }
        }
        assertTrue(only11, "every type=11 record must carry code 11");
        assertEquals(1, failuresWithMarker,
                "type=11 must match exactly the seeded failure row");

        // Ordering: the seeded trio newest-first is sign-out (ts), failure (ts-1000),
        // op (ts-2000) - actionTime DESC with id DESC tie-break (distinct times here).
        ResponseEntity<String> orderResponse = exchangeJson(HttpMethod.GET,
                LOGS_URL + "?page=1&pageSize=10&account=" + marker, token);
        JsonNode orderBody = parse(orderResponse);
        List<String> titlesInOrder = new ArrayList<>();
        orderBody.path("data").path("records")
                .forEach(item -> titlesInOrder.add(item.path("title").asText()));
        // The sign rows' titles come from msg ("Sign-IN"/"Sign-OUT" - the retarget maps
        // title=msg); the op row's title is the marker + "-old" suffix.
        int idxOut = titlesInOrder.indexOf("Sign-OUT");
        int idxIn = titlesInOrder.indexOf("Sign-IN");
        int idxOld = titlesInOrder.indexOf(marker + "-old");
        assertTrue(idxOut >= 0 && idxIn >= 0 && idxOld >= 0 && idxOut < idxIn && idxIn < idxOld,
                "rows must return actionTime DESC (sign-out, failure, op-row): " + titlesInOrder);
        captureSeededIds(orderBody);
    }

    @Test
    void nonIntegerTypeIsRejectedAsAParameterError() throws Exception {
        String token = adminToken();
        ResponseEntity<String> response = exchangeJson(HttpMethod.GET,
                LOGS_URL + "?page=1&pageSize=10&type=not-a-number", token);
        JsonNode body = parse(response);
        String code = body.path("code").asText("");
        assertTrue(code.startsWith("A"),
                "a non-integer type must be rejected with an A-class parameter error: "
                        + code + " / " + body);
    }

    @Test
    void signEndpointIsGone() throws Exception {
        String token = adminToken();
        ResponseEntity<String> response = exchangeJson(HttpMethod.GET,
                SIGN_URL + "?page=1&pageSize=10", token);
        assertEquals(404, response.getStatusCodeValue(),
                "the sign-log endpoint must no longer exist");
    }

    private void captureSeededIds(JsonNode pageBody) {
        for (JsonNode item : pageBody.path("data").path("records")) {
            if (item.path("title").asText("").startsWith("b3-")
                    || item.path("descr").asText("").startsWith("b3 ")) {
                seededIds.add(item.path("id").asInt());
            }
        }
    }

    private String markerPrefix() {
        return "b3.";
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
