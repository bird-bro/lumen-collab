package org.birdbro.lumen.system;

import java.util.ArrayList;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void validAdminLoginReturnsTokenAndProfile() throws Exception {
        ResponseEntity<String> response = postJson(LOGIN_URL, "{\"username\":\"admin\",\"password\":\"admin123\"}");

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
        ResponseEntity<String> response = postJson(LOGIN_URL, "{\"username\":\"admin\",\"password\":\"totally-wrong\"}");

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
                "{\"username\":\"" + unknown + "\",\"password\":\"whatever\"}");

        assertEquals(200, response.getStatusCodeValue());
        JsonNode body = parse(response);
        assertEquals("A0201", body.path("code").asText(), "an unknown account must map to the A0201 code");
        assertFalse(body.hasNonNull("data"));
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
        ResponseEntity<String> loginResponse = postJson(LOGIN_URL, "{\"username\":\"admin\",\"password\":\"admin123\"}");
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

        // The seeded menu tree: roots 1/3/4/5, user management (2) nests under the system
        // directory (1), the audit button (6) nests under the radiology page (3).
        JsonNode menus = body.path("data").path("menus");
        assertTrue(menus.isArray() && menus.size() >= 4, "the seeded tree has four roots");
        JsonNode systemDirectory = menuWithMid(menus, 1);
        assertNotNull(systemDirectory, "menu 1 系统管理 must be a tree root");
        assertEquals("系统管理", systemDirectory.path("title").asText());
        assertNotNull(menuWithMid(systemDirectory.path("children"), 2),
                "user management (menu 2) nests under the system directory");
        JsonNode radiologyPage = menuWithMid(menus, 3);
        assertNotNull(radiologyPage, "menu 3 放射科 must be a tree root");
        assertNotNull(menuWithMid(radiologyPage.path("children"), 6),
                "the audit button (menu 6) nests under the radiology page");

        JsonNode perms = body.path("data").path("perms");
        assertTrue(perms.isArray() && perms.size() > 0, "the perms set must be present");
        List<String> permStrings = new ArrayList<>();
        perms.forEach(perm -> permStrings.add(perm.asText()));
        assertTrue(permStrings.contains("system:user:list"), "perms must carry the user-management string");
        assertTrue(permStrings.contains("radiology:report:audit"),
                "perms must carry the seeded audit-button string: " + permStrings);
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
