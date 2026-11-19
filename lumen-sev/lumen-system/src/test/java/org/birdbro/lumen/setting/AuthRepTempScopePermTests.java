package org.birdbro.lumen.setting;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
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

import org.birdbro.common.core.pojo.user.UserLogin;
import org.birdbro.common.core.pojo.user.UserSign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Report-template scope-perm proof (rep-temp-scope-perms tasks 1.2-1.4; design D2/D5,
 * spec deltas exam-setting "Report Template Scope Enforcement" + "Static pins admit
 * either family" and system-data "Report Template Scope Button Perms"): the D5 test
 * matrix over the scope-keyed families - base-family-only users refuse public
 * writes/reads with the content-free A0301 envelope, public-family-only users pass the
 * relaxed static pins and create owner-less public rows, dual-family users keep every
 * path green, and a non-owner private write still refuses A0400 (ownership asserts
 * BEFORE the family - D2 order; the public family never unlocks private rows). The V42
 * seed assertions (task 1.4 - SettingSchemaSeedTests' V29-table shape does not cover
 * menu F nodes and the file sits in a parallel session's dirty set, so the assertions
 * live here): four F nodes under the 报告模板 C page, role 1 auto-granted, re-execution
 * a live-row no-op (the AuthExamSettingSeedTests discipline). Timestamped fixtures,
 * hard-deleted afterwards (user ruling 2026-11-07).
 */
class AuthRepTempScopePermTests extends SettingIntegrationTestBase {

    private static final String TEMP_URL = "/api/v1/setting/rep-temps";

    /** A second run-scoped identity (the non-owner in the D2-order scenario). */
    private static final Integer OTHER_UID = RUN_UID + 1;

    /** The four V41 button perms (the public family; perms = the contract). */
    private static final String[] PUBLIC_FAMILY = {
            "setting:temp:public:list", "setting:temp:public:create",
            "setting:temp:public:update", "setting:temp:public:delete"};

    /** The ruled F-node sorts under the 报告模板 page (its own buttons sit 1-3). */
    private static final int[] PUBLIC_FAMILY_SORTS = {4, 5, 6, 7};

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ===================== D5 matrix: only the BASE family =====================

    @Test
    void publicWritesWithoutPublicFamilyRefuseContentFree() throws Exception {
        long ts = System.currentTimeMillis();
        String adminToken = dualFamilyToken();
        int folder = createTemp(adminToken, 1, 0, "ut模板目录_守卫_" + ts, null);
        int temp = createTemp(adminToken, 1, 1, "ut模板_守卫_" + ts, folder);

        String baseOnly = baseFamilyToken();

        ResponseEntity<String> deniedCreate = exchangeJson(HttpMethod.POST, TEMP_URL,
                body("{\"type\":1,\"modality\":\"CT\",\"flag\":1,\"name\":\"ut模板_越权建_"
                        + ts + "\",\"finding\":\"表现\",\"impression\":\"意见\"}"), baseOnly);
        assertEquals("A0301", parse(deniedCreate).path("code").asText(),
                "a base-family-only create of a PUBLIC row must refuse A0301: " + deniedCreate);
        Integer leaked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.rep_temp WHERE name = ? AND deleted = 0",
                Integer.class, "ut模板_越权建_" + ts);
        assertEquals(0, leaked.intValue(),
                "the refused create must not insert the row");

        ResponseEntity<String> deniedUpdate = exchangeJson(HttpMethod.PUT,
                TEMP_URL + "/" + temp,
                body("{\"modality\":\"CT\",\"flag\":1,\"upId\":" + folder
                        + ",\"name\":\"ut模板_越权改_" + ts + "\"}"), baseOnly);
        assertEquals("A0301", parse(deniedUpdate).path("code").asText(),
                "a base-family-only update of a PUBLIC row must refuse A0301: " + deniedUpdate);

        ResponseEntity<String> deniedDelete = exchangeJson(HttpMethod.DELETE,
                TEMP_URL + "/" + temp, null, baseOnly);
        assertEquals("A0301", parse(deniedDelete).path("code").asText(),
                "a base-family-only delete of a PUBLIC row must refuse A0301: " + deniedDelete);

        // Content-free: no refusal envelope may echo the row's name.
        assertFalse(parse(deniedUpdate).toString().contains("ut模板_守卫_" + ts),
                "the refusal envelope must stay content-free");

        // The refused delete must not have touched the row.
        Integer alive = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.rep_temp WHERE id = ? AND deleted = 0",
                Integer.class, temp);
        assertEquals(1, alive.intValue(), "the refused delete must leave the row live");

        hardDeleteTemp(temp);
        hardDeleteTemp(folder);
    }

    @Test
    void publicReadWithoutPublicListRefused() throws Exception {
        long ts = System.currentTimeMillis();
        String adminToken = dualFamilyToken();
        int folder = createTemp(adminToken, 1, 0, "ut模板目录_读守卫_" + ts, null);

        String baseListOnly = mintToken("setting:temp:list");

        ResponseEntity<String> deniedTree = exchangeJson(HttpMethod.GET,
                TEMP_URL + "/tree?modality=CT&type=1", null, baseListOnly);
        assertEquals("A0301", parse(deniedTree).path("code").asText(),
                "a base-list-only user must NOT read the PUBLIC tree: " + deniedTree);
        assertFalse(parse(deniedTree).toString().contains("ut模板目录_读守卫_" + ts),
                "the refusal envelope must stay content-free");

        ResponseEntity<String> deniedPage = exchangeJson(HttpMethod.GET,
                TEMP_URL + "/page?modality=CT&type=1&upId=" + folder, null, baseListOnly);
        assertEquals("A0301", parse(deniedPage).path("code").asText(),
                "a base-list-only user must NOT read the PUBLIC page: " + deniedPage);

        // The base family still reads the caller's OWN private scope (family precision).
        ResponseEntity<String> ownTree = exchangeJson(HttpMethod.GET,
                TEMP_URL + "/tree?modality=CT&type=2", null, baseListOnly);
        assertEquals("200", parse(ownTree).path("code").asText(),
                "the base list perm must keep governing the PRIVATE tree: " + ownTree);

        hardDeleteTemp(folder);
    }

    // ===================== D5 matrix: only the PUBLIC family ====================

    @Test
    void publicWriteWithPublicFamilySucceedsAndStaysOwnerless() throws Exception {
        long ts = System.currentTimeMillis();
        String adminToken = dualFamilyToken();
        int folder = createTemp(adminToken, 1, 0, "ut模板目录_公族_" + ts, null);

        // Static-pin proof (delta "Static pins admit either family"): the token holds
        // NO base write perm - the relaxed pin MUST have admitted the public family,
        // and the service allowed the request by its scope.
        String publicCreator = mintToken("setting:temp:list", "setting:temp:public:create");
        ResponseEntity<String> created = exchangeJson(HttpMethod.POST, TEMP_URL,
                body("{\"type\":1,\"modality\":\"CT\",\"flag\":1,\"upId\":" + folder
                        + ",\"name\":\"ut模板_公族建_" + ts + "\","
                        + "\"finding\":\"表现\",\"impression\":\"意见\"}"), publicCreator);
        assertEquals("200", parse(created).path("code").asText(),
                "a public-family-only create must pass the pin AND the service: " + created);
        int temp = parse(created).path("data").asInt(-1);
        assertNull(jdbcTemplate.queryForObject(
                "SELECT owner_uid FROM system.rep_temp WHERE id = ?", Integer.class, temp),
                "a public row must stay owner-less");

        String publicEditor = mintToken("setting:temp:list", "setting:temp:public:update");
        ResponseEntity<String> updated = exchangeJson(HttpMethod.PUT, TEMP_URL + "/" + temp,
                body("{\"modality\":\"CT\",\"flag\":1,\"upId\":" + folder
                        + ",\"name\":\"ut模板_公族改_" + ts + "\","
                        + "\"finding\":\"表现\",\"impression\":\"意见\"}"), publicEditor);
        assertEquals("200", parse(updated).path("code").asText(),
                "a public-family-only update must succeed: " + updated);

        String publicDeleter = mintToken("setting:temp:list", "setting:temp:public:delete");
        ResponseEntity<String> deleted = exchangeJson(HttpMethod.DELETE,
                TEMP_URL + "/" + temp, null, publicDeleter);
        assertEquals("200", parse(deleted).path("code").asText(),
                "a public-family-only delete must succeed: " + deleted);

        hardDeleteTemp(temp);
        hardDeleteTemp(folder);
    }

    // ===================== D5 matrix: DUAL family / ownership order =============

    @Test
    void dualFamilyUserKeepsEveryPathGreen() throws Exception {
        long ts = System.currentTimeMillis();
        String dual = dualFamilyToken();

        ResponseEntity<String> publicTree = exchangeJson(HttpMethod.GET,
                TEMP_URL + "/tree?modality=CT&type=1", null, dual);
        assertEquals("200", parse(publicTree).path("code").asText(),
                "the dual-family user must read the PUBLIC tree: " + publicTree);
        ResponseEntity<String> privateTree = exchangeJson(HttpMethod.GET,
                TEMP_URL + "/tree?modality=CT&type=2", null, dual);
        assertEquals("200", parse(privateTree).path("code").asText(),
                "the dual-family user must read their PRIVATE tree: " + privateTree);

        int publicTemp = createTemp(dual, 1, 1, "ut模板_双族公_" + ts, null);
        int privateTemp = createTemp(dual, 2, 1, "ut模板_双族私_" + ts, null);
        Integer privateOwner = jdbcTemplate.queryForObject(
                "SELECT owner_uid FROM system.rep_temp WHERE id = ?", Integer.class,
                privateTemp);
        assertEquals(RUN_UID, privateOwner,
                "the private create must still bind the session owner");

        ResponseEntity<String> updated = exchangeJson(HttpMethod.PUT,
                TEMP_URL + "/" + publicTemp,
                body("{\"modality\":\"CT\",\"flag\":1,\"name\":\"ut模板_双族公改_"
                        + ts + "\",\"finding\":\"表现\",\"impression\":\"意见\"}"), dual);
        assertEquals("200", parse(updated).path("code").asText(),
                "the dual-family user must update a PUBLIC row: " + updated);

        hardDeleteTemp(publicTemp);
        hardDeleteTemp(privateTemp);
    }

    @Test
    void nonOwnerPrivateWriteStillRefusedA0400BeforeFamily() throws Exception {
        long ts = System.currentTimeMillis();
        String adminToken = dualFamilyToken();
        int folder = createTemp(adminToken, 2, 0, "ut模板目录_归属序_" + ts, null);
        int temp = createTemp(adminToken, 2, 1, "ut模板_归属序_" + ts, folder);

        // The stranger holds the PUBLIC update family but NOT the base update perm -
        // the pin admits, ownership refuses FIRST (D2 order), and the public family
        // never unlocks another user's private row.
        String stranger = mintTokenAs(OTHER_UID, "setting:temp:list",
                "setting:temp:public:update");
        ResponseEntity<String> denied = exchangeJson(HttpMethod.PUT, TEMP_URL + "/" + temp,
                body("{\"modality\":\"CT\",\"flag\":1,\"upId\":" + folder
                        + ",\"name\":\"ut模板_篡改序_" + ts + "\"}"), stranger);
        assertEquals("A0400", parse(denied).path("code").asText(),
                "the non-owner private write must refuse A0400 BEFORE any family assert: "
                        + denied);

        hardDeleteTemp(temp);
        hardDeleteTemp(folder);
    }

    // ===================== V41 seed (task 1.4, spec system-data) ================

    @Test
    void publicFamilyNodesSeededUnderRepTempPageAndAdminAutoGranted() {
        for (int i = 0; i < PUBLIC_FAMILY.length; i++) {
            String perms = PUBLIC_FAMILY[i];
            Map<String, Object> node = jdbcTemplate.queryForMap(
                    "SELECT up_id, sort FROM system.menu_info "
                            + "WHERE type = 'F' AND perms = ? AND deleted = 0", perms);
            Integer underCPage = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system.menu_info WHERE id = ? AND type = 'C' "
                            + "AND perms = 'setting:temp:list' AND deleted = 0",
                    Integer.class, node.get("up_id"));
            assertEquals(1, underCPage.intValue(),
                    "the F node " + perms + " must sit under the 报告模板 page");
            assertEquals(PUBLIC_FAMILY_SORTS[i], ((Number) node.get("sort")).intValue(),
                    "the F node " + perms + " must carry the ruled sort");

            Integer granted = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system.role_menu rm "
                            + "JOIN system.menu_info m ON m.id = rm.menu_id "
                            + "WHERE rm.role_id = 1 AND m.perms = ? AND rm.deleted = 0",
                    Integer.class, perms);
            assertEquals(1, granted.intValue(),
                    "the admin role must be auto-granted " + perms);
        }
    }

    @Test
    void v42ReExecutionIsALiveRowNoOp() throws SQLException {
        String liveNodes = "SELECT COUNT(*) FROM system.menu_info WHERE deleted = 0 "
                + "AND perms LIKE 'setting:temp:public:%'";
        String liveGrants = "SELECT COUNT(*) FROM system.role_menu rm "
                + "JOIN system.menu_info m ON m.id = rm.menu_id "
                + "WHERE rm.role_id = 1 AND rm.deleted = 0 "
                + "AND m.perms LIKE 'setting:temp:public:%'";
        Integer nodesBefore = jdbcTemplate.queryForObject(liveNodes, Integer.class);
        Integer grantsBefore = jdbcTemplate.queryForObject(liveGrants, Integer.class);
        assertTrue(nodesBefore != null && nodesBefore >= 4,
                "the four public-family F nodes must exist before the re-run");
        assertTrue(grantsBefore != null && grantsBefore >= 4,
                "role 1 must hold the four public-family grants before the re-run");

        for (String sql : migrationStatements("/db/migration/V42__rep_temp_public_perms.sql")) {
            try (Connection connection = openMigrationConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }

        assertEquals(nodesBefore, jdbcTemplate.queryForObject(liveNodes, Integer.class),
                "re-running V42 must not add or remove any live menu row");
        assertEquals(grantsBefore, jdbcTemplate.queryForObject(liveGrants, Integer.class),
                "re-running V42 must not add or remove any role 1 grant");
    }

    // ===================== fixture + call helpers =====================

    /** Token carrying ONLY the base family (the pre-change administrator shape). */
    private String baseFamilyToken() {
        return mintToken("setting:temp:list", "setting:temp:create",
                "setting:temp:update", "setting:temp:delete");
    }

    /** Token carrying BOTH families (the dual-family user = today's admin). */
    private String dualFamilyToken() {
        return mintToken("setting:temp:list", "setting:temp:create",
                "setting:temp:update", "setting:temp:delete",
                "setting:temp:public:list", "setting:temp:public:create",
                "setting:temp:public:update", "setting:temp:public:delete");
    }

    private int createTemp(String token, int type, int flag, String name, Integer upId)
            throws Exception {
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
        jdbcTemplate.update("DELETE FROM system.rep_temp WHERE id = ?", id);
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

    // ===================== migration re-run helpers =====================

    /** Every statement of one migration file, split on the semicolons the comments never contain. */
    private static java.util.List<String> migrationStatements(String classPathFile) {
        try {
            String raw = new String(new org.springframework.core.io.ClassPathResource(
                    classPathFile).getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            return java.util.Arrays.stream(raw.split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(classPathFile + " must stay on the "
                    + "classpath for the re-execution proof", e);
        }
    }

    /** Plain-JDBC connection to the team test server's database. */
    private static Connection openMigrationConnection() throws SQLException {
        SettingTestEnv env = SettingTestEnv.load();
        Map<String, String> required = env.require(
                SettingTestEnv.PG_HOST, SettingTestEnv.PG_PORT,
                SettingTestEnv.PG_USER, SettingTestEnv.PG_PASSWORD);
        String database = env.optional(SettingTestEnv.DB, SettingTestEnv.DEFAULT_DB);
        String url = "jdbc:postgresql://" + required.get(SettingTestEnv.PG_HOST) + ":"
                + required.get(SettingTestEnv.PG_PORT) + "/" + database
                + "?currentSchema=system,public";
        return DriverManager.getConnection(url, required.get(SettingTestEnv.PG_USER),
                required.get(SettingTestEnv.PG_PASSWORD));
    }
}
