package org.birdbro.lumen.setting;

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

import org.birdbro.common.core.pojo.user.UserLogin;
import org.birdbro.common.core.pojo.user.UserSign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Report-template endpoint proof (exam-setting-api task 5.1; spec deltas exam-setting
 * "Report Template Endpoints" / "Report Template Scope Enforcement" / "Report Template
 * Columns"): the modality+scope tree, the paged template list under one parent with a
 * name filter, server-side owner binding on private creates, owner-only writes, and
 * the non-owner leak-free refusal. Timestamped fixtures, hard-deleted afterwards
 * (user ruling 2026-11-07).
 */
class AuthRepTempApiTests extends SettingIntegrationTestBase {

    private static final String TEMP_URL = "/api/v1/setting/rep-temps";

    /** A second run-scoped identity (the non-owner in the enforcement scenarios). */
    private static final Integer OTHER_UID = RUN_UID + 1;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void treeReturnsScopesIndependentlyAndPageFiltersUnderOneParent() throws Exception {
        long ts = System.currentTimeMillis();
        String ownerToken = fullPermToken();
        int publicFolder = createTemp(ownerToken, 1, null, 0, "ut模板目录_公共_" + ts, null);
        int publicTemp = createTemp(ownerToken, 1, null, 1, "ut模板_公共模板_" + ts,
                publicFolder);
        int privateFolder = createTemp(ownerToken, 2, null, 0, "ut模板目录_私有_" + ts, null);
        int privateTemp = createTemp(ownerToken, 2, null, 1, "ut模板_私有模板_" + ts,
                privateFolder);

        // Trees are scope-independent: type=1 never carries the private rows.
        JsonNode publicTree = parse(exchangeJson(HttpMethod.GET,
                TEMP_URL + "/tree?modality=CT&type=1", null, ownerToken));
        assertEquals("200", publicTree.path("code").asText(), "the public tree must succeed");
        assertTrue(publicTree.path("data").toString().contains("ut模板目录_公共_" + ts),
                "the public tree must carry the public folder");
        assertTrue(!publicTree.path("data").toString().contains("ut模板_私有模板_" + ts),
                "the public tree must NOT carry private rows");

        JsonNode privateTree = parse(exchangeJson(HttpMethod.GET,
                TEMP_URL + "/tree?modality=CT&type=2", null, ownerToken));
        assertTrue(privateTree.path("data").toString().contains("ut模板_私有模板_" + ts),
                "the owner's private tree must carry their private template");

        // The page lists flag=1 rows under one parent, name-filtered.
        JsonNode page = parse(exchangeJson(HttpMethod.GET,
                TEMP_URL + "/page?modality=CT&type=1&upId=" + publicFolder
                        + "&name=公共模板", null, ownerToken));
        assertEquals(1, page.path("data").path("total").asLong(-1),
                "the folder-scoped page must isolate the template: " + page);
        assertEquals("ut模板_公共模板_" + ts,
                page.path("data").path("records").get(0).path("name").asText(),
                "the page hit must be the public template");

        hardDeleteTemp(publicTemp);
        hardDeleteTemp(publicFolder);
        hardDeleteTemp(privateTemp);
        hardDeleteTemp(privateFolder);
    }

    @Test
    void privateCreateBindsOwnerServerSideAndNonOwnerWritesRefuse() throws Exception {
        long ts = System.currentTimeMillis();
        String ownerToken = fullPermToken();
        int folder = createTemp(ownerToken, 2, null, 0, "ut模板目录_归属_" + ts, null);

        // A private create CLAIMING someone else's uid must be overridden server-side.
        ResponseEntity<String> created = exchangeJson(HttpMethod.POST, TEMP_URL,
                body("{\"type\":2,\"ownerUid\":" + OTHER_UID + ",\"modality\":\"CT\","
                        + "\"flag\":1,\"upId\":" + folder + ",\"name\":\"ut模板_归属模板_"
                        + ts + "\",\"finding\":\"表现内容\",\"impression\":\"意见内容\"}"),
                ownerToken);
        assertEquals("200", parse(created).path("code").asText(),
                "the private create must succeed: " + created);
        int temp = parse(created).path("data").asInt(-1);
        Integer storedOwner = jdbcTemplate.queryForObject(
                "SELECT owner_uid FROM setting.rep_temp WHERE id = ?", Integer.class, temp);
        assertEquals(RUN_UID, storedOwner,
                "the owner binding must come from the SESSION, never the request body");

        // The non-owner token must be refused WITHOUT leaking the row's content.
        String otherToken = mintTokenAs(OTHER_UID, "setting:item:list", "setting:temp:list",
                "setting:temp:create", "setting:temp:update", "setting:temp:delete");
        ResponseEntity<String> leakProbe = exchangeJson(HttpMethod.GET,
                TEMP_URL + "/tree?modality=CT&type=2", null, otherToken);
        assertTrue(!parse(leakProbe).path("data").toString().contains("ut模板_归属模板_" + ts),
                "another user's private rows must never render in their tree");

        ResponseEntity<String> deniedUpdate = exchangeJson(HttpMethod.PUT,
                TEMP_URL + "/" + temp,
                body("{\"type\":2,\"modality\":\"CT\",\"flag\":1,\"upId\":" + folder
                        + ",\"name\":\"ut模板_篡改_" + ts + "\"}"), otherToken);
        assertEquals("A0400", parse(deniedUpdate).path("code").asText(),
                "a non-owner private update must refuse A0400: " + deniedUpdate);

        ResponseEntity<String> deniedDelete = exchangeJson(HttpMethod.DELETE,
                TEMP_URL + "/" + temp, null, otherToken);
        assertEquals("A0400", parse(deniedDelete).path("code").asText(),
                "a non-owner private delete must refuse A0400: " + deniedDelete);

        // The owner still writes fine.
        ResponseEntity<String> ownerUpdate = exchangeJson(HttpMethod.PUT,
                TEMP_URL + "/" + temp,
                body("{\"type\":2,\"modality\":\"CT\",\"flag\":1,\"upId\":" + folder
                        + ",\"name\":\"ut模板_归属模板改_" + ts + "\","
                        + "\"finding\":\"表现\",\"impression\":\"意见\"}"), ownerToken);
        assertEquals("200", parse(ownerUpdate).path("code").asText(),
                "the owner's update must succeed: " + ownerUpdate);

        hardDeleteTemp(temp);
        hardDeleteTemp(folder);
    }

    @Test
    void unknownModalityIsRejectedAndDeletesStayLogical() throws Exception {
        long ts = System.currentTimeMillis();
        String ownerToken = fullPermToken();
        ResponseEntity<String> unknown = exchangeJson(HttpMethod.POST, TEMP_URL,
                body("{\"type\":1,\"modality\":\"XX\",\"flag\":1,\"name\":\"ut模板_野模态_"
                        + ts + "\"}"), ownerToken);
        assertEquals("A0400", parse(unknown).path("code").asText(),
                "a modality absent from dict 1027 must reject A0400: " + unknown);

        int temp = createTemp(ownerToken, 1, null, 1, "ut模板_软删_" + ts, null);
        exchangeJson(HttpMethod.DELETE, TEMP_URL + "/" + temp, null, ownerToken);
        Integer deletedFlag = jdbcTemplate.queryForObject(
                "SELECT deleted FROM setting.rep_temp WHERE id = ?", Integer.class, temp);
        assertEquals(Integer.valueOf(1), deletedFlag,
                "the delete must be LOGICAL (deleted=1), never physical");
        hardDeleteTemp(temp);
    }

    // ===================== fixture + call helpers =====================

    private int createTemp(String token, int type, Integer ownerUidIgnored, int flag,
            String name, Integer upId) throws Exception {
        String payload = "{\"type\":" + type + ",\"modality\":\"CT\",\"flag\":" + flag
                + (upId != null ? ",\"upId\":" + upId : "")
                + ",\"name\":\"" + name + "\""
                + (flag == 1 ? ",\"finding\":\"表现\",\"impression\":\"意见\"" : "")
                + "}";
        ResponseEntity<String> created = exchangeJson(HttpMethod.POST, TEMP_URL,
                body(payload), token);
        JsonNode createdBody = parse(created);
        assertEquals("200", createdBody.path("code").asText(),
                "the fixture template must create cleanly: " + created);
        return createdBody.path("data").asInt(-1);
    }

    /** Mints a token for a DIFFERENT run-scoped identity (the non-owner). */
    private String mintTokenAs(Integer uid, String... permissions) {
        java.util.Set<String> perms = new java.util.LinkedHashSet<>();
        for (String permission : permissions) {
            perms.add(permission);
        }
        UserSign sign = new UserSign();
        sign.setUid(uid);
        sign.setAccount("it-setting_" + uid);
        sign.setName("it-setting non-owner " + uid);
        sign.setEnable(1);
        UserLogin login = new UserLogin(uid, sign, perms);
        return tokenService.createToken(login);
    }

    private void hardDeleteTemp(int id) {
        jdbcTemplate.update("DELETE FROM setting.rep_temp WHERE id = ?", id);
    }

    private HttpEntity<String> body(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(json, headers);
    }

    private ResponseEntity<String> exchangeJson(HttpMethod method, String url,
            HttpEntity<String> entity, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (entity != null && entity.getHeaders() != null) {
            headers.addAll(entity.getHeaders());
        }
        headers.set(AUTH_HEADER, "Bearer " + token);
        return restTemplate.exchange(url, method,
                new HttpEntity<>(entity != null ? entity.getBody() : null, headers),
                String.class);
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        assertNotNull(response.getBody(), "every call must carry an envelope body");
        return objectMapper.readTree(response.getBody());
    }
}
