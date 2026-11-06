package org.birdbro.lumen.system;

import java.util.TimerTask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.birdbro.common.core.pojo.user.UserLogin;
import org.birdbro.framework.manager.factory.AsyncFactory;
import org.birdbro.framework.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * User-Agent resilience (adaptation (10), user decision 2026-11-04): API-first clients
 * (scripts, service-to-service calls, probes) send NO User-Agent or an UNVERSIONED one -
 * the copied machinery chained {@code getBrowserVersion().getVersion()} and NPE'd, so
 * sign rows were silently dropped (async pool) and {@code @Log} endpoints broke
 * (synchronous aspect). The copied bitwalker lineage must tolerate both.
 *
 * <p>Shared long-lived database: probe rows are keyed by the {@code ua-probe} account
 * name and left in place (log tables are append-write by design).</p>
 *
 * @author birdbro
 */
class AuthUserAgentResilienceTests extends AuthIntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenService tokenService;

    @Test
    void recordForSignSurvivesMissingUserAgent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            TimerTask task = AsyncFactory.recordForSign(
                    "ua-probe", 10, "login", "no-UA probe");
            task.run(); // RED before adaptation (10): NPE inside the copied parse chain
            Integer rows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.log_sign WHERE account = 'ua-probe'",
                    Integer.class);
            assertTrue(rows != null && rows >= 1,
                    "a UA-less sign event must still land in public.log_sign");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void setUserAgentSurvivesMissingUserAgent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            UserLogin login = new UserLogin();
            tokenService.setUserAgent(login); // RED before adaptation (10): parse-chain NPE
            assertTrue(login.getAgent() != null,
                    "AgentInfo must be populated (browser/os = UNKNOWN) without a UA header");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void annotatedOrgCreateSurvivesUnversionedUserAgent() throws Exception {
        // Regression sentinel: bitwalker derives a Version even for an unversioned UA, so
        // this path was never broken - it guards against future parse regressions. The
        // MISSING-UA synchronous path is covered by setUserAgentSurvivesMissingUserAgent.
        String token = adminToken();
        long ts = System.currentTimeMillis();
        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST,
                "/api/v1/system/orgs",
                "{\"upId\":1,\"name\":\"UA探针机构_" + ts + "\"}",
                "lumen-probe", token); // UNVERSIONED UA - bitwalker still derives a Version
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(),
                "a @Log write endpoint must survive an unversioned UA: " + createBody);
        int createdId = createBody.path("data").asInt(-1);
        assertTrue(createdId > 0, "the created org id must come back");
        Integer logRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.log_info WHERE uid = ? AND name = 'create'",
                Integer.class, runUserId());
        assertTrue(logRows != null && logRows >= 1,
                "the @Log row must land even with an unversioned UA");
        jdbcTemplate.update("UPDATE system.org_info SET deleted = 1 WHERE id = ?", createdId);
    }

    private String adminToken() throws Exception {
        ResponseEntity<String> loginResponse = postJson("/api/v1/system/login",
                runAdminLoginBody(), null);
        JsonNode loginBody = parse(loginResponse);
        assertEquals("200", loginBody.path("code").asText(),
                "admin login must hand out the token");
        return loginBody.path("data").path("token").asText("");
    }

    private ResponseEntity<String> postJson(String url, String json, String userAgent) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (userAgent != null) {
            headers.set("User-Agent", userAgent);
        } else {
            headers.remove("User-Agent");
        }
        return restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(json, headers), String.class);
    }

    private ResponseEntity<String> exchangeJson(HttpMethod method, String url, String json,
            String userAgent, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (userAgent != null) {
            headers.set("User-Agent", userAgent);
        } else {
            headers.remove("User-Agent");
        }
        headers.set("Authorization", "Bearer " + token);
        return restTemplate.exchange(url, method, new HttpEntity<>(json, headers),
                String.class);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return new ObjectMapper().readTree(response.getBody());
    }
}
