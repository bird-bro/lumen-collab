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

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sign-event wiring proof (system-data tasks 3.4 / 4.5, spec scenario 10): the login loop
 * writes {@code public.log_sign} rows for the SUCCESS and WRONG-PASSWORD events
 * (symmetric to the copied logout handler's {@code Constants.SIGN_OUT} event), and the
 * logout through the copied {@code LogoutSuccessHandlerImpl} chain lands its row too -
 * proving adaptation (7) end-to-end (de-{@code ice.} entities over the V3 table).
 *
 * <p>Everything here is asynchronous ({@code AsyncManager}) - the assertions POLL via
 * {@link AuthAsyncPoll}. The sign events are keyed to a freshly created user (unique
 * account per run), so the shared long-lived database's accumulated rows never blur the
 * verdicts. Requests carry a real-browser User-Agent: the copied
 * {@code AsyncFactory.recordForSign} parses browser/os/engine from it, and the
 * TestRestTemplate default {@code Java/...} agent has no parseable browser version.</p>
 *
 * <p>Event constants (recorded selection): the copied Constants carry no SIGN_IN; the
 * sign-in pair uses the existing generic SUCCESS (10) / FAIL (11), keeping SIGN_OUT (12)
 * for logout - no common modification.</p>
 *
 * @author birdbro
 */
class AuthSignLogTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String USERS_URL = "/api/v1/auth/users";
    private static final String SIGN_OUT_URL = "/api/sign/out";
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
    void loginSuccessAndWrongPasswordEachAppendSignRow() throws Exception {
        String adminToken = adminToken();
        String username = "b2sign_" + System.currentTimeMillis(); // unique - shared DB
        int uid = createUser(adminToken, username, "sign-pass-123");

        // Login SUCCESS (scenario 10): one log_sign row with type SUCCESS(10), uid, account.
        ResponseEntity<String> successResponse = postJsonWithUa(LOGIN_URL,
                "{\"username\":\"" + username + "\",\"password\":\"sign-pass-123\"}");
        JsonNode successBody = parse(successResponse);
        assertEquals("200", successBody.path("code").asText(), "the fresh user login must succeed: " + successBody);
        String jwt = successBody.path("data").path("token").asText("");

        AtomicBoolean successRowPresent = new AtomicBoolean(false);
        AuthAsyncPoll.until(() -> {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.log_sign WHERE account = ? AND type = 10 AND uid = ?",
                    Integer.class, username, uid);
            successRowPresent.set(count != null && count > 0);
            return successRowPresent.get();
        }, "the login-success sign row (account " + username + ", type 10, uid " + uid
                + ") never landed in public.log_sign");

        // The success row carries the Sign-IN msg and the session anchor (the UserLogin
        // token - a UUID, not the JWT).
        Integer tokenCarriers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.log_sign WHERE account = ? AND type = 10 "
                + "AND msg = 'Sign-IN' AND token IS NOT NULL AND descr LIKE ?",
                Integer.class, username, "%登录成功%");
        assertEquals(Integer.valueOf(1), tokenCarriers,
                "the success row must carry msg Sign-IN, a non-null session token anchor, and the success descr");

        // Login FAILURE (wrong password, scenario 10): type FAIL(11) with the attempt uid.
        ResponseEntity<String> wrongResponse = postJsonWithUa(LOGIN_URL,
                "{\"username\":\"" + username + "\",\"password\":\"totally-wrong\"}");
        JsonNode wrongBody = parse(wrongResponse);
        assertEquals("A0210", wrongBody.path("code").asText(),
                "the wrong-password login must still be rejected with the A0210 envelope (wiring must not change behavior)");
        assertFalse(wrongBody.hasNonNull("data"), "no token is issued on failure");

        AtomicBoolean failureRowPresent = new AtomicBoolean(false);
        AuthAsyncPoll.until(() -> {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.log_sign WHERE account = ? AND type = 11 AND uid = ? "
                    + "AND msg = 'Sign-IN'",
                    Integer.class, username, uid);
            failureRowPresent.set(count != null && count > 0);
            return failureRowPresent.get();
        }, "the wrong-password sign row (account " + username + ", type 11, uid " + uid
                + ") never landed in public.log_sign");

        jdbcTemplate.update("UPDATE auth.user_info SET deleted = 1 WHERE id = ?", uid);
    }

    @Test
    void signOutAppendsSignRowThroughTheCopiedHandler() throws Exception {
        String adminToken = adminToken();
        String username = "b2out_" + System.currentTimeMillis(); // unique - shared DB
        int uid = createUser(adminToken, username, "out-pass-123");

        ResponseEntity<String> loginResponse = postJsonWithUa(LOGIN_URL,
                "{\"username\":\"" + username + "\",\"password\":\"out-pass-123\"}");
        JsonNode loginBody = parse(loginResponse);
        assertEquals("200", loginBody.path("code").asText(), "the fresh user login must succeed: " + loginBody);
        String jwt = loginBody.path("data").path("token").asText("");

        // Sign-OUT through the copied handler chain (POST /api/sign/out with the token) -
        // adaptation (7) in action: the copied handler + de-ice. entities write the V3 row.
        ResponseEntity<String> outResponse = postJsonWithUaToken(SIGN_OUT_URL, jwt);
        JsonNode outBody = parse(outResponse);
        assertEquals("200", outBody.path("code").asText(), "the sign-out must succeed: " + outBody);

        AtomicBoolean outRowPresent = new AtomicBoolean(false);
        AuthAsyncPoll.until(() -> {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.log_sign WHERE account = ? AND type = 12 AND uid = ?",
                    Integer.class, username, uid);
            outRowPresent.set(count != null && count > 0);
            return outRowPresent.get();
        }, "the sign-out row (account " + username + ", type SIGN_OUT 12, uid " + uid
                + ") never landed in public.log_sign - the copied logout chain must append it");

        // The logout row keeps the copied handler's msg/descr wording.
        Integer worded = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.log_sign WHERE account = ? AND type = 12 "
                + "AND msg = 'Sign-OUT' AND descr LIKE ?",
                Integer.class, username, "%退出成功%");
        assertEquals(Integer.valueOf(1), worded, "the logout row must carry the copied handler's wording");

        jdbcTemplate.update("UPDATE auth.user_info SET deleted = 1 WHERE id = ?", uid);
    }

    /** Creates a live user through the admin endpoint and returns its id. */
    private int createUser(String adminToken, String username, String password) throws Exception {
        ResponseEntity<String> createResponse = postJsonWithUa(USERS_URL,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"name\":\"sign 日志\"}",
                adminToken);
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(), "the fresh user create must succeed: " + createBody);
        int uid = createBody.path("data").asInt(-1);
        assertTrue(uid > 1, "the created user id must come back past the seeded admin id");
        return uid;
    }

    private String adminToken() throws Exception {
        ResponseEntity<String> loginResponse = postJsonWithUa(LOGIN_URL,
                "{\"username\":\"admin\",\"password\":\"admin123\"}");
        JsonNode loginBody = parse(loginResponse);
        assertEquals("200", loginBody.path("code").asText(), "admin login must hand out the token");
        String token = loginBody.path("data").path("token").asText("");
        assertTrue(!token.isEmpty(), "the admin token must be present");
        return token;
    }

    private ResponseEntity<String> postJsonWithUa(String url, String json) {
        return postJsonWithUa(url, json, null);
    }

    private ResponseEntity<String> postJsonWithUa(String url, String json, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", CHROME_UA);
        if (token != null) {
            headers.set("Authorization", "Bearer " + token); // Constants.TOKEN_PREFIX
        }
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    /** Posts with ONLY the Bearer token (the sign-out contract: token identifies the session). */
    private ResponseEntity<String> postJsonWithUaToken(String url, String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwt); // Constants.TOKEN_PREFIX
        headers.set("User-Agent", CHROME_UA);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(headers), String.class);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }
}
