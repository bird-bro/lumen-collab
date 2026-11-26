package org.birdbro.lumen.us;

import java.util.Arrays;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import org.birdbro.common.core.R;
import org.birdbro.lumen.feign.system.RoomSummaryDto;
import org.birdbro.lumen.feign.system.SystemRoomApi;
import org.birdbro.lumen.us.config.UsStationProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The station room-directory proof (us-order-exam-room-sev task 3.1; spec delta
 * us-station "Station Room Directory"): {@code GET /api/v1/us/rooms} serves the
 * station department's enabled rooms through the {@code SystemRoomApi} seam —
 * the knob {@code lumen.us.station-dept-id} scopes the read, an adapter failure
 * degrades to the HONEST empty list, the absent binding answers empty, and the
 * endpoint sits behind {@code us:order:create} (the room pick is a create-time
 * decision). REAL HTTP under the real security chain.
 *
 * @author birdbro
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsStationRoomsApiTests extends UsApiTestBase {

    private static final String ROOMS_URL = "/api/v1/us/rooms";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsStationProperties stationProperties;

    @Autowired
    private SystemRoomApi systemRoomApi;

    @org.junit.jupiter.api.AfterAll
    void restoreBindingAfterClass() {
        // the null-mutation test (absentStationBindingAnswersEmpty) leaves the shared
        // singleton UNBOUND - restore the suite anchor so sibling suites sharing this
        // context never see a null binding (JUnit class order is
        // deterministic-but-unspecified; the review Important fix, 2026-11-26)
        ReflectionTestUtils.setField(stationProperties, "stationDeptId", 307L);
    }

    @Test
    void roomsListServesTheStationDepartmentsRooms() throws Exception {
        ReflectionTestUtils.setField(stationProperties, "stationDeptId", 307L);
        when(systemRoomApi.listInternal(307)).thenReturn(R.ofSuccess(Arrays.asList(
                RoomSummaryDto.builder().id(1).name("1 号诊室").build(),
                RoomSummaryDto.builder().id(2).name("2 号诊室").build())));

        JsonNode body = parse(exchangeJson(HttpMethod.GET, ROOMS_URL, null,
                mintToken("us:order:create")));
        assertEquals("200", body.path("code").asText(),
                "the directory must succeed through the real chain: " + body);
        JsonNode rooms = body.path("data");
        assertEquals(2, rooms.size(), "the adapter rows flow through verbatim: " + body);
        assertEquals(1, rooms.get(0).path("id").asInt(-1), "the slim row carries the id");
        assertEquals("1 号诊室", rooms.get(0).path("name").asText(null),
                "the slim row carries the name");
        assertTrue(rooms.get(0).path("did").isMissingNode(),
                "the directory row stays SLIM ({id, name} only): " + rooms.get(0));
    }

    @Test
    void adapterDegradedBandServesEmptyThroughTheRealChain() throws Exception {
        ReflectionTestUtils.setField(stationProperties, "stationDeptId", 307L);
        // the degradation happens INSIDE the Sentinel fallback (the split semantics are
        // SystemRoomApiFallbackContractTest's) - at the endpoint the degraded band is
        // the fallback's ANSWER (empty list) flowing through the real HTTP chain: the
        // desk gets a usable 200 + empty response, never a 5xx, never fabricated rows
        when(systemRoomApi.listInternal(307))
                .thenReturn(R.ofSuccess(java.util.Collections.emptyList()));

        JsonNode body = parse(exchangeJson(HttpMethod.GET, ROOMS_URL, null,
                mintToken("us:order:create")));
        assertEquals("200", body.path("code").asText(),
                "the degraded directory still answers 200 (the desk blocks on the empty "
                + "Select + the required rule, not on a failed request): " + body);
        assertEquals(0, body.path("data").size(),
                "the failure band is the empty list, never fabricated rows");
    }

    @Test
    void absentStationBindingAnswersEmpty() throws Exception {
        ReflectionTestUtils.setField(stationProperties, "stationDeptId", null);

        JsonNode body = parse(exchangeJson(HttpMethod.GET, ROOMS_URL, null,
                mintToken("us:order:create")));
        assertEquals("200", body.path("code").asText(),
                "the absent binding answers 200 empty (fail closed, never a "
                + "whole-registry dump): " + body);
        assertEquals(0, body.path("data").size(),
                "no department binding = no rooms");
    }

    @Test
    void roomsListRequiresTheCreatePerm() throws Exception {
        ResponseEntity<String> response = exchangeJson(HttpMethod.GET, ROOMS_URL, null,
                mintToken("us:order:list"));
        String code = parse(response).path("code").asText("");
        assertTrue(!"200".equals(code),
                "the room pick is a create-time decision - us:order:list alone must be "
                + "refused (the chain answers 203/A0301, never 200): " + response);
    }

    // ===================== helpers =====================

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getBody());
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
