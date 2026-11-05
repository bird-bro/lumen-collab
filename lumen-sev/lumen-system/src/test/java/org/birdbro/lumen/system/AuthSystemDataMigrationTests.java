package org.birdbro.lumen.system;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schema + seed proof for {@code V3__system_data.sql} (system-data task 1.1): after
 * Flyway applies V3, the four platform-shared system tables must exist in the
 * {@code public} schema of the team test server's {@code lumen} database (design D1 -
 * the tables live in public, NOT in the service's system schema; Flyway history stays
 * in system),
 * shaped exactly by their copied entities:
 *
 * <ul>
 *   <li>{@code dict_catal} / {@code dict_detail} carry the EntityBase audit block as
 *       their exact tail (V1 style); V9 slimmed the copied entity shape - the
 *       denormalized {@code ca_code}, the duplicated {@code label} and the pinyin
 *       {@code initial} columns are dropped, and the spare {@code back_1}/{@code back_2}
 *       pair is renamed to {@code tag}/{@code flag};</li>
 *   <li>{@code log_info} / {@code log_sign} are append-write tables with NO audit
 *       block (no create_by/update_by) - columns map the copied LogInfoEntity /
 *       LogSignEntity fields 1:1 camelCase to snake_case;</li>
 *   <li>logical-delete unique values live in PARTIAL unique indexes
 *       ({@code WHERE deleted = 0}) - the detail uniqueness anchors on
 *       {@code (ca_id, value)} after V9;</li>
 *   <li>the {@code common_status} seed catalog with its 启用/停用 details is queryable
 *       (V7 re-based the seed id to 1000; V9 carried the display text into
 *       {@code value}).</li>
 * </ul>
 *
 * <p>Shared long-lived database: every assertion is keyed/CONTAINS-style, never an
 * exact row count.</p>
 *
 * @author birdbro
 */
class AuthSystemDataMigrationTests extends AuthIntegrationTestBase {

    /** The four platform-shared system tables of design D1, in creation order. */
    private static final List<String> SYSTEM_TABLES = Arrays.asList(
            "dict_catal", "dict_detail", "log_info", "log_sign");

    /** The EntityBase audit block the dict tables carry (exact columns, same order). */
    private static final List<String> AUDIT_COLUMNS = Arrays.asList(
            "deleted", "create_by", "create_time", "update_by", "update_time");

    /** Business columns beyond id, per table (the copied entities are authoritative). */
    private static final Map<String, List<String>> REQUIRED_COLUMNS;

    /**
     * Partial-unique index name -> indexed columns. Logical delete must never block
     * re-creating a value; the {@code WHERE deleted = 0} clause is asserted via indexdef.
     */
    private static final Map<String, String> PARTIAL_UNIQUE_INDEXES;

    /** Plain lookup index name -> indexed column (per the binding DDL design). */
    private static final Map<String, String> LOOKUP_INDEXES;

    static {
        Map<String, List<String>> columns = new LinkedHashMap<>();
        columns.put("dict_catal", Arrays.asList(
                "name", "enable", "sort", "remark", "cls"));
        columns.put("dict_detail", Arrays.asList(
                "ca_id", "code", "value", "tag", "flag", "color", "css",
                "sort", "enable", "remark"));
        columns.put("log_info", Arrays.asList(
                "date_ym", "title", "descr", "type", "uid", "account", "action_time",
                "spent", "method", "name", "pack", "url", "session", "mobile", "ip",
                "os", "browser", "engine", "record", "param", "result", "exception",
                "app", "deleted", "create_time"));
        columns.put("log_sign", Arrays.asList(
                "date_ym", "msg", "descr", "type", "uid", "account", "action_time",
                "mobile", "ip", "os", "browser", "engine", "session", "token", "app",
                "deleted", "create_time"));
        REQUIRED_COLUMNS = Collections.unmodifiableMap(columns);

        Map<String, String> unique = new LinkedHashMap<>();
        unique.put("uk_dict_catal_name", "name");
        unique.put("uk_dict_detail_ca_id_value", "ca_id, value");
        PARTIAL_UNIQUE_INDEXES = Collections.unmodifiableMap(unique);

        Map<String, String> lookup = new LinkedHashMap<>();
        lookup.put("idx_dict_detail_ca_id", "ca_id");
        lookup.put("idx_log_info_date_ym", "date_ym");
        lookup.put("idx_log_info_uid", "uid");
        lookup.put("idx_log_sign_date_ym", "date_ym");
        lookup.put("idx_log_sign_uid", "uid");
        LOOKUP_INDEXES = Collections.unmodifiableMap(lookup);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void fourSystemTablesExistInPublicSchema() {
        for (String table : SYSTEM_TABLES) {
            Integer found = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class, table);
            assertEquals(1, found.intValue(),
                    "shared system table public." + table + " must exist (design D1: "
                    + "the public schema, not the system schema)");
        }
    }

    @Test
    void dictTablesCarryEntityColumnsAndAuditBlockTail() {
        for (String table : Arrays.asList("dict_catal", "dict_detail")) {
            List<String> orderedColumns = jdbcTemplate.queryForList(
                    "SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = 'public' AND table_name = ? "
                    + "ORDER BY ordinal_position", String.class, table);

            List<String> expected = new ArrayList<>();
            expected.add("id");
            expected.addAll(REQUIRED_COLUMNS.get(table));
            assertTrue(orderedColumns.containsAll(expected),
                    "public." + table + " must carry " + expected + " but had "
                    + orderedColumns);
            assertEquals("id", orderedColumns.get(0),
                    "public." + table + " must lead with its serial id");

            List<String> tail = orderedColumns.subList(
                    orderedColumns.size() - AUDIT_COLUMNS.size(), orderedColumns.size());
            assertEquals(AUDIT_COLUMNS, tail,
                    "public." + table + " must end with the EntityBase audit block in "
                    + "order (V1 tail style)");
        }
    }

    @Test
    void logTablesCarryEntityColumnsWithoutAuditBlock() {
        for (String table : Arrays.asList("log_info", "log_sign")) {
            List<String> orderedColumns = jdbcTemplate.queryForList(
                    "SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = 'public' AND table_name = ? "
                    + "ORDER BY ordinal_position", String.class, table);

            assertTrue(orderedColumns.containsAll(REQUIRED_COLUMNS.get(table)),
                    "public." + table + " must carry the copied entity columns "
                    + REQUIRED_COLUMNS.get(table) + " but had " + orderedColumns);
            assertEquals("id", orderedColumns.get(0),
                    "public." + table + " must lead with its serial id");
            assertFalse(orderedColumns.contains("create_by"),
                    "public." + table + " is append-write: NO audit block (no create_by)");
            assertFalse(orderedColumns.contains("update_by"),
                    "public." + table + " is append-write: NO audit block (no update_by)");
        }
    }

    @Test
    void logicalDeletePartialUniqueIndexesExist() {
        for (Map.Entry<String, String> index : PARTIAL_UNIQUE_INDEXES.entrySet()) {
            List<String> definitions = jdbcTemplate.queryForList(
                    "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' "
                    + "AND indexname = ?", String.class, index.getKey());
            assertEquals(1, definitions.size(),
                    "partial-unique index " + index.getKey() + " must exist in public");
            String definition = definitions.get(0);
            assertTrue(definition.contains("UNIQUE"),
                    index.getKey() + " must be UNIQUE: " + definition);
            for (String column : index.getValue().split(", ")) {
                assertTrue(definition.contains(column),
                        index.getKey() + " must index " + index.getValue() + ": "
                        + definition);
            }
            assertTrue(definition.contains("deleted = 0"),
                    index.getKey() + " must be partial on deleted = 0 so a logical "
                    + "delete never blocks re-creating the value: " + definition);
        }
    }

    @Test
    void lookupIndexesExist() {
        for (Map.Entry<String, String> index : LOOKUP_INDEXES.entrySet()) {
            List<String> definitions = jdbcTemplate.queryForList(
                    "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' "
                    + "AND indexname = ?", String.class, index.getKey());
            assertEquals(1, definitions.size(),
                    "lookup index " + index.getKey() + " must exist in public");
            assertTrue(definitions.get(0).contains(index.getValue()),
                    index.getKey() + " must index " + index.getValue() + ": "
                    + definitions.get(0));
        }
    }

    @Test
    void commonStatusSeedCatalogQueryable() {
        // V12 dropped dict_catal.code - the seed anchors by its V7-rebased id 1000
        // (the documented head of the platform-dictionary id range).
        Map<String, Object> catal = jdbcTemplate.queryForMap(
                "SELECT name, enable, sort, deleted FROM public.dict_catal "
                + "WHERE id = 1000 AND deleted = 0");
        assertEquals("通用状态", catal.get("name"),
                "the common_status catalog display name");
        assertEquals(1, ((Number) catal.get("enable")).intValue(),
                "the seeded catalog must be enabled (lumen enable convention 1/0)");
    }

    @Test
    void commonStatusSeedDetailsQueryable() {
        // The 启用/停用 pair: after V9 the display text lives in value (label dropped),
        // and V7 re-based the seed catalog id to 1000 (V12 killed the code column, so
        // the join anchors by that id). Keyed assertions (not counts) - the shared
        // database may grow more statuses later.
        Object[][] expectedDetails = {
                {"启用", 1},
                {"停用", 0}};
        for (Object[] expected : expectedDetails) {
            Map<String, Object> detail = jdbcTemplate.queryForMap(
                    "SELECT d.code, d.enable, d.deleted FROM public.dict_detail d "
                    + "JOIN public.dict_catal c ON c.id = d.ca_id "
                    + "WHERE c.id = 1000 AND d.value = ? AND d.deleted = 0",
                    expected[0]);
            assertEquals(((Number) expected[1]).intValue(),
                    ((Number) detail.get("code")).intValue(),
                    "detail '" + expected[0] + "' numeric code (the machine key)");
            assertEquals(1, ((Number) detail.get("enable")).intValue(),
                    "both seeded details are themselves live dropdown options");
        }
    }
}
