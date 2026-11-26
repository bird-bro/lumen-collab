package org.birdbro.lumen.us;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import org.birdbro.common.core.R;
import org.birdbro.lumen.feign.system.RoomDetailDto;
import org.birdbro.lumen.feign.system.RoomSummaryDto;
import org.birdbro.lumen.feign.system.SystemRoomApi;
import org.birdbro.lumen.us.config.UsStationProperties;

/**
 * The exam-room create-path proof (us-order-exam-room-sev task 4.1/4.3; spec delta
 * us-station "Registration Surface" scenarios "Create refuses a missing or invalid
 * room" / "Create a booked order" room stamp / "The create result carries the derived
 * state" view pair): the room is REQUIRED at create (检查室不能为空), validated
 * server-side (exists + enabled + the station's department — 检查室不存在或未启用),
 * stamped on BOTH entry branches, and the page view carries {@code roomId}/{@code
 * roomName} (the read-time name join; a dangling id yields a null name). REAL HTTP
 * under the real security chain; the room reads ride the {@code SystemRoomApi} seam.
 *
 * <p>The station binding for the whole us suite is the test-yml anchor (307, set in
 * {@code UsIntegrationTestBase}); tests that mutate the binding restore it.</p>
 *
 * @author birdbro
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsRegistrationRoomTests extends UsApiTestBase {

    /** The room id the fixtures stamp (the base stub answers it as a valid 307 room). */
    private static final int ROOM_ID = 90307;
    private static final String ROOM_NAME = "307 诊室";
    private static final String ORDERS_URL = "/api/v1/us/orders";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SystemRoomApi systemRoomApi;

    @Autowired
    private UsStationProperties stationProperties;

    @BeforeEach
    void restoreBindingAndDefaultRoomStub() {
        // the suite anchor binding (application-test yml equivalent) - the rooms
        // directory tests may have mutated the singleton bean
        ReflectionTestUtils.setField(stationProperties, "stationDeptId", 307L);
        when(systemRoomApi.getInternalRoom(any())).thenAnswer(inv -> R.ofSuccess(
                RoomDetailDto.builder()
                        .id(inv.getArgument(0))
                        .oid(885000).did(307).name(ROOM_NAME).enable(1)
                        .build()));
    }

    @AfterAll
    void restoreBindingAfterClass() {
        ReflectionTestUtils.setField(stationProperties, "stationDeptId", 307L);
    }

    @Test
    void roomlessCreateIsRefusedWithNothingPersisted() throws Exception {
        int stubUid = STUB_UID_BASE + 401;
        stubIdentity(stubUid);
        String body = walkInBody(stamp(), null);

        JsonNode response = parse(exchangeJson(HttpMethod.POST, ORDERS_URL, body,
                fullPermToken()));
        assertEquals("A0400", response.path("code").asText(),
                "a roomless create is refused (检查室不能为空): " + response);
        assertTrue(response.path("msg").asText("").contains("检查室不能为空"),
                "the refusal names the missing room: " + response);
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_order WHERE patient_uid = ?",
                Integer.class, stubUid);
        assertEquals(0, rows.intValue(),
                "a refused create leaves NO order row behind (the validation runs BEFORE "
                + "the port resolve, so not even the patient archive is touched)");
    }

    @Test
    void unknownRoomIsRefused() throws Exception {
        int stubUid = STUB_UID_BASE + 402;
        stubIdentity(stubUid);
        when(systemRoomApi.getInternalRoom(ROOM_ID)).thenReturn(R.ofSuccess(null));

        JsonNode response = parse(exchangeJson(HttpMethod.POST, ORDERS_URL,
                walkInBody(stamp(), ROOM_ID), fullPermToken()));
        assertEquals("A0400", response.path("code").asText(),
                "an unknown room id is refused: " + response);
        assertTrue(response.path("msg").asText("").contains("检查室不存在或未启用"),
                "the refusal names the invalid room: " + response);
    }

    @Test
    void disabledRoomIsRefused() throws Exception {
        int stubUid = STUB_UID_BASE + 403;
        stubIdentity(stubUid);
        when(systemRoomApi.getInternalRoom(ROOM_ID)).thenReturn(R.ofSuccess(
                RoomDetailDto.builder().id(ROOM_ID).oid(885000).did(307)
                        .name(ROOM_NAME).enable(0).build()));

        JsonNode response = parse(exchangeJson(HttpMethod.POST, ORDERS_URL,
                walkInBody(stamp(), ROOM_ID), fullPermToken()));
        assertEquals("A0400", response.path("code").asText(),
                "a disabled room is refused: " + response);
        assertTrue(response.path("msg").asText("").contains("检查室不存在或未启用"),
                "the disabled room rides the same refusal: " + response);
    }

    @Test
    void foreignDepartmentRoomIsRefused() throws Exception {
        int stubUid = STUB_UID_BASE + 404;
        stubIdentity(stubUid);
        when(systemRoomApi.getInternalRoom(ROOM_ID)).thenReturn(R.ofSuccess(
                RoomDetailDto.builder().id(ROOM_ID).oid(885000).did(999)
                        .name("别科诊室").enable(1).build()));

        JsonNode response = parse(exchangeJson(HttpMethod.POST, ORDERS_URL,
                walkInBody(stamp(), ROOM_ID), fullPermToken()));
        assertEquals("A0400", response.path("code").asText(),
                "a room of ANOTHER department is refused (the room scope is the station's "
                + "department, resolved server-side): " + response);
        assertTrue(response.path("msg").asText("").contains("检查室不存在或未启用"),
                "the foreign-department room rides the same refusal: " + response);
    }

    @Test
    void validRoomStampsBothEntryBranches() throws Exception {
        // (a) the walk-in branch
        int walkInUid = STUB_UID_BASE + 405;
        stubIdentity(walkInUid);
        stubNumber();
        JsonNode walkIn = parse(exchangeJson(HttpMethod.POST, ORDERS_URL,
                walkInBody(stamp(), ROOM_ID), fullPermToken()));
        assertEquals("200", walkIn.path("code").asText(),
                "the room-validated walk-in create succeeds: " + walkIn);
        int walkInOrderId = walkIn.path("data").path("orderId").asInt(-1);
        UsRegistrationFixtures.registerOrder(walkInOrderId);
        Integer walkInRoom = jdbcTemplate.queryForObject(
                "SELECT room_id FROM exam_order WHERE id = ?", Integer.class, walkInOrderId);
        assertEquals(ROOM_ID, walkInRoom.intValue(),
                "the walk-in order carries the room anchor");

        // (b) the pre-registration branch (the capacity dance passes, the room stamps)
        String modality = uniqueModality();
        seedSlotConfig(modality, 4);
        int preUid = STUB_UID_BASE + 406;
        stubIdentity(preUid);
        String stubAccession = stubNumber();
        String bookingTime = tomorrowAt(9, 40);
        JsonNode pre = parse(exchangeJson(HttpMethod.POST, ORDERS_URL,
                preregBody(stamp(), ROOM_ID, modality, bookingTime), fullPermToken()));
        assertEquals("200", pre.path("code").asText(),
                "the room-validated pre-registration create succeeds: " + pre);
        int preOrderId = pre.path("data").path("orderId").asInt(-1);
        UsRegistrationFixtures.registerOrder(preOrderId);
        Map<String, Object> preRow = jdbcTemplate.queryForMap(
                "SELECT room_id, registration_type::text AS reg_type "
                + "FROM exam_order WHERE id = ?", preOrderId);
        assertEquals(ROOM_ID, ((Number) preRow.get("room_id")).intValue(),
                "the pre-registered order carries the SAME room anchor (预约时选房)");
        assertEquals("PRE_REGISTERED", preRow.get("reg_type"),
                "the pre-registration branch is untouched beyond the room stamp");
    }

    @Test
    void viewRowsCarryTheRoomPairWithTheReadTimeNameJoin() throws Exception {
        int stubUid = STUB_UID_BASE + 407;
        stubIdentity(stubUid);
        stubNumber();
        JsonNode created = parse(exchangeJson(HttpMethod.POST, ORDERS_URL,
                walkInBody(stamp(), ROOM_ID), fullPermToken()));
        assertEquals("200", created.path("code").asText(), "the create must succeed: "
                + created);
        UsRegistrationFixtures.registerOrder(created.path("data").path("orderId").asInt(-1));

        // the directory answers the room -> the view joins the name at read time
        when(systemRoomApi.listInternal(307)).thenReturn(R.ofSuccess(Collections.singletonList(
                RoomSummaryDto.builder().id(ROOM_ID).name(ROOM_NAME).build())));
        JsonNode page = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "?pageNum=1&pageSize=20", null, fullPermToken()));
        assertEquals("200", page.path("code").asText(), "the page must succeed: " + page);
        JsonNode row = findRowByUid(page, stubUid);
        assertEquals(ROOM_ID, row.path("roomId").asInt(-1),
                "the view row carries roomId");
        assertEquals(ROOM_NAME, row.path("roomName").asText(null),
                "the view row joins roomName at read time");

        // the dangling id (the room left the directory) -> a NULL name, never a guess
        when(systemRoomApi.listInternal(307)).thenReturn(R.ofSuccess(Collections.emptyList()));
        JsonNode pageAfter = parse(exchangeJson(HttpMethod.GET,
                ORDERS_URL + "?pageNum=1&pageSize=20", null, fullPermToken()));
        JsonNode rowAfter = findRowByUid(pageAfter, stubUid);
        assertEquals(ROOM_ID, rowAfter.path("roomId").asInt(-1),
                "the stored anchor survives the registry change");
        assertTrue(rowAfter.path("roomName").isNull() || rowAfter.path("roomName").isMissingNode(),
                "a dangling room id yields a null name (the UI renders '—')");
    }

    // ===================== fixtures =====================

    private void stubIdentity(int uid) {
        when(patientApi.resolveOrRegister(any())).thenReturn(R.ofSuccess(
                org.birdbro.lumen.feign.patient.PatientResolveOrRegisterResult.builder()
                        .result(org.birdbro.lumen.feign.patient.PatientResolveOrRegisterResult.RESULT_MATCHED)
                        .identity(org.birdbro.lumen.feign.patient.PatientIdentityResponse.builder()
                                .patUid(uid)
                                .patId("US3-ROOM")
                                .name("us3房间档案")
                                .gender(2)
                                .state("RESOLVED")
                                .build())
                        .build()));
    }

    private String walkInBody(long ts, Integer roomId) {
        return "{\"idType\":\"01\",\"idNo\":\"US3ROOM-" + ts + "\","
                + "\"cardNo\":\"US3RCARD-" + ts + "\","
                + "\"name\":\"us3房间档案\",\"gender\":2,\"age\":\"30\","
                + "\"patientSource\":\"门诊\","
                + "\"examItems\":[{\"itemId\":1024,\"itemName\":\"腹部超声\"}],"
                + "\"emgFlag\":0"
                + (roomId == null ? "" : ",\"roomId\":" + roomId) + "}";
    }

    private String preregBody(long ts, Integer roomId, String modality, String bookingTime) {
        return "{\"idType\":\"01\",\"idNo\":\"US3RPRE-" + ts + "\","
                + "\"name\":\"us3房间档案\",\"gender\":2,"
                + "\"bookingTime\":\"" + bookingTime + "\","
                + "\"examItems\":[{\"itemId\":1024,\"itemName\":\"预登记项目\","
                + "\"modality\":\"" + modality + "\"}]"
                + (roomId == null ? "" : ",\"roomId\":" + roomId) + "}";
    }

    private JsonNode findRowByUid(JsonNode page, int uid) {
        for (JsonNode record : page.path("data").path("records")) {
            if (record.path("patientUid").asInt(-1) == uid) {
                return record;
            }
        }
        throw new AssertionError("the created order's row must appear in the page: " + page);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getBody());
    }

    private String stubNumber() {
        String accession = STUB_ACCESSION_PREFIX + "ROOM-" + stamp();
        when(numberApi.issue(any())).thenReturn(R.ofSuccess(
                org.birdbro.lumen.feign.number.NumberIssueResponse.builder()
                        .number(accession).build()));
        return accession;
    }

    /** Live-unique UR3- modality token (the slot-config sweep family). */
    private String uniqueModality() {
        return "UR3" + java.util.UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8);
    }

    /** Seeds one live slot config (granularity 30, morning window 08:00-12:00). */
    private void seedSlotConfig(String modality, int capacity) {
        Integer id = jdbcTemplate.queryForObject(
                "INSERT INTO exam_slot_config (modality, granularity_minutes, slot_capacity,"
                + " open_windows) VALUES (?, 30, ?, ?::jsonb) RETURNING id",
                Integer.class, modality, capacity,
                "[{\"start\":\"08:00\",\"end\":\"12:00\"}]");
        UsRegistrationFixtures.registerSlotConfig(id);
    }

    /** A fixed-future wall clock tomorrow morning (inside the seeded window). */
    private static String tomorrowAt(int hour, int minute) {
        return java.time.LocalDate.now().plusDays(1).atTime(hour, minute, 0)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private long stamp() {
        return System.currentTimeMillis();
    }

    private ResponseEntity<String> exchangeJson(HttpMethod method, String url, String body,
            String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set("Authorization", "Bearer " + token);
        }
        return restTemplate.exchange(url, method,
                new HttpEntity<>(body, headers), String.class);
    }
}
