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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exam-room registry endpoint proof (org-device-room-api task 3.2; spec deltas api
 * "Exam Room Registry Endpoints" / "Exam Room Binding Validation" / "Exam Room Update
 * and Delete"): the page/detail reads resolve the bound-device summary by JOIN (D2 -
 * the room row itself stays device-free), create binds by REFERENCE, dead/foreign
 * references reject A0400 no-change, the update rebinds AND clears (D9), the status
 * toggle mutates only enable (D6), and delete is logical. Timestamped fixtures,
 * hard-deleted afterwards (user ruling 2026-11-07).
 */
class AuthRoomApiTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/system/login";
    private static final String ORGS_URL = "/api/v1/system/orgs";
    private static final String ORG_DEPS_URL = "/api/v1/system/org-deps";
    private static final String DEVICES_URL = "/api/v1/system/org-devices";
    private static final String ROOMS_URL = "/api/v1/system/org-rooms";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void roomPageCarriesBoundDeviceSummaryAndUnboundNulls() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int orgId = createOrg("诊室院区_page_" + ts);
        int depId = createDepartment(orgId, "ut_room_dep_p" + ts, "放射科");
        int deviceId = createDevice("ut_dev_room_p" + ts, "CT-P" + ts, orgId, "AE_P");
        int boundRoom = createRoom(orgId, depId, "ut_room_bound_" + ts, deviceId);
        int freeRoom = createRoom(orgId, depId, "ut_room_free_" + ts, null);

        ResponseEntity<String> page = exchangeJson(HttpMethod.GET,
                ROOMS_URL + "/page?pageNum=1&pageSize=50&orgId=" + orgId, null, token);
        JsonNode body = parse(page);
        assertEquals("200", body.path("code").asText(), "the org-scoped page must succeed");
        assertEquals(2, body.path("data").path("total").asLong(-1),
                "both rooms must list: " + body);

        JsonNode bound = findRecord(body, boundRoom);
        assertEquals("CT-P" + ts, bound.path("devName").asText(null),
                "the bound room must carry the device name by JOIN");
        assertEquals("AE_P_" + ts, bound.path("devAe").asText(null),
                "the bound room must carry the device AE by JOIN");
        assertEquals("10.0.0." + (ts % 250), bound.path("devIp").asText(null),
                "the bound room must carry the device IP by JOIN");
        assertEquals("104", bound.path("devPort").asText(null),
                "the bound room must carry the device port by JOIN");

        JsonNode free = findRecord(body, freeRoom);
        assertTrue(free.path("devId").isNull() || free.path("devId").asInt(0) == 0,
                "the unbound room's devId must be null");
        assertTrue(free.path("devName").isNull() || free.path("devName").asText("").isEmpty(),
                "the unbound room's device summary must be null");
        // Newest first.
        assertEquals(freeRoom, body.path("data").path("records").get(0).path("id").asInt(-1),
                "the page must be ordered by id descending");

        hardDeleteRoom(boundRoom);
        hardDeleteRoom(freeRoom);
        hardDeleteDevice(deviceId);
        hardDeleteDepartment(depId);
        hardDeleteOrg(orgId);
    }

    @Test
    void roomDetailEchoesTheBoundDevice() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int orgId = createOrg("诊室院区_detail_" + ts);
        int depId = createDepartment(orgId, "ut_room_dep_d" + ts, "放射科");
        int deviceId = createDevice("ut_dev_room_d" + ts, "CT-D" + ts, orgId, "AE_D");
        int roomId = createRoom(orgId, depId, "ut_room_detail_" + ts, deviceId);

        ResponseEntity<String> detail = exchangeJson(HttpMethod.GET,
                ROOMS_URL + "/" + roomId, null, token);
        JsonNode body = parse(detail);
        assertEquals("200", body.path("code").asText(), "the detail must succeed: " + body);
        assertEquals(deviceId, body.path("data").path("devId").asInt(-1),
                "the detail must echo the bound device id");
        assertEquals("CT-D" + ts, body.path("data").path("devName").asText(null),
                "the detail must echo the device name");
        assertEquals("AE_D_" + ts, body.path("data").path("devAe").asText(null),
                "the detail must echo the device AE");

        hardDeleteRoom(roomId);
        hardDeleteDevice(deviceId);
        hardDeleteDepartment(depId);
        hardDeleteOrg(orgId);
    }

    @Test
    void createRoomBindsDeviceByReferenceOnly() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int orgId = createOrg("诊室院区_create_" + ts);
        int depId = createDepartment(orgId, "ut_room_dep_c" + ts, "放射科");
        int deviceId = createDevice("ut_dev_room_c" + ts, "CT-C" + ts, orgId, "AE_C");

        ResponseEntity<String> create = exchangeJson(HttpMethod.POST, ROOMS_URL,
                "{\"oid\":" + orgId + ",\"did\":" + depId + ",\"name\":\"ut_room_ref_" + ts
                        + "\",\"code\":\"CR-" + ts + "\",\"enable\":1"
                        + ",\"devId\":" + deviceId
                        + ",\"callIp\":\"10.1.1.10\",\"callPort\":\"3000\",\"callMac\":\"AA:BB:CC\"}",
                token);
        JsonNode createBody = parse(create);
        assertEquals("200", createBody.path("code").asText(),
                "the create must succeed: " + createBody);
        int roomId = createBody.path("data").asInt(-1);
        assertTrue(roomId > 0, "the create must return the new room id");

        // The room row stores ONLY the reference - no device field of the OPS shape.
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT dev_id, call_ip, call_port, call_mac, code, enable"
                        + " FROM system.org_room WHERE id = ?", roomId);
        assertEquals(deviceId, ((Number) row.get("dev_id")).intValue(),
                "the device must bind by dev_id reference");
        assertEquals("10.1.1.10", row.get("call_ip"), "the 叫号 block must persist on the room");
        assertEquals("3000", row.get("call_port"), "the call port must persist");
        assertEquals("AA:BB:CC", row.get("call_mac"), "the call MAC must persist");
        assertEquals("CR-" + ts, row.get("code"), "the room code must persist");
        Integer aeCols = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='system'"
                        + " AND table_name='org_room' AND column_name IN ('ae','dev_ae','ip')",
                Integer.class);
        assertEquals(0, aeCols.intValue(),
                "the room table must carry NO inline device column (the split ruling)");

        hardDeleteRoom(roomId);
        hardDeleteDevice(deviceId);
        hardDeleteDepartment(depId);
        hardDeleteOrg(orgId);
    }

    @Test
    void createRoomRejectsDeadOrForeignReferences() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int orgA = createOrg("诊室院区_guardA_" + ts);
        int orgB = createOrg("诊室院区_guardB_" + ts);
        int depA = createDepartment(orgA, "ut_room_dep_ga" + ts, "A院放射科");
        int depB = createDepartment(orgB, "ut_room_dep_gb" + ts, "B院放射科");
        int deviceB = createDevice("ut_dev_room_gb" + ts, "CT-B" + ts, orgB, "AE_B");

        // A device of ANOTHER org.
        ResponseEntity<String> foreignDev = exchangeJson(HttpMethod.POST, ROOMS_URL,
                buildRoomBody(orgA, depA, "ut_room_fd_" + ts, deviceB), token);
        assertEquals("A0400", parse(foreignDev).path("code").asText(),
                "a foreign-org devId must reject A0400: " + foreignDev);

        // A deleted device of the same org.
        int deadDevice = createDevice("ut_dev_room_dead" + ts, "CT-DEAD" + ts, orgA, "AE_X");
        hardDeleteDevice(deadDevice);
        ResponseEntity<String> deadDev = exchangeJson(HttpMethod.POST, ROOMS_URL,
                buildRoomBody(orgA, depA, "ut_room_dd_" + ts, deadDevice), token);
        assertEquals("A0400", parse(deadDev).path("code").asText(),
                "a deleted devId must reject A0400: " + deadDev);

        // A did of ANOTHER org.
        ResponseEntity<String> foreignDep = exchangeJson(HttpMethod.POST, ROOMS_URL,
                buildRoomBody(orgA, depB, "ut_room_fdep_" + ts, null), token);
        assertEquals("A0400", parse(foreignDep).path("code").asText(),
                "a foreign-org did must reject A0400: " + foreignDep);

        // A duplicate room name within the SAME department (D4).
        int original = createRoom(orgA, depA, "ut_room_dup_" + ts, null);
        ResponseEntity<String> duplicate = exchangeJson(HttpMethod.POST, ROOMS_URL,
                buildRoomBody(orgA, depA, "ut_room_dup_" + ts, null), token);
        assertEquals("A0400", parse(duplicate).path("code").asText(),
                "a duplicate name within one department must reject A0400: " + duplicate);

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.org_room WHERE name LIKE ?",
                Integer.class, "ut_room_%_" + ts);
        assertEquals(1, rows.intValue(),
                "only the original room may exist after the rejections");

        hardDeleteRoom(original);
        hardDeleteDevice(deviceB);
        hardDeleteDepartment(depA);
        hardDeleteDepartment(depB);
        hardDeleteOrg(orgA);
        hardDeleteOrg(orgB);
    }

    @Test
    void updateRoomRebindsClearsAndNullAwareClearsCallFields() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int orgId = createOrg("诊室院区_upd_" + ts);
        int depId = createDepartment(orgId, "ut_room_dep_u" + ts, "放射科");
        int deviceA = createDevice("ut_dev_room_ua" + ts, "CT-UA" + ts, orgId, "AE_UA");
        int deviceB = createDevice("ut_dev_room_ub" + ts, "CT-UB" + ts, orgId, "AE_UB");
        int roomId = createRoom(orgId, depId, "ut_room_upd_" + ts, deviceA);

        // Rebind to device B with the full surface.
        ResponseEntity<String> rebind = exchangeJson(HttpMethod.PUT, ROOMS_URL + "/" + roomId,
                "{\"oid\":" + orgId + ",\"did\":" + depId + ",\"name\":\"ut_room_upd_" + ts
                        + "\",\"devId\":" + deviceB
                        + ",\"callIp\":\"10.2.2.20\"}", token);
        assertEquals("200", parse(rebind).path("code").asText(),
                "the rebind update must succeed: " + rebind);
        Map<String, Object> rebound = jdbcTemplate.queryForMap(
                "SELECT dev_id, call_ip FROM system.org_room WHERE id = ?", roomId);
        assertEquals(deviceB, ((Number) rebound.get("dev_id")).intValue(),
                "the update must rebind dev_id to device B");
        assertEquals("10.2.2.20", rebound.get("call_ip"), "the call ip must update");

        // Clear the binding (devId absent = null) - D9 null-aware.
        ResponseEntity<String> clear = exchangeJson(HttpMethod.PUT, ROOMS_URL + "/" + roomId,
                "{\"oid\":" + orgId + ",\"did\":" + depId + ",\"name\":\"ut_room_upd_" + ts
                        + "\"}", token);
        assertEquals("200", parse(clear).path("code").asText(),
                "the clearing update must succeed: " + clear);
        Map<String, Object> cleared = jdbcTemplate.queryForMap(
                "SELECT dev_id, call_ip FROM system.org_room WHERE id = ?", roomId);
        assertNull(cleared.get("dev_id"), "an absent devId must CLEAR the binding");
        assertNull(cleared.get("call_ip"), "an absent call field must CLEAR (D9)");

        hardDeleteRoom(roomId);
        hardDeleteDevice(deviceA);
        hardDeleteDevice(deviceB);
        hardDeleteDepartment(depId);
        hardDeleteOrg(orgId);
    }

    @Test
    void roomStatusTogglesOnlyEnable() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int orgId = createOrg("诊室院区_status_" + ts);
        int depId = createDepartment(orgId, "ut_room_dep_s" + ts, "放射科");
        int roomId = createRoom(orgId, depId, "ut_room_status_" + ts, null);

        ResponseEntity<String> toggle = exchangeJson(HttpMethod.PUT,
                ROOMS_URL + "/" + roomId + "/status", "{\"enable\":0}", token);
        JsonNode toggleBody = parse(toggle);
        assertEquals("200", toggleBody.path("code").asText(),
                "the status toggle must succeed: " + toggleBody);
        assertTrue(toggleBody.path("data").asBoolean(), "the toggle must confirm true");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT enable, name FROM system.org_room WHERE id = ?", roomId);
        assertEquals(0, ((Number) row.get("enable")).intValue(),
                "only the enable column may change");
        assertEquals("ut_room_status_" + ts, row.get("name"),
                "the name must stay untouched by the toggle");

        hardDeleteRoom(roomId);
        hardDeleteDepartment(depId);
        hardDeleteOrg(orgId);
    }

    @Test
    void deleteRoomIsLogicalAndMissingIdRejects() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int orgId = createOrg("诊室院区_del_" + ts);
        int depId = createDepartment(orgId, "ut_room_dep_x" + ts, "放射科");
        int roomId = createRoom(orgId, depId, "ut_room_del_" + ts, null);

        ResponseEntity<String> delete = exchangeJson(HttpMethod.DELETE,
                ROOMS_URL + "/" + roomId, null, token);
        JsonNode deleteBody = parse(delete);
        assertEquals("200", deleteBody.path("code").asText(),
                "the room delete must succeed: " + deleteBody);
        Integer deletedFlag = jdbcTemplate.queryForObject(
                "SELECT deleted FROM system.org_room WHERE id = ?", Integer.class, roomId);
        assertEquals(1, deletedFlag.intValue(), "the delete must be logical");

        ResponseEntity<String> missing = exchangeJson(HttpMethod.DELETE,
                ROOMS_URL + "/99999999", null, token);
        assertEquals("A0400", parse(missing).path("code").asText(),
                "a missing room id must reject A0400: " + missing);

        hardDeleteRoom(roomId);
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

    /** Departments created by this run (auto-provisioned did anchors included). */
    private final java.util.List<Integer> fixtureDepIds = new java.util.ArrayList<>();

    private int createDepartment(int orgId, String code, String name) throws Exception {
        String token = adminToken();
        ResponseEntity<String> response = exchangeJson(HttpMethod.POST, ORG_DEPS_URL,
                "{\"orgId\":" + orgId + ",\"code\":\"" + code + "\",\"name\":\"" + name + "\"}",
                token);
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(),
                "department fixture create must succeed: " + body);
        int depId = body.path("data").asInt(-1);
        fixtureDepIds.add(depId);
        return depId;
    }

    private int createDevice(String code, String name, int orgId, String aePrefix)
            throws Exception {
        String token = adminToken();
        // Second-round ruling: did is REQUIRED - the fixture auto-provisions the
        // 使用科室 (unique per device code; sweep-registered "ut_dev_dep_" prefix).
        String stem = code.replaceAll("[^a-zA-Z0-9]", "");
        int depId = createDepartment(orgId, "ut_dev_dep_"
                + stem.substring(Math.max(0, stem.length() - 18)), "定科设备科室");
        ResponseEntity<String> response = exchangeJson(HttpMethod.POST, DEVICES_URL,
                "{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"type\":\"CT\""
                        + ",\"status\":1,\"tag\":10,\"oid\":" + orgId + ",\"did\":" + depId
                        + ",\"ae\":\"" + aePrefix + "_" + ts(code)
                        + "\",\"ip\":\"10.0.0." + (ts(code) % 250)
                        + "\",\"port\":\"104\"}", token);
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(), "device fixture create must succeed: "
                + body);
        return body.path("data").asInt(-1);
    }

    /** The timestamp-ish suffix of a fixture code (ae uniqueness within one test run). */
    private long ts(String code) {
        return Long.parseLong(code.replaceAll("[^0-9]", ""));
    }

    private String buildRoomBody(int orgId, int depId, String name, Integer devId) {
        return "{\"oid\":" + orgId + ",\"did\":" + depId + ",\"name\":\"" + name
                + "\",\"enable\":1" + (devId == null ? "" : ",\"devId\":" + devId) + "}";
    }

    private int createRoom(int orgId, int depId, String name, Integer devId) throws Exception {
        String token = adminToken();
        ResponseEntity<String> response = exchangeJson(HttpMethod.POST, ROOMS_URL,
                buildRoomBody(orgId, depId, name, devId), token);
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(), "room fixture create must succeed: "
                + body);
        return body.path("data").asInt(-1);
    }

    private JsonNode findRecord(JsonNode pageBody, int id) {
        for (JsonNode record : pageBody.path("data").path("records")) {
            if (record.path("id").asInt(-1) == id) {
                return record;
            }
        }
        return pageBody.path("data").path("records").get(0);
    }

    private void hardDeleteRoom(int id) {
        jdbcTemplate.update("DELETE FROM system.org_room WHERE id = ?", id);
    }

    private void hardDeleteDevice(int id) {
        jdbcTemplate.update("DELETE FROM system.org_device WHERE id = ?", id);
    }

    private void hardDeleteDepartment(int id) {
        jdbcTemplate.update("DELETE FROM system.org_dep WHERE id = ?", id);
    }

    private void hardDeleteOrg(int id) {
        // Departments before their campus (org_dep.org_id physical FK).
        for (Integer depId : fixtureDepIds) {
            jdbcTemplate.update("DELETE FROM system.org_dep WHERE id = ?", depId);
        }
        fixtureDepIds.clear();
        jdbcTemplate.update("DELETE FROM system.org_info WHERE id = ?", id);
    }

    private ResponseEntity<String> exchangeJson(HttpMethod method, String url, String json,
            String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set("Authorization", "Bearer " + token);
        }
        return restTemplate.exchange(url, method, new HttpEntity<>(json, headers), String.class);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }
}
