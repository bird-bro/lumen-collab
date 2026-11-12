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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Device registry endpoint proof (org-device-room-api task 2.2; spec deltas api
 * "Device Registry Endpoints" / "Device Create Validation" / "Device Update and Delete"):
 * org-scoped paging with keyword/type/status filters, create with the full surface
 * (access + acquisition blocks), duplicate-code and dead-department A0400 rejections,
 * D9 null-aware full-replace updates, the room-reference delete guard, and the picker
 * list. Timestamped fixtures, hard-deleted afterwards (user ruling 2026-11-07).
 */
class AuthDeviceApiTests extends AuthIntegrationTestBase {

    private static final String LOGIN_URL = "/api/v1/system/login";
    private static final String ORGS_URL = "/api/v1/system/orgs";
    private static final String ORG_DEPS_URL = "/api/v1/system/org-deps";
    private static final String DEVICES_URL = "/api/v1/system/org-devices";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void devicePageIsOrgScopedAndOrderedByIdDescending() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int orgA = createOrg("设备院区_pageA_" + ts);
        int orgB = createOrg("设备院区_pageB_" + ts);
        String code = "ut_dev_page_" + ts;
        int devA = createDevice(code + "_a", "分页设备A", orgA, null);
        int devB = createDevice(code + "_b", "分页设备B", orgB, null);

        ResponseEntity<String> pageA = exchangeJson(HttpMethod.GET,
                DEVICES_URL + "/page?pageNum=1&pageSize=50&orgId=" + orgA + "&keyword=" + code,
                null, token);
        JsonNode bodyA = parse(pageA);
        assertEquals("200", bodyA.path("code").asText(), "the org-scoped page must succeed");
        assertEquals(1, bodyA.path("data").path("total").asLong(-1),
                "org A's page must isolate its own row: " + bodyA);
        assertEquals(devA, bodyA.path("data").path("records").get(0).path("id").asInt(-1),
                "org A's page must return org A's device");

        // Newest-first: a second device in org A must head the list.
        int devA2 = createDevice(code + "_a2", "分页设备A2", orgA, null);
        ResponseEntity<String> pageA2 = exchangeJson(HttpMethod.GET,
                DEVICES_URL + "/page?pageNum=1&pageSize=50&orgId=" + orgA + "&keyword=" + code,
                null, token);
        JsonNode bodyA2 = parse(pageA2);
        assertEquals(2, bodyA2.path("data").path("total").asLong(-1),
                "both org A devices must match: " + bodyA2);
        assertEquals(devA2, bodyA2.path("data").path("records").get(0).path("id").asInt(-1),
                "the page must be ordered by id descending");

        hardDeleteDevice(devA);
        hardDeleteDevice(devA2);
        hardDeleteDevice(devB);
        hardDeleteOrg(orgA);
        hardDeleteOrg(orgB);
    }

    @Test
    void devicePageFiltersByKeywordTypeAndStatus() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int orgId = createOrg("设备院区_filter_" + ts);
        // type "CT" (the dict-1024 VALUE token), status 1 = 在用; type "DR", status 2 = 停用.
        int ctDevice = createDevice("ut_dev_kw_" + ts + "_ct", "CT一号机" + ts, orgId, null);
        int drDevice = createDeviceWithStatus("ut_dev_kw_" + ts + "_dr_x", "DR一号机" + ts, orgId,
                "DR", 2);

        // Keyword matches the NAME (raw CJK in the URL - TestRestTemplate's
        // UriTemplateHandler encodes it; hand-URLEncoder output would be double-encoded).
        ResponseEntity<String> byName = exchangeJson(HttpMethod.GET,
                DEVICES_URL + "/page?pageNum=1&pageSize=50&orgId=" + orgId
                        + "&keyword=CT一号机" + ts,
                null, token);
        JsonNode nameBody = parse(byName);
        assertEquals(1, nameBody.path("data").path("total").asLong(-1),
                "the name keyword must match only the CT device: " + nameBody);

        // Keyword matches the CODE.
        ResponseEntity<String> byCode = exchangeJson(HttpMethod.GET,
                DEVICES_URL + "/page?pageNum=1&pageSize=50&orgId=" + orgId + "&keyword=_dr_",
                null, token);
        JsonNode codeBody = parse(byCode);
        assertEquals(1, codeBody.path("data").path("total").asLong(-1),
                "the code keyword must match only the DR device: " + codeBody);

        // Type filter (the dict-1024 value token).
        ResponseEntity<String> byType = exchangeJson(HttpMethod.GET,
                DEVICES_URL + "/page?pageNum=1&pageSize=50&orgId=" + orgId + "&type=CT",
                null, token);
        JsonNode typeBody = parse(byType);
        assertEquals(1, typeBody.path("data").path("total").asLong(-1),
                "the type filter must isolate the CT device: " + typeBody);
        assertEquals(ctDevice, typeBody.path("data").path("records").get(0).path("id").asInt(-1),
                "the type-filtered row must be the CT device");

        // Status filter (dict 1025: 1 在用 vs 2 停用).
        ResponseEntity<String> byDisabled = exchangeJson(HttpMethod.GET,
                DEVICES_URL + "/page?pageNum=1&pageSize=50&orgId=" + orgId + "&status=2",
                null, token);
        JsonNode disabledBody = parse(byDisabled);
        assertEquals(1, disabledBody.path("data").path("total").asLong(-1),
                "the status filter must isolate the 停用 device: " + disabledBody);
        assertEquals(drDevice,
                disabledBody.path("data").path("records").get(0).path("id").asInt(-1),
                "the status-filtered row must be the DR device");

        hardDeleteDevice(ctDevice);
        hardDeleteDevice(drDevice);
        hardDeleteOrg(orgId);
    }

    @Test
    void createDeviceReturnsIdAndPersistsTheFullSurface() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int orgId = createOrg("设备院区_create_" + ts);
        int depId = createDepartment(orgId, "ut_dev_dep_" + ts, "影像科");

        String body = "{\"code\":\"ut_dev_full_" + ts + "\",\"name\":\"全字段设备" + ts + "\""
                + ",\"type\":\"CT\",\"status\":1,\"tag\":10,\"oid\":" + orgId + ",\"did\":" + depId
                + ",\"brand\":\"联影\",\"model\":\"uCT760\",\"spec\":\"64排\""
                + ",\"serialNum\":\"SN" + ts + "\",\"manufacturer\":\"联影医疗\""
                + ",\"productionDate\":\"2024-01-15\",\"purchaseDate\":\"2024-02-20\""
                + ",\"acceptanceDate\":\"2024-03-01\",\"responsible\":\"张工\""
                + ",\"responsiblePhone\":\"13800000000\",\"startDate\":\"2024-03-10\""
                + ",\"endDate\":\"2034-03-10\",\"remark\":\"验收通过\""
                + ",\"ae\":\"LUMEN_CT\",\"ip\":\"192.168.1.50\",\"port\":\"104\""
                + ",\"boxAdapter\":\"A1\",\"boxIp\":\"192.168.1.51\",\"boxPort\":\"9000\""
                + ",\"boxMac\":\"AA:BB:CC:DD:EE:FF\",\"boxPrint\":\" ward-printer\""
                + ",\"captureW\":2048,\"captureH\":1536,\"prune\":true"
                + ",\"pruneU\":10,\"pruneD\":20,\"pruneL\":30,\"pruneR\":40}";

        ResponseEntity<String> createResponse = exchangeJson(HttpMethod.POST, DEVICES_URL,
                body, token);
        JsonNode createBody = parse(createResponse);
        assertEquals("200", createBody.path("code").asText(),
                "the create must succeed: " + createBody);
        int deviceId = createBody.path("data").asInt(-1);
        assertTrue(deviceId > 0, "the create must return the new device id");

        // The full surface persists (spot-check the blocks the split ruling pins here).
        java.util.Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT brand, ae, ip, port, box_adapter, capture_w, prune, prune_l, did"
                        + " FROM system.org_device WHERE id = ?", deviceId);
        assertNotNull(row, "the created row must be queryable");
        assertEquals("联影", row.get("brand"), "the profile field must persist");
        assertEquals("LUMEN_CT", row.get("ae"), "the access block must persist");
        assertEquals("192.168.1.50", row.get("ip"), "the access IP must persist");
        assertEquals("104", row.get("port"), "the access port must persist");
        assertEquals("A1", row.get("box_adapter"), "the acquisition block must persist");
        assertEquals(2048, ((Number) row.get("capture_w")).intValue(),
                "capture width must persist");
        assertEquals(Boolean.TRUE, row.get("prune"), "the prune flag must persist");
        assertEquals(30, ((Number) row.get("prune_l")).intValue(), "prune L must persist");
        assertEquals(depId, ((Number) row.get("did")).intValue(),
                "the department anchor must persist");

        hardDeleteDevice(deviceId);
        hardDeleteDepartment(depId);
        hardDeleteOrg(orgId);
    }

    @Test
    void createDeviceRejectsForeignOrDeletedDepAndDuplicateCode() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int orgA = createOrg("设备院区_guardA_" + ts);
        int orgB = createOrg("设备院区_guardB_" + ts);
        int depB = createDepartment(orgB, "ut_dev_dep_g" + ts, "B院影像科");
        int depA = createDepartment(orgA, "ut_dev_dep_ga" + ts, "A院影像科");
        String code = "ut_dev_dup_" + ts;

        int original = createDevice(code, "重复码设备", orgA, depA);

        // Duplicate live code within the same org (body carries the required did).
        ResponseEntity<String> duplicate = exchangeJson(HttpMethod.POST, DEVICES_URL,
                buildDeviceBody(code, "重复码设备2", "CT", 1, 10, orgA, depA), token);
        assertEquals("A0400", parse(duplicate).path("code").asText(),
                "a duplicate live code must reject A0400: " + duplicate);

        // A department of ANOTHER org.
        ResponseEntity<String> foreignDep = exchangeJson(HttpMethod.POST, DEVICES_URL,
                buildDeviceBody(code + "_f", "跨院科室设备", "CT", 1, 10, orgA, depB), token);
        assertEquals("A0400", parse(foreignDep).path("code").asText(),
                "a foreign-org did must reject A0400: " + foreignDep);

        // A deleted department of the same org.
        hardDeleteDepartment(depA);
        ResponseEntity<String> deadDep = exchangeJson(HttpMethod.POST, DEVICES_URL,
                buildDeviceBody(code + "_d", "死科室设备", "CT", 1, 10, orgA, depA), token);
        assertEquals("A0400", parse(deadDep).path("code").asText(),
                "a deleted did must reject A0400: " + deadDep);

        // No rejected call may have written a row.
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.org_device WHERE code LIKE ?",
                Integer.class, "ut_dev_dup_" + ts + "%");
        assertEquals(1, rows.intValue(),
                "only the original device row may exist after the rejections");

        hardDeleteDevice(original);
        hardDeleteDepartment(depB);
        hardDeleteOrg(orgA);
        hardDeleteOrg(orgB);
    }

    @Test
    void createDeviceWithoutDepartmentIsRejected() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int orgId = createOrg("设备院区_nodid_" + ts);

        // Missing did - the second-round ruling's required 使用科室 anchor.
        ResponseEntity<String> missing = exchangeJson(HttpMethod.POST, DEVICES_URL,
                buildDeviceBody("ut_dev_nodid_" + ts, "无科室设备", "CT", 1, 10, orgId, null), token);
        JsonNode missingBody = parse(missing);
        assertEquals("A0400", missingBody.path("code").asText(),
                "a device without a department must reject A0400: " + missingBody);
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.org_device WHERE code = ?",
                Integer.class, "ut_dev_nodid_" + ts);
        assertEquals(0, rows.intValue(), "no row may be written for the rejection");

        hardDeleteOrg(orgId);
    }

    @Test
    void devicePageWithoutOrgIdListsEveryCampus() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int orgA = createOrg("设备院区_rootA_" + ts);
        int orgB = createOrg("设备院区_rootB_" + ts);
        int devA = createDevice("ut_dev_ra_" + ts, "根全量设备" + ts, orgA, null);
        int devB = createDevice("ut_dev_rb_" + ts, "根全量设备" + ts, orgB, null);

        // Root selection = ALL campuses: the page omits orgId, the keyword isolates
        // this test's shared name.
        ResponseEntity<String> all = exchangeJson(HttpMethod.GET,
                DEVICES_URL + "/page?pageNum=1&pageSize=50&keyword=根全量设备" + ts,
                null, token);
        JsonNode allBody = parse(all);
        assertEquals(2, allBody.path("data").path("total").asLong(-1),
                "the org-less page must list both campuses' devices: " + allBody);

        // A present orgId still filters to that campus.
        ResponseEntity<String> scoped = exchangeJson(HttpMethod.GET,
                DEVICES_URL + "/page?pageNum=1&pageSize=50&orgId=" + orgA
                        + "&keyword=根全量设备" + ts,
                null, token);
        JsonNode scopedBody = parse(scoped);
        assertEquals(1, scopedBody.path("data").path("total").asLong(-1),
                "the org-scoped page must isolate its campus: " + scopedBody);

        hardDeleteDevice(devA);
        hardDeleteDevice(devB);
        hardDeleteOrg(orgA);
        hardDeleteOrg(orgB);
    }

    @Test
    void devicePickerListNarrowsByDepartment() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int orgId = createOrg("设备院区_pickdep_" + ts);
        int depA = createDepartment(orgId, "ut_dev_dep_pa" + ts, "A科");
        int depB = createDepartment(orgId, "ut_dev_dep_pb" + ts, "B科");
        int devA = createDeviceInDep("ut_dev_pda_" + ts, "A科设备", orgId, depA);
        int devB = createDeviceInDep("ut_dev_pdb_" + ts, "B科设备", orgId, depB);

        ResponseEntity<String> list = exchangeJson(HttpMethod.GET,
                DEVICES_URL + "/list?orgId=" + orgId + "&depId=" + depA, null, token);
        JsonNode listBody = parse(list);
        assertEquals("200", listBody.path("code").asText(),
                "the department-scoped picker list must succeed: " + listBody);
        JsonNode rows = listBody.path("data");
        assertEquals(1, rows.size(), "the depId filter must narrow to dep A's device: "
                + rows);
        assertEquals(devA, rows.get(0).path("id").asInt(-1),
                "the dep-scoped row must be dep A's device");

        hardDeleteDevice(devA);
        hardDeleteDevice(devB);
        hardDeleteDepartment(depA);
        hardDeleteDepartment(depB);
        hardDeleteOrg(orgId);
    }

    @Test
    void updateDeviceFullReplacesAndClearsClearedOptionals() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int orgId = createOrg("设备院区_upd_" + ts);
        int depId = createDepartment(orgId, "ut_dev_dep_u" + ts, "更新科室");
        int deviceId = createDeviceInDep("ut_dev_upd_" + ts, "更新前设备", orgId, depId);
        // Set brand + ae first so the D9 clear path has something to clear.
        exchangeJson(HttpMethod.PUT, DEVICES_URL + "/" + deviceId,
                "{\"code\":\"ut_dev_upd_" + ts + "\",\"name\":\"更新前设备\",\"type\":\"CT\""
                        + ",\"status\":1,\"tag\":10,\"oid\":" + orgId + ",\"did\":" + depId
                        + ",\"brand\":\"旧品牌\",\"ae\":\"OLD_AE\"}", token);

        // Full-replace WITHOUT brand/ae keys - D9: absent optionals CLEAR. The did
        // anchor stays REQUIRED (a device always belongs to a department).
        String slimBody = "{\"code\":\"ut_dev_upd_" + ts + "\",\"name\":\"更新后设备\""
                + ",\"type\":\"MR\",\"status\":2,\"tag\":11,\"oid\":" + orgId
                + ",\"did\":" + depId + "}";
        ResponseEntity<String> update = exchangeJson(HttpMethod.PUT,
                DEVICES_URL + "/" + deviceId, slimBody, token);
        JsonNode updateBody = parse(update);
        assertEquals("200", updateBody.path("code").asText(),
                "the full update must succeed: " + updateBody);
        assertTrue(updateBody.path("data").asBoolean(), "the update must confirm true");

        java.util.Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT name, type, status, tag, brand, ae FROM system.org_device"
                        + " WHERE id = ?", deviceId);
        assertEquals("更新后设备", row.get("name"), "the name must replace");
        assertEquals("MR", row.get("type"), "the type token must replace");
        assertEquals(2, ((Number) row.get("status")).intValue(), "the status must replace");
        assertEquals(11, ((Number) row.get("tag")).intValue(), "the tag must replace");
        assertNull(row.get("brand"), "an absent optional must clear to NULL (D9)");
        assertNull(row.get("ae"), "an absent optional must clear to NULL (D9)");

        // A missing device id rejects A0400.
        ResponseEntity<String> missing = exchangeJson(HttpMethod.PUT,
                DEVICES_URL + "/99999999", slimBody, token);
        assertEquals("A0400", parse(missing).path("code").asText(),
                "a missing device id must reject A0400: " + missing);

        hardDeleteDevice(deviceId);
        hardDeleteDepartment(depId);
        hardDeleteOrg(orgId);
    }

    @Test
    void deleteDeviceIsLogicalAndGuardedByLiveRoomReferences() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int orgId = createOrg("设备院区_del_" + ts);
        int depId = createDepartment(orgId, "ut_dev_dep_r" + ts, "放射科");
        int freeDevice = createDevice("ut_dev_free_" + ts, "空闲设备", orgId, depId);
        int boundDevice = createDevice("ut_dev_bound_" + ts, "绑定设备", orgId, depId);

        // A live room binds the second device (direct fixture - the room API is task 3.2).
        jdbcTemplate.update("INSERT INTO system.org_room (oid, did, name, enable, dev_id)"
                        + " VALUES (?, ?, ?, 1, ?)",
                orgId, depId, "ut_room_guard_" + ts, boundDevice);
        Integer roomId = jdbcTemplate.queryForObject(
                "SELECT id FROM system.org_room WHERE name = ?", Integer.class,
                "ut_room_guard_" + ts);

        // The free device deletes; the bound one rejects with the room count in the msg.
        ResponseEntity<String> freeDelete = exchangeJson(HttpMethod.DELETE,
                DEVICES_URL + "/" + freeDevice, null, token);
        assertEquals("200", parse(freeDelete).path("code").asText(),
                "an unbound device must delete: " + freeDelete);

        ResponseEntity<String> boundDelete = exchangeJson(HttpMethod.DELETE,
                DEVICES_URL + "/" + boundDevice, null, token);
        JsonNode boundBody = parse(boundDelete);
        assertEquals("A0400", boundBody.path("code").asText(),
                "a room-bound device must reject A0400: " + boundBody);
        assertTrue(boundBody.path("msg").asText("").contains("1"),
                "the rejection msg must name the live room count: " + boundBody);

        // Rebind (clear the reference) and retry - the guard must lift.
        jdbcTemplate.update("UPDATE system.org_room SET dev_id = NULL WHERE id = ?", roomId);
        ResponseEntity<String> retry = exchangeJson(HttpMethod.DELETE,
                DEVICES_URL + "/" + boundDevice, null, token);
        assertEquals("200", parse(retry).path("code").asText(),
                "after the room rebinds, the device must delete: " + retry);

        Integer deletedFlag = jdbcTemplate.queryForObject(
                "SELECT deleted FROM system.org_device WHERE id = ?", Integer.class, boundDevice);
        assertEquals(1, deletedFlag.intValue(), "the delete must be logical");

        jdbcTemplate.update("DELETE FROM system.org_room WHERE id = ?", roomId);
        hardDeleteDepartment(depId);
        hardDeleteOrg(orgId);
    }

    @Test
    void devicePickerListFeedsTheRoomForm() throws Exception {
        String token = adminToken();
        long ts = System.currentTimeMillis();
        int orgId = createOrg("设备院区_list_" + ts);
        // Names chosen REVERSE to the creation order - the list must sort by name.
        int zeta = createDevice("ut_dev_lz_" + ts, "Zeta设备", orgId, null);
        int alpha = createDevice("ut_dev_la_" + ts, "Alpha设备", orgId, null);

        ResponseEntity<String> list = exchangeJson(HttpMethod.GET,
                DEVICES_URL + "/list?orgId=" + orgId, null, token);
        JsonNode listBody = parse(list);
        assertEquals("200", listBody.path("code").asText(), "the picker list must succeed");
        JsonNode rows = listBody.path("data");
        assertTrue(rows.isArray() && rows.size() == 2, "the org's live devices must list: " + rows);
        assertEquals("Alpha设备", rows.get(0).path("name").asText(),
                "the picker list must order by name");
        assertNotNull(rows.get(0).path("code").asText(null), "picker rows carry name/code/type");
        assertEquals("CT", rows.get(0).path("type").asText(), "picker rows carry the type token");

        hardDeleteDevice(zeta);
        hardDeleteDevice(alpha);
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

    /** Minimal create body; the full-surface test builds its own JSON inline. */
    private String buildDeviceBody(String code, String name, String type, int status, int tag,
            int oid, Integer did) {
        return "{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"type\":\"" + type + "\""
                + ",\"status\":" + status + ",\"tag\":" + tag + ",\"oid\":" + oid
                + (did == null ? "" : ",\"did\":" + did) + "}";
    }

    private int createDevice(String code, String name, int orgId, Integer did) throws Exception {
        if (did == null) {
            // Second-round ruling: did is REQUIRED - the fixture auto-provisions the
            // 使用科室 (unique per device code; sweep-registered "ut_dev_dep_" prefix).
            String stem = code.replaceAll("[^a-zA-Z0-9]", "");
            String depCode = "ut_dev_dep_"
                    + stem.substring(Math.max(0, stem.length() - 18));
            did = createDepartment(orgId, depCode, "定科设备科室");
        }
        String token = adminToken();
        ResponseEntity<String> response = exchangeJson(HttpMethod.POST, DEVICES_URL,
                buildDeviceBody(code, name, "CT", 1, 10, orgId, did), token);
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(), "device fixture create must succeed: "
                + body);
        return body.path("data").asInt(-1);
    }

    /** Fixture create into an EXPLICIT department (the picker-narrowing test). */
    private int createDeviceInDep(String code, String name, int orgId, int did)
            throws Exception {
        String token = adminToken();
        ResponseEntity<String> response = exchangeJson(HttpMethod.POST, DEVICES_URL,
                buildDeviceBody(code, name, "CT", 1, 10, orgId, did), token);
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(), "device fixture create must succeed: "
                + body);
        return body.path("data").asInt(-1);
    }

    /**
     * Fixture create with explicit dict-1024 type and dict-1025 status codes. The
     * second-round ruling makes device `did` REQUIRED, so the fixture auto-provisions a
     * department (code prefix registered in the sweep; org-scoped sweep also purges it).
     */
    private int createDeviceWithStatus(String code, String name, int orgId, String type, int status)
            throws Exception {
        String token = adminToken();
        String stem = code.replaceAll("[^a-zA-Z0-9]", "");
        int depId = createDepartment(orgId, "ut_dev_dep_"
                + stem.substring(Math.max(0, stem.length() - 18)), "定科设备科室");
        ResponseEntity<String> response = exchangeJson(HttpMethod.POST, DEVICES_URL,
                "{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"type\":\"" + type + "\""
                        + ",\"status\":" + status + ",\"tag\":10,\"oid\":" + orgId
                        + ",\"did\":" + depId + "}",
                token);
        JsonNode body = parse(response);
        assertEquals("200", body.path("code").asText(), "device fixture create must succeed: "
                + body);
        return body.path("data").asInt(-1);
    }

    private void hardDeleteDevice(int id) {
        jdbcTemplate.update("DELETE FROM system.org_device WHERE id = ?", id);
    }

    private void hardDeleteDepartment(int id) {
        jdbcTemplate.update("DELETE FROM system.org_dep WHERE id = ?", id);
    }

    private void hardDeleteOrg(int id) {
        // Departments before their campus (org_dep.org_id physical FK) - the tracked
        // list covers the auto-provisioned did anchors too.
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
