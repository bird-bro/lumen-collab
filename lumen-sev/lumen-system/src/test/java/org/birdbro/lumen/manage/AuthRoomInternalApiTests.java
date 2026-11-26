package org.birdbro.lumen.manage;

import java.util.Map;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The INTERNAL exam-room reads proof (us-order-exam-room-sev task 2.1; spec delta api
 * "Internal Exam Room Reads"): the service-to-service rooms family under
 * {@code /api/v1/system/internal/org-rooms/**} is X-Internal-Key guarded (the
 * NumberIssueController precedent - NO JWT), the list returns the department's ENABLED
 * rooms as slim {@code {id, name}} rows ordered by id, and the detail carries the
 * validation fields ({@code did}/{@code enable}) the station's create-time check reads.
 * The console registry endpoints are untouched. Timestamped fixtures, hard-deleted
 * afterwards (user ruling 2026-11-07).
 */
class AuthRoomInternalApiTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/system/login";
    private static final String ORGS_URL = "/api/v1/system/orgs";
    private static final String ORG_DEPS_URL = "/api/v1/system/org-deps";
    private static final String ROOMS_URL = "/api/v1/system/org-rooms";
    private static final String INTERNAL_LIST_URL = "/api/v1/system/internal/org-rooms/list";
    private static final String INTERNAL_DETAIL_URL = "/api/v1/system/internal/org-rooms";

    /** The test key both sides agree on (application-test.yml, the /issue family). */
    private static final String INTERNAL_KEY = "nr-tests-internal-key";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void internalListReturnsDeptEnabledRoomsOrderedById() throws Exception {
        long ts = System.currentTimeMillis();
        int orgId = createOrg("诊室院区_il_" + ts);
        int depId = createDepartment(orgId, "ut_room_dep_il" + ts, "超声科");
        int first = createRoom(orgId, depId, "ut_room_il_a" + ts);
        int second = createRoom(orgId, depId, "ut_room_il_b" + ts);
        int disabled = createRoom(orgId, depId, "ut_room_il_c" + ts);
        disableRoom(disabled);

        ResponseEntity<String> response = exchangeWithKey(HttpMethod.GET,
                INTERNAL_LIST_URL + "?depId=" + depId);
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(),
                "the internal list must succeed with the shared key: " + body);
        JsonNode rooms = body.path("data");
        assertEquals(2, rooms.size(),
                "only the ENABLED rooms of the department list: " + body);
        assertEquals(first, rooms.get(0).path("id").asInt(-1),
                "the list orders by id ascending");
        assertEquals(second, rooms.get(1).path("id").asInt(-1),
                "the list orders by id ascending");
        assertEquals("ut_room_il_a" + ts, rooms.get(0).path("name").asText(null),
                "the slim row carries the room name");
        assertTrue(rooms.get(0).path("did").isMissingNode(),
                "the list row stays SLIM ({id, name} only): " + rooms.get(0));

        hardDeleteRoom(first);
        hardDeleteRoom(second);
        hardDeleteRoom(disabled);
        hardDeleteDepartment(depId);
        hardDeleteOrg(orgId);
    }

    @Test
    void internalDetailCarriesTheValidationFields() throws Exception {
        long ts = System.currentTimeMillis();
        int orgId = createOrg("诊室院区_id_" + ts);
        int depId = createDepartment(orgId, "ut_room_dep_id" + ts, "超声科");
        int roomId = createRoom(orgId, depId, "ut_room_id_a" + ts);

        ResponseEntity<String> response = exchangeWithKey(HttpMethod.GET,
                INTERNAL_DETAIL_URL + "/" + roomId);
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(),
                "the internal detail must succeed with the shared key: " + body);
        JsonNode room = body.path("data");
        assertEquals(roomId, room.path("id").asInt(-1), "the detail echoes the room id");
        assertEquals(orgId, room.path("oid").asInt(-1), "the detail carries oid");
        assertEquals(depId, room.path("did").asInt(-1),
                "the detail carries did - the station's department-match field");
        assertEquals(1, room.path("enable").asInt(-1),
                "the detail carries enable - the station's enabled check field");

        hardDeleteRoom(roomId);
        hardDeleteDepartment(depId);
        hardDeleteOrg(orgId);
    }

    @Test
    void keylessCallerIsRefused() throws Exception {
        long ts = System.currentTimeMillis();
        int orgId = createOrg("诊室院区_ik_" + ts);
        int depId = createDepartment(orgId, "ut_room_dep_ik" + ts, "超声科");

        // NO X-Internal-Key header - the guard refuses before the controller body runs
        ResponseEntity<String> response = exchangeJson(HttpMethod.GET,
                INTERNAL_LIST_URL + "?depId=" + depId, null, null);
        assertEquals(401, response.getStatusCodeValue(),
                "a keyless internal call must be refused 401: " + response);
        assertTrue(parse(response).path("code").asText("").startsWith("401"),
                "the refusal uses the 401 envelope family");

        // a WRONG key refuses the same way (fail-closed, not a fuzzy match)
        ResponseEntity<String> wrong = exchangeWithKey(HttpMethod.GET,
                INTERNAL_LIST_URL + "?depId=" + depId, "not-the-key");
        assertEquals(401, wrong.getStatusCodeValue(),
                "a wrong internal key must be refused 401: " + wrong);

        hardDeleteDepartment(depId);
        hardDeleteOrg(orgId);
    }

    // ===================== fixtures =====================

    private String adminToken() throws Exception {
        ResponseEntity<String> loginResponse = exchangeJson(HttpMethod.POST, LOGIN_URL,
                runAdminLoginBody(), null);
        JsonNode loginBody = parse(loginResponse);
        assertEquals("200", loginBody.path("code").asText(), "admin login must hand out the token");
        String token = loginBody.path("data").path("token").asText("");
        assertTrue(!token.isEmpty(), "the admin token must be present");
        return token;
    }

    private int createOrg(String name) throws Exception {
        String token = adminToken();
        ResponseEntity<String> response = exchangeJson(HttpMethod.POST, ORGS_URL,
                "{\"upId\":1,\"name\":\"" + name + "\"}", token);
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(), "org fixture create must succeed: " + body);
        return body.path("data").asInt(-1);
    }

    private int createDepartment(int orgId, String code, String name) throws Exception {
        String token = adminToken();
        ResponseEntity<String> response = exchangeJson(HttpMethod.POST, ORG_DEPS_URL,
                "{\"orgId\":" + orgId + ",\"code\":\"" + code + "\",\"name\":\"" + name + "\"}",
                token);
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(),
                "department fixture create must succeed: " + body);
        return body.path("data").asInt(-1);
    }

    private int createRoom(int orgId, int depId, String name) throws Exception {
        String token = adminToken();
        ResponseEntity<String> response = exchangeJson(HttpMethod.POST, ROOMS_URL,
                "{\"oid\":" + orgId + ",\"did\":" + depId + ",\"name\":\"" + name
                        + "\",\"enable\":1}", token);
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(), "room fixture create must succeed: "
                + body);
        return body.path("data").asInt(-1);
    }

    private void disableRoom(int roomId) throws Exception {
        String token = adminToken();
        ResponseEntity<String> response = exchangeJson(HttpMethod.PUT,
                ROOMS_URL + "/" + roomId + "/status", "{\"enable\":0}", token);
        assertEquals("200", parse(response).path("code").asText(),
                "room fixture disable must succeed");
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private ResponseEntity<String> exchangeJson(HttpMethod method, String url, String body,
            String token) throws Exception {
        return exchangeJson(method, url, body, token, null);
    }

    private ResponseEntity<String> exchangeJson(HttpMethod method, String url, String body,
            String token, String overrideKey) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set("Authorization", "Bearer " + token);
        }
        if (overrideKey != null) {
            headers.set("X-Internal-Key", overrideKey);
        }
        return restTemplate.exchange(url, method,
                new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> exchangeWithKey(HttpMethod method, String url)
            throws Exception {
        return exchangeWithKey(method, url, INTERNAL_KEY);
    }

    private ResponseEntity<String> exchangeWithKey(HttpMethod method, String url, String key)
            throws Exception {
        return exchangeJson(method, url, null, null, key);
    }

    private void hardDeleteRoom(int id) {
        jdbcTemplate.update("DELETE FROM system.org_room WHERE id = ?", id);
    }

    private void hardDeleteDepartment(int id) {
        jdbcTemplate.update("DELETE FROM system.org_dep WHERE id = ?", id);
    }

    private void hardDeleteOrg(int id) {
        jdbcTemplate.update("DELETE FROM system.org_info WHERE id = ?", id);
    }
}
