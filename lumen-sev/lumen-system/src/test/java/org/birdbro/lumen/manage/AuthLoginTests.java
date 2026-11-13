package org.birdbro.lumen.manage;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Login loop proof (auth-rbac-foundation tasks 3.1-3.2): the FIRST real REST endpoints of
 * the platform, driven through the real servlet context (TestRestTemplate against the
 * RANDOM_PORT server of {@link AuthIntegrationTestBase}) with the copied security machine
 * live - {@code JwtAuthenticationTokenFilter}, {@code SecurityConfig}, and
 * {@code AuthenticationEntryPointImpl} all in the chain.
 *
 * <p>Coverage: valid credentials issue a JWT through the copied {@code TokenService}
 * (token state lands in the test server's Redis); wrong credentials and unknown users
 * come back as the A-class business envelope codes A0210 / A0201; a tokenless request to
 * the protected {@code /auth/me} is rejected with the copied entry point's envelope code
 * "203" (HTTP 200 transport semantics, user ruling 2026-11-02); and the same endpoint
 * with the admin token returns the fresh profile, the seeded menu tree, and the perms set
 * containing the audit button string.</p>
 *
 * @author birdbro
 */
class AuthLoginTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/system/login";
    private static final String ME_URL = "/api/v1/system/me";
    private static final String AUTH_KEY_URL = "/api/v1/system/auth-key";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void validAdminLoginReturnsTokenAndProfile() throws Exception {
        ResponseEntity<String> response = postJson(LOGIN_URL, loginBody("admin", ADMIN_TEST_PASSWORD));

        assertEquals(200, response.getStatusCodeValue(), "login responds over HTTP 200 (envelope transport)");
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(), "successful login must carry the success envelope code");
        String token = body.path("data").path("token").asText("");
        assertFalse(token.isEmpty(), "data.token must carry the issued JWT");
        assertEquals(1, body.path("data").path("profile").path("uid").asInt(-1),
                "the seeded admin has uid 1");
        assertEquals("admin", body.path("data").path("profile").path("username").asText(),
                "profile.username is the login name");
        assertEquals(1, body.path("data").path("profile").path("mustChangePassword").asInt(-1),
                "the seeded admin carries the must-change-password flag");
        assertFalse(body.path("data").path("profile").has("password"),
                "the profile DTO must not even carry a password field (Batch-2 review handoff: "
                        + "the hash must never serialize)");
    }

    @Test
    void wrongPasswordRejectedWithA0210() throws Exception {
        // The wrong password still travels ENCRYPTED (a format failure would be A0400,
        // not A0210 - the clean cutover keeps the wrong-password semantic for decryptable
        // values only).
        ResponseEntity<String> response = postJson(LOGIN_URL, loginBody("admin", "totally-wrong"));

        assertEquals(200, response.getStatusCodeValue());
        JsonNode body = parse(response);
        assertEquals("A0210", body.path("code").asText(), "wrong credentials must map to the A0210 code");
        assertFalse(body.path("msg").asText().isEmpty(), "the A-class rejection carries a descriptive msg");
        assertFalse(body.hasNonNull("data"),
                "no token is issued - data stays null (or is omitted by fastjson2)");
    }

    @Test
    void unknownUserRejectedWithA0201() throws Exception {
        String unknown = "no.such.user." + System.currentTimeMillis(); // unique - shared DB
        ResponseEntity<String> response = postJson(LOGIN_URL,
                loginBody(unknown, "whatever"));

        assertEquals(200, response.getStatusCodeValue());
        JsonNode body = parse(response);
        assertEquals("A0201", body.path("code").asText(), "an unknown account must map to the A0201 code");
        assertFalse(body.hasNonNull("data"));
    }

    @Test
    void plaintextPasswordRejectedWithA0400BeforeAccountLookup() throws Exception {
        // The clean cutover (password-transport-encryption-api): ANY non-ciphertext
        // password field is a request-parameter failure, NOT a login attempt. It must
        // short-circuit BEFORE the account query - the unknown username below must not
        // leak A0201 (no account-existence signal through the format gate).
        String unknown = "no.such.user." + System.currentTimeMillis(); // unique - shared DB
        ResponseEntity<String> response = postJson(LOGIN_URL,
                "{\"username\":\"" + unknown + "\",\"password\":\"admin123\"}");

        assertEquals(200, response.getStatusCodeValue());
        JsonNode body = parse(response);
        assertEquals("A0400", body.path("code").asText(),
                "a plaintext password field must be rejected as a request-parameter error");
        assertFalse(body.hasNonNull("data"));
        assertNotEquals("A0201", body.path("code").asText(),
                "the format gate must fire before the account lookup - no enumeration signal");
    }

    @Test
    void tamperedCiphertextRejectedWithA0400() throws Exception {
        String wire = encryptTestPassword(ADMIN_TEST_PASSWORD);
        String tampered = wire.substring(0, wire.length() - 1)
                + (wire.endsWith("0") ? "1" : "0"); // flip the last hex nibble
        ResponseEntity<String> response = postJson(LOGIN_URL,
                "{\"username\":\"admin\",\"password\":\"" + tampered + "\"}");

        assertEquals(200, response.getStatusCodeValue());
        JsonNode body = parse(response);
        assertEquals("A0400", body.path("code").asText(), "tampered ciphertext must not decrypt");
        assertFalse(body.hasNonNull("data"));
    }

    @Test
    void authKeyEndpointIsAnonymousAndCarriesTheDerivedPoint() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(AUTH_KEY_URL, String.class);

        assertEquals(200, response.getStatusCodeValue());
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(),
                "the key endpoint must be anonymous - no token, no 203 envelope");
        assertEquals("sm2-test", body.path("data").path("keyId").asText(),
                "keyId comes from the test profile config");
        String publicKey = body.path("data").path("publicKey").asText("");
        assertEquals(130, publicKey.length(), "the point is 04||x||y - 130 hex chars");
        assertTrue(publicKey.startsWith("04"), "the uncompressed point marker is present");
    }

    @Test
    void meWithoutTokenRejectedWith203Envelope() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(ME_URL, String.class);

        assertEquals(200, response.getStatusCodeValue(),
                "the copied entry point renders rejections as HTTP 200 envelopes");
        JsonNode body = parse(response);
        assertEquals("203", body.path("code").asText(),
                "a tokenless request must be rejected by the copied AuthenticationEntryPointImpl semantic");
        assertTrue(body.path("msg").asText().contains("认证失败"),
                "the rejection msg mentions 认证失败: " + body.path("msg").asText());
        assertFalse(body.hasNonNull("data"),
                "data stays null (the entry point's fastjson2 rendering omits null fields)");
    }

    @Test
    void meWithAdminTokenReturnsProfileMenusAndPerms() throws Exception {
        ResponseEntity<String> loginResponse = postJson(LOGIN_URL, loginBody("admin", ADMIN_TEST_PASSWORD));
        JsonNode loginBody = parse(loginResponse);
        assertEquals("200", loginBody.path("code").asText(), "the token source (login) must succeed first");
        String token = loginBody.path("data").path("token").asText("");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token); // Constants.TOKEN_PREFIX
        ResponseEntity<String> response = restTemplate.exchange(ME_URL, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertEquals(200, response.getStatusCodeValue());
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(), "a valid admin token must open /auth/me");
        assertEquals(1, body.path("data").path("profile").path("uid").asInt(-1));
        assertEquals("admin", body.path("data").path("profile").path("username").asText());
        assertTrue(body.path("data").path("profile").path("roles").isArray()
                        && body.path("data").path("profile").path("roles").size() > 0,
                "the seeded admin owns role 1 系统管理员");

        // The live menu tree after the console-IA migrations: the 系统 directory (1,
        // renamed from 系统管理 by menu-settings-root) roots the console beside the
        // L1 设置 group and the workstation W nodes, 用户管理 (2) nests under the
        // 用户权限 group (14), 日志查询 (12) under 安全 (16).
        JsonNode menus = body.path("data").path("menus");
        assertTrue(menus.isArray() && menus.size() >= 1, "the live tree has the 系统 root");
        JsonNode systemDirectory = menuWithMid(menus, 1);
        assertNotNull(systemDirectory, "menu 1 系统 must be a tree root");
        assertEquals("系统", systemDirectory.path("title").asText());
        JsonNode userPermGroup = menuWithMid(systemDirectory.path("children"), 14);
        assertNotNull(userPermGroup, "the 用户权限 group (14) nests under the system directory");
        assertNotNull(menuWithMid(userPermGroup.path("children"), 2),
                "user management (menu 2) nests under the 用户权限 group");
        JsonNode securityGroup = menuWithMid(systemDirectory.path("children"), 16);
        assertNotNull(securityGroup, "the 安全 group (16) nests under the system directory");
        assertNotNull(menuWithMid(securityGroup.path("children"), 12),
                "log query (menu 12) nests under the 安全 group");

        JsonNode perms = body.path("data").path("perms");
        assertTrue(perms.isArray() && perms.size() > 0, "the perms set must be present");
        List<String> permStrings = new ArrayList<>();
        perms.forEach(perm -> permStrings.add(perm.asText()));
        assertTrue(permStrings.contains("system:user:list"), "perms must carry the user-management string");
        assertTrue(permStrings.contains("system:log:list"),
                "perms must carry the log-query string: " + permStrings);
    }

    // ===================== login lockout (login-lockout task 2.1, design T1-T4) =====================
    // Probes use the pw_u_ fixture family (already in the base's sweep namespace) — the
    // real-password probes are created through the admin endpoint and their passwords
    // may be freely reset, never touching RUN_ACCOUNT or the seeded admin.

    private static final String PROBE_PASSWORD = "lock-pass-123";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** Creates a live probe user through the admin endpoint; returns its username. */
    private String createProbeUser(String username) throws Exception {
        ResponseEntity<String> adminLogin = postJson(LOGIN_URL, runAdminLoginBody());
        String adminToken = parse(adminLogin).path("data").path("token").asText("");
        ResponseEntity<String> created = postJsonAuthed("/api/v1/system/users",
                "{\"username\":\"" + username + "\",\"password\":\"" + encryptTestPassword(PROBE_PASSWORD)
                        + "\",\"name\":\"lockout probe\"}", adminToken);
        assertEquals("200", parse(created).path("code").asText(),
                "the probe user must be created: " + created.getBody());
        return username;
    }

    private ResponseEntity<String> postJsonAuthed(String url, String json, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token); // Constants.TOKEN_PREFIX
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    private String wrongAttempts(String username, int times) throws Exception {
        String lastCode = null;
        for (int i = 0; i < times; i++) {
            ResponseEntity<String> response = postJson(LOGIN_URL, loginBody(username, "wrong-" + i));
            lastCode = parse(response).path("code").asText();
        }
        return lastCode;
    }

    @Test
    void failureThresholdLocksAndNextAttemptAnswersA0211EvenWithCorrectPassword() throws Exception {
        String probe = createProbeUser("pw_u_lock_th_" + System.currentTimeMillis());
        try {
            // max-attempts (5) wrong passwords: each answers A0210 — the THRESHOLD
            // attempt itself is still a wrong-try answer (spec).
            assertEquals("A0210", wrongAttempts(probe, 5),
                    "the 5th wrong try still answers A0210, not A0211");

            // The NEXT attempt — even the CORRECT password — is the lock answer.
            ResponseEntity<String> correct = postJson(LOGIN_URL, loginBody(probe, PROBE_PASSWORD));
            JsonNode body = parse(correct);
            assertEquals("A0211", body.path("code").asText(),
                    "a locked account rejects the correct password before any BCrypt round");
            assertFalse(body.hasNonNull("data"), "no token is issued while locked");
        } finally {
            stringRedisTemplate.delete("login:fail:" + probe);
            stringRedisTemplate.delete("login:lock:" + probe);
        }
    }

    @Test
    void unknownAccountProbesNeverCreateLockKeys() throws Exception {
        String unknown = "no.such.user." + System.currentTimeMillis(); // unique - shared DB
        ResponseEntity<String> response = postJson(LOGIN_URL, loginBody(unknown, "whatever"));

        assertEquals("A0201", parse(response).path("code").asText(),
                "an unknown account still maps to A0201");
        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey("login:fail:" + unknown)),
                "an unknown username creates NO failure counter");
        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey("login:lock:" + unknown)),
                "an unknown username can never be locked");
    }

    @Test
    void plaintextAttemptsDoNotCountTowardLockout() throws Exception {
        String probe = "pw_u_lock_pt_" + System.currentTimeMillis();
        try {
            // A0400 (transport failure) must leave the counter untouched — the fail key
            // never comes into existence for a username that only sent plaintext.
            ResponseEntity<String> response = postJson(LOGIN_URL,
                    "{\"username\":\"" + probe + "\",\"password\":\"admin123\"}");
            assertEquals("A0400", parse(response).path("code").asText());
            assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey("login:fail:" + probe)),
                    "a transport-format failure does NOT count toward lockout");
        } finally {
            stringRedisTemplate.delete("login:fail:" + probe);
            stringRedisTemplate.delete("login:lock:" + probe);
        }
    }

    @Test
    void successfulLoginClearsTheFailureCounter() throws Exception {
        String probe = createProbeUser("pw_u_lock_sc_" + System.currentTimeMillis());
        try {
            wrongAttempts(probe, 4); // sub-threshold
            ResponseEntity<String> success = postJson(LOGIN_URL, loginBody(probe, PROBE_PASSWORD));
            assertEquals("200", parse(success).path("code").asText(), "sub-threshold account logs in fine");

            assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey("login:fail:" + probe)),
                    "success clears the counter — earlier failures never accumulate");

            // The full threshold is needed AGAIN from zero: 5 fresh wrongs lock, so the
            // 6th attempt answers A0211 (with only 4 old + 1 new, it would already lock).
            wrongAttempts(probe, 5);
            ResponseEntity<String> next = postJson(LOGIN_URL, loginBody(probe, PROBE_PASSWORD));
            assertEquals("A0211", parse(next).path("code").asText(),
                    "the counter restarted from zero after the successful login");
        } finally {
            stringRedisTemplate.delete("login:fail:" + probe);
            stringRedisTemplate.delete("login:lock:" + probe);
        }
    }

    @Test
    void adminPasswordResetUnlocksALockedAccount() throws Exception {
        String probe = createProbeUser("pw_u_lock_rs_" + System.currentTimeMillis());
        try {
            wrongAttempts(probe, 5);
            assertEquals("A0211", parse(postJson(LOGIN_URL, loginBody(probe, PROBE_PASSWORD)))
                    .path("code").asText(), "precondition: the probe is locked");

            // Admin reset (emergency unlock channel, design D6) — then the NEW password
            // logs in immediately.
            ResponseEntity<String> adminLogin = postJson(LOGIN_URL, runAdminLoginBody());
            String adminToken = parse(adminLogin).path("data").path("token").asText("");
            ResponseEntity<String> listed = restTemplate.exchange("/api/v1/system/users/list",
                    HttpMethod.GET, new HttpEntity<>(authedJsonHeaders(adminToken)), String.class);
            JsonNode users = parse(listed).path("data");
            int probeId = -1;
            for (JsonNode u : users) {
                if (probe.equals(u.path("username").asText())) {
                    probeId = u.path("uid").asInt();
                }
            }
            assertTrue(probeId > 0, "the probe user id must resolve for the reset call");
            ResponseEntity<String> reset = restTemplate.exchange(
                    "/api/v1/system/users/" + probeId + "/password", HttpMethod.PUT,
                    new HttpEntity<>("{\"newPassword\":\"" + encryptTestPassword("reset-pass-456") + "\"}",
                            authedJsonHeaders(adminToken)), String.class);
            assertEquals("200", parse(reset).path("code").asText(), "the admin reset must succeed");

            ResponseEntity<String> recovered = postJson(LOGIN_URL, loginBody(probe, "reset-pass-456"));
            assertEquals("200", parse(recovered).path("code").asText(),
                    "the reset cleared the lock — the account logs in immediately");
        } finally {
            stringRedisTemplate.delete("login:fail:" + probe);
            stringRedisTemplate.delete("login:lock:" + probe);
        }
    }

    @Test
    void plantedShortTtlLockExpiresAndTheAccountRecovers() throws Exception {
        String probe = createProbeUser("pw_u_lock_ex_" + System.currentTimeMillis());
        try {
            // Design T2 at the LOGIN level: the gate reads the KEY. Plant a 2s lock
            // directly — A0211 while present, normal loop after TTL.
            stringRedisTemplate.opsForValue().set("login:lock:" + probe, "1",
                    java.time.Duration.ofSeconds(2));
            ResponseEntity<String> locked = postJson(LOGIN_URL, loginBody(probe, PROBE_PASSWORD));
            assertEquals("A0211", parse(locked).path("code").asText());

            Thread.sleep(2500);
            ResponseEntity<String> recovered = postJson(LOGIN_URL, loginBody(probe, PROBE_PASSWORD));
            assertEquals("200", parse(recovered).path("code").asText(),
                    "the TTL elapsed — the normal login loop resumes");
        } finally {
            stringRedisTemplate.delete("login:fail:" + probe);
            stringRedisTemplate.delete("login:lock:" + probe);
        }
    }

    private HttpHeaders authedJsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    private ResponseEntity<String> postJson(String url, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private static JsonNode menuWithMid(JsonNode nodes, int mid) {
        if (!nodes.isArray()) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (node.path("mid").asInt(-1) == mid) {
                return node;
            }
            JsonNode found = menuWithMid(node.path("children"), mid);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
