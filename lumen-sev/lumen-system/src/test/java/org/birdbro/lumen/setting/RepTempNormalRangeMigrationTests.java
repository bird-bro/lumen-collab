package org.birdbro.lumen.setting;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V47 one-shot content migration proof (rep-temp-normal-range-sev task 1; spec delta
 * exam-setting "Normal-Range v2 One-Shot Migration"): a SQL-inserted v1 row re-runs
 * the V47 statements verbatim and the strict pattern set converts every measure
 * normal to the v2 union - two-sided / single-sided ranges, decimals, the verbatim
 * text degradation for unparseable phrases (byte-for-byte, spaces included), the
 * null pass-through, and the structural edges ([] and null measures sections).
 * Replay after migration is a no-op (the version=1 guard skips v2 rows).
 *
 * <p>Shared long-lived database: the fixture row is timestamped under the
 * sweep-registered 「ut模板」 prefix family and hard-deleted afterwards
 * (user ruling 2026-11-07).</p>
 *
 * @author birdbro
 */
class RepTempNormalRangeMigrationTests extends SettingIntegrationTestBase {

    private static final String MIGRATION = "/db/migration/V47__rep_temp_normal_range_v2.sql";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** The v1 content fixture: 8 measures across 3 sections covering every rule. */
    private static final String V1_CONTENT = "{\"version\":1,\"sections\":[{"
            + "\"name\":\"心脏\",\"finding\":\"节内所见\",\"measures\":["
            + "{\"code\":\"AO\",\"label\":\"主动脉根部内径\",\"unit\":\"mm\",\"normal\":\"21-37\"},"
            + "{\"code\":\"LAD\",\"label\":\"冠状动脉左前降支\",\"unit\":null,\"normal\":\"<5\"},"
            + "{\"code\":\"EF\",\"label\":\"射血分数\",\"unit\":\"%\",\"normal\":\"≤55\"},"
            + "{\"code\":\"LV\",\"label\":\"左室内径\",\"unit\":null,\"normal\":\">3\"},"
            + "{\"code\":\"RV\",\"label\":\"右室内径\",\"unit\":null,\"normal\":\"≥2\"},"
            + "{\"code\":\"MR\",\"label\":\"二尖瓣反流\",\"unit\":null,\"normal\":\" 阴性 \"},"
            + "{\"code\":\"PE\",\"label\":\"心包积液\",\"unit\":null,\"normal\":null},"
            + "{\"code\":\"AO2\",\"label\":\"根部长度\",\"unit\":null,\"normal\":\" 3.5-5.5 \"}]},"
            + "{\"name\":\"心包\",\"finding\":null,\"measures\":[]},"
            + "{\"name\":\"裸节\",\"finding\":null,\"measures\":null}],"
            + "\"impression\":\"超声意见\"}";

    @Test
    void migrationConvertsEveryPatternToTheV2Union() throws Exception {
        long ts = System.currentTimeMillis();
        int temp = insertV1Row("ut模板_v1迁移_" + ts, V1_CONTENT);

        // Re-run the V47 statements verbatim (the SettingSchemaSeedTests replay
        // discipline: conversion is proven, not assumed).
        runMigrationStatements();

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT content->>'version' AS version, content->>'impression' AS impression, "
                        + "content->'sections'->0->>'finding' AS finding "
                        + "FROM system.rep_temp WHERE id = ?", temp);
        assertEquals("2", stored.get("version"), "the migrated row must be version 2");
        assertEquals("超声意见", stored.get("impression"),
                "the global impression must survive the rebuild");
        assertEquals("节内所见", stored.get("finding"),
                "the section finding must survive the rebuild");

        // Per-measure union conversions (the strict pattern set, in order).
        assertRange(temp, 0, 21.0, 37.0, "21-37 must become a two-sided range");
        assertRange(temp, 1, null, 5.0, "<5 must become a max-only range");
        assertRange(temp, 2, null, 55.0, "≤55 must become a max-only range (flattening)");
        assertRange(temp, 3, 3.0, null, ">3 must become a min-only range");
        assertRange(temp, 4, 2.0, null, "≥2 must become a min-only range (flattening)");

        // Unparseable phrase: text variant, ORIGINAL bytes verbatim (spaces included).
        String text = jdbcTemplate.queryForObject(
                "SELECT content->'sections'->0->'measures'->5->'normal'->>'text' "
                        + "FROM system.rep_temp WHERE id = ?", String.class, temp);
        assertEquals(" 阴性 ", text, "the text variant must carry the original bytes verbatim");
        String kind = jdbcTemplate.queryForObject(
                "SELECT content->'sections'->0->'measures'->5->'normal'->>'kind' "
                        + "FROM system.rep_temp WHERE id = ?", String.class, temp);
        assertEquals("text", kind, "the unparseable normal must degrade to kind=text");

        // Null pass-through: the measure normal stays JSON null (no variant object).
        String nullNormal = jdbcTemplate.queryForObject(
                "SELECT content->'sections'->0->'measures'->6->>'normal' "
                        + "FROM system.rep_temp WHERE id = ?", String.class, temp);
        assertNull(nullNormal, "a null normal must stay null (no text wrapper)");

        // Decimal two-sided range trims the surrounding spaces.
        assertRange(temp, 7, 3.5, 5.5, "spaced decimals must parse into a range");

        // Structural edges: [] measures stays an array, null measures stays null.
        String empty = jdbcTemplate.queryForObject(
                "SELECT jsonb_typeof(content->'sections'->1->'measures') "
                        + "FROM system.rep_temp WHERE id = ?", String.class, temp);
        assertEquals("array", empty, "an empty measures array must survive as an array");
        String bare = jdbcTemplate.queryForObject(
                "SELECT content->'sections'->2->>'measures' "
                        + "FROM system.rep_temp WHERE id = ?", String.class, temp);
        assertNull(bare, "a null measures list must survive as null");

        hardDelete(temp);
    }

    @Test
    void migrationReplayIsANoOpOverV2Rows() throws Exception {
        long ts = System.currentTimeMillis();
        int temp = insertV1Row("ut模板_v1迁移重放_" + ts, V1_CONTENT);
        runMigrationStatements();
        String afterFirst = jdbcTemplate.queryForObject(
                "SELECT content::text FROM system.rep_temp WHERE id = ?", String.class, temp);

        // Replay: the version=1 guard must skip the now-v2 row byte-for-byte.
        runMigrationStatements();
        String afterSecond = jdbcTemplate.queryForObject(
                "SELECT content::text FROM system.rep_temp WHERE id = ?", String.class, temp);
        assertEquals(afterFirst, afterSecond, "the replay must not touch a v2 row");

        hardDelete(temp);
    }

    // ===================== helpers =====================

    /** Direct SQL fixture: the v1 shape is rejected by the API after this change. */
    private int insertV1Row(String name, String content) {
        jdbcTemplate.update(
                "INSERT INTO system.rep_temp (type, owner_uid, modality, flag, name, content) "
                        + "VALUES (1, NULL, 'CT', 1, ?, CAST(? AS jsonb))",
                name, content);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM system.rep_temp WHERE name = ?", Integer.class, name);
    }

    private void assertRange(int temp, int measureIndex, Double min, Double max, String message) {
        Map<String, Object> normal = jdbcTemplate.queryForMap(
                "SELECT content->'sections'->0->'measures'->" + measureIndex
                        + "->'normal'->>'kind' AS kind, "
                        + "content->'sections'->0->'measures'->" + measureIndex
                        + "->'normal'->>'min' AS min, "
                        + "content->'sections'->0->'measures'->" + measureIndex
                        + "->'normal'->>'max' AS max "
                        + "FROM system.rep_temp WHERE id = ?", temp);
        assertEquals("range", normal.get("kind"), message);
        assertEquals(min, min == null ? null : Double.valueOf((String) normal.get("min")), message);
        assertEquals(max, max == null ? null : Double.valueOf((String) normal.get("max")), message);
    }

    private void runMigrationStatements() throws SQLException {
        for (String sql : migrationStatements()) {
            try (Connection connection = openMigrationConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }

    private void hardDelete(int id) {
        jdbcTemplate.update("DELETE FROM system.rep_temp WHERE id = ?", id);
    }

    /** Every V47 statement, split on the semicolons the comments never contain. */
    private static List<String> migrationStatements() {
        try {
            String raw = new String(new ClassPathResource(MIGRATION).getInputStream()
                    .readAllBytes(), StandardCharsets.UTF_8);
            return java.util.Arrays.stream(raw.split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("V47 migration must stay on the classpath "
                    + "for the replay proof", e);
        }
    }

    /** Plain-JDBC connection (the migration runs outside the pooled datasource). */
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
