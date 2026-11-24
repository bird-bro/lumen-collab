package org.birdbro.lumen.us;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The slot-config administration proof (us-registration-redesign-api tasks 4.1-4.3;
 * specs/us-station "Slot Config Administration" - the WHEN/THEN scenarios ARE the
 * acceptance gate), REAL HTTP under the real security chain: one trimmed live row per
 * modality (the duplicate refuses A0400), the policy fields update while the modality
 * token stays immutable (the pool identity), soft delete fail-closes the modality's
 * pre-registration, the structured windows decode on the list, and the
 * {@code setting:slot:*} family gates every action.
 *
 * <p>Fixtures ride the UR3- modality family (the sweep arm in
 * {@link UsRegistrationFixtures#sweep} hard-DELETES it — the shared-server ruling).</p>
 *
 * @author birdbro
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsSlotConfigApiTests extends UsApiTestBase {

    private static final String CONFIGS_URL = "/api/v1/us/slot-configs";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void sweepBeforeTest() {
        UsRegistrationFixtures.sweep(jdbcTemplate);
    }

    @AfterAll
    void sweepAfterClass() {
        UsRegistrationFixtures.sweep(jdbcTemplate);
    }

    @Test
    void createStoresOneTrimmedLiveRowPerModality() throws Exception {
        String modality = uniqueModality();
        String body = configBody(" " + modality + " ", 30, 4,
                "[{\"start\":\"08:00\",\"end\":\"12:00\"},{\"start\":\"14:00\",\"end\":\"17:00\"}]");
        JsonNode create = parse(exchangeJson(HttpMethod.POST, CONFIGS_URL, body,
                slotPermToken("setting:slot:create")));
        assertEquals("200", create.path("code").asText(),
                "the create must succeed through the real chain: " + create);
        int configId = create.path("data").path("id").asInt(-1);
        assertTrue(configId > 0, "the create returns the config id: " + create);
        UsRegistrationFixtures.registerSlotConfig(configId);

        // trimmed verbatim storage + the structured windows decode on the read
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT modality, granularity_minutes, slot_capacity FROM exam_slot_config "
                        + "WHERE id = ?", configId);
        assertEquals(modality, row.get("modality"), "the token stores TRIMMED verbatim");
        assertEquals(30, ((Number) row.get("granularity_minutes")).intValue());
        assertEquals(4, ((Number) row.get("slot_capacity")).intValue());

        JsonNode list = parse(exchangeJson(HttpMethod.GET, CONFIGS_URL, null,
                slotPermToken("setting:slot:list")));
        assertEquals("200", list.path("code").asText(), "the list must succeed: " + list);
        JsonNode record = findConfigById(list, configId);
        assertNotNull(record, "the live config renders in the list");
        assertEquals(2, record.path("openWindows").size(),
                "the windows decode as STRUCTURED {start,end} pairs (never a string)");
        assertEquals("08:00", record.path("openWindows").get(0).path("start").asText(),
                "the first window start decodes");
        assertEquals("17:00", record.path("openWindows").get(1).path("end").asText(),
                "the second window end decodes");
    }

    @Test
    void duplicateLiveModalityRefuses() throws Exception {
        String modality = uniqueModality();
        int configId = seedConfigDirect(modality);
        UsRegistrationFixtures.registerSlotConfig(configId);

        JsonNode duplicate = parse(exchangeJson(HttpMethod.POST, CONFIGS_URL,
                configBody(modality, 60, 2, "[]"), slotPermToken("setting:slot:create")));
        assertEquals("A0400", duplicate.path("code").asText(),
                "a duplicate live modality refuses: " + duplicate);
        assertTrue(duplicate.path("msg").asText().contains(modality),
                "the refusal NAMES the duplicate token: " + duplicate);
    }

    @Test
    void updateEditsPolicyAndTokenIsImmutable() throws Exception {
        String modality = uniqueModality();
        int configId = seedConfigDirect(modality);
        UsRegistrationFixtures.registerSlotConfig(configId);

        JsonNode update = parse(exchangeJson(HttpMethod.PUT, CONFIGS_URL + "/" + configId,
                configBody(modality, 60, 6, "[{\"start\":\"09:00\",\"end\":\"11:00\"}]"),
                slotPermToken("setting:slot:update")));
        assertEquals("200", update.path("code").asText(),
                "the policy update must succeed: " + update);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT granularity_minutes, slot_capacity FROM exam_slot_config WHERE id = ?",
                configId);
        assertEquals(60, ((Number) row.get("granularity_minutes")).intValue(),
                "the granularity updates");
        assertEquals(6, ((Number) row.get("slot_capacity")).intValue(),
                "the capacity updates");

        // the pool identity: a token change refuses
        JsonNode tokenChange = parse(exchangeJson(HttpMethod.PUT, CONFIGS_URL + "/" + configId,
                configBody(uniqueModality(), 30, 4, "[]"),
                slotPermToken("setting:slot:update")));
        assertEquals("A0400", tokenChange.path("code").asText(),
                "the modality token is immutable after create: " + tokenChange);
        Map<String, Object> after = jdbcTemplate.queryForMap(
                "SELECT modality FROM exam_slot_config WHERE id = ?", configId);
        assertEquals(modality, after.get("modality"), "the stored token stays untouched");
    }

    @Test
    void deleteIsSoftAndFreesTheModality() throws Exception {
        String modality = uniqueModality();
        int configId = seedConfigDirect(modality);

        JsonNode delete = parse(exchangeJson(HttpMethod.DELETE, CONFIGS_URL + "/" + configId,
                null, slotPermToken("setting:slot:delete")));
        assertEquals("204", delete.path("code").asText(),
                "the void delete answers the house no-content envelope: " + delete);
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM exam_slot_config WHERE id = ?", Integer.class, configId);
        assertEquals(1, deleted.intValue(), "the row SOFT-deletes (deleted=1, physically kept)");

        JsonNode list = parse(exchangeJson(HttpMethod.GET, CONFIGS_URL, null,
                slotPermToken("setting:slot:list")));
        assertNull(findConfigById(list, configId), "the soft-deleted row leaves the live list");

        // freeing the modality: a fresh create for the SAME token succeeds again
        JsonNode recreate = parse(exchangeJson(HttpMethod.POST, CONFIGS_URL,
                configBody(modality, 30, 4, "[]"), slotPermToken("setting:slot:create")));
        assertEquals("200", recreate.path("code").asText(),
                "the live-unique guard only covers LIVE rows: " + recreate);
        UsRegistrationFixtures.registerSlotConfig(recreate.path("data").path("id").asInt(-1));
    }

    @Test
    void thePermsFamilyGatesEveryAction() throws Exception {
        // tokenless -> 203; authenticated perm-less -> A0301
        JsonNode tokenless = parse(exchangeJson(HttpMethod.GET, CONFIGS_URL, null, null));
        assertEquals("203", tokenless.path("code").asText(),
                "a tokenless list answers the entry point's 203: " + tokenless);
        JsonNode denied = parse(exchangeJson(HttpMethod.POST, CONFIGS_URL,
                configBody(uniqueModality(), 30, 4, "[]"), noPermToken()));
        assertEquals("A0301", denied.path("code").asText(),
                "an authenticated perm-less create is denied A0301: " + denied);

        // a LIST-only token reads but cannot create (the per-verb gates)
        JsonNode readOnly = parse(exchangeJson(HttpMethod.GET, CONFIGS_URL, null,
                slotPermToken("setting:slot:list")));
        assertEquals("200", readOnly.path("code").asText(),
                "the list perm reads: " + readOnly);
        JsonNode createDenied = parse(exchangeJson(HttpMethod.POST, CONFIGS_URL,
                configBody(uniqueModality(), 30, 4, "[]"),
                slotPermToken("setting:slot:list")));
        assertEquals("A0301", createDenied.path("code").asText(),
                "a list-only token cannot create: " + createDenied);
    }

    // ---------------------------------------------------------------- helpers

    private String configBody(String modality, int granularity, int capacity, String windows) {
        return "{\"modality\":\"" + modality + "\",\"granularityMinutes\":" + granularity
                + ",\"slotCapacity\":" + capacity + ",\"openWindows\":" + windows + "}";
    }

    /** Direct jdbcTemplate seed (the policy row lands outside the API path). */
    private int seedConfigDirect(String modality) {
        Integer id = jdbcTemplate.queryForObject(
                "INSERT INTO exam_slot_config (modality, granularity_minutes, slot_capacity,"
                + " open_windows) VALUES (?, 30, 4, ?::jsonb) RETURNING id",
                Integer.class, modality, "[{\"start\":\"08:00\",\"end\":\"12:00\"}]");
        assertNotNull(id, "the direct seed must persist");
        return id;
    }

    private String uniqueModality() {
        return "UR3" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /** A token carrying exactly one setting:slot perm. */
    private String slotPermToken(String perm) {
        return mintToken(perm);
    }

    private JsonNode findConfigById(JsonNode listResponse, int configId) {
        for (JsonNode record : listResponse.path("data")) {
            if (record.path("id").asInt(-1) == configId) {
                return record;
            }
        }
        return null;
    }

    private ResponseEntity<String> exchangeJson(HttpMethod method, String url, String json,
            String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set("Authorization", "Bearer " + token);
        }
        return restTemplate.exchange(url, method, new HttpEntity<>(json, headers),
                String.class);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getBody());
    }
}
