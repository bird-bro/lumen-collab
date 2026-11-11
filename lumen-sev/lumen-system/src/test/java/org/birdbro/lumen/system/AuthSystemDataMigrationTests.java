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
 * Schema + seed proof for {@code V3__system_data.sql} (system-data task 1.1) and the
 * V25 audit-log unification (audit-log-unify-api tasks 1.1-1.2): after Flyway applies
 * the chain, the platform-shared system tables must exist in the
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
 *   <li>{@code log_info} is an append-write table with NO audit
 *       block (no create_by/update_by) - columns map the copied LogInfoEntity fields
 *       1:1 camelCase to snake_case; V25 dropped the {@code log_sign} family and
 *       re-typed {@code type} to integer (coordination D1/D2 - one audit table);</li>
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

    /** The platform-shared system tables of design D1, in creation order (V25
     *  dropped log_sign - three remain). */
    private static final List<String> SYSTEM_TABLES = Arrays.asList(
            "dict_catal", "dict_detail", "log_info");

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
        REQUIRED_COLUMNS = Collections.unmodifiableMap(columns);

        Map<String, String> unique = new LinkedHashMap<>();
        unique.put("uk_dict_catal_name", "name");
        unique.put("uk_dict_detail_ca_id_value", "ca_id, value");
        PARTIAL_UNIQUE_INDEXES = Collections.unmodifiableMap(unique);

        Map<String, String> lookup = new LinkedHashMap<>();
        lookup.put("idx_dict_detail_ca_id", "ca_id");
        lookup.put("idx_log_info_date_ym", "date_ym");
        lookup.put("idx_log_info_uid", "uid");
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
        for (String table : Arrays.asList("log_info")) {
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

    // ===================== V25 audit-log unification =====================
    // (audit-log-unify-api tasks 1.1-1.2, coordination D1/D2/D4/D6/D7)

    /**
     * The log_sign family of V13 must be GONE: the partitioned parent (cascading its
     * 2026/2027/def children), the parked conversion residue of any pre-V13 shape, and
     * the id sequence (V13's OWNED BY dance reversed - the sequence dies with the last
     * table that owned it). Coordination D1: one audit table, no data migration.
     */
    @Test
    void v25DropsTheLogSignFamily() {
        // Tables + partitions via information_schema (sequences are NOT listed there).
        for (String relation : Arrays.asList("log_sign", "log_sign_parked",
                "log_sign_2026", "log_sign_2027", "log_sign_def")) {
            Integer found = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class, relation);
            assertEquals(0, found.intValue(),
                    "V25 must drop public." + relation + " (coordination D1: log_sign "
                    + "family leaves with the table; no residue may remain)");
        }
        // The id sequence via pg_class (relkind S) - the V13 OWNED BY dance reversed.
        Integer seqFound = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                + "WHERE n.nspname = 'public' AND c.relname = 'log_sign_id_seq' "
                + "AND c.relkind = 'S'", Integer.class);
        assertEquals(0, seqFound.intValue(),
                "V25 must drop the orphaned public.log_sign_id_seq (coordination D1)");
    }

    /**
     * {@code log_info.type} must be integer after V25 (coordination D2: String at the
     * edge via the {@code @Log} annotation, Integer code at rest - 1~5 operations,
     * 10/11/12 sign events reusing {@code Constants.SUCCESS/FAIL/SIGN_OUT}).
     */
    @Test
    void v25RetypesLogInfoTypeToInteger() {
        String dataType = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND table_name = 'log_info' "
                + "AND column_name = 'type'", String.class);
        assertEquals("integer", dataType,
                "V25 must re-type public.log_info.type to integer (coordination D2: "
                + "Integer code values at rest)");
    }

    /**
     * Menu 12 (the old 日志查询 page) converges to the unified audit page: title
     * 审计日志, re-parented into the 安全 group (16), sort 3 in the retired 17's
     * slot (coordination D6 - one page). Guarded convergence, not an exact shape
     * assert on unrelated columns.
     */
    @Test
    void v25ConvergesMenu12ToTheAuditPage() {
        Map<String, Object> menu = jdbcTemplate.queryForMap(
                "SELECT title, up_id, sort, deleted FROM system.menu_info WHERE id = 12");
        assertEquals("审计日志", menu.get("title"),
                "menu 12 must carry the unified page title 审计日志 (coordination D6)");
        assertEquals(16, ((Number) menu.get("up_id")).intValue(),
                "menu 12 must sit in the 安全 group (16)");
        assertEquals(3, ((Number) menu.get("sort")).intValue(),
                "menu 12 must take sort 3 (the retired menu 17's slot)");
        assertEquals(0, ((Number) menu.get("deleted")).intValue(),
                "the unified audit page itself stays live");
    }

    /**
     * Menu 17 (登录日志, the sign-log page) must retire: soft-deleted (deleted = 1 -
     * the row stays for menu-id history, matching the V22 node-retirement shape) and
     * its {@code role_menu} grants hard-DELETED (V22 precedent - a grant pointing at a
     * dead menu can never be held by a role again).
     */
    @Test
    void v25RetiresMenu17WithItsGrants() {
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM system.menu_info WHERE id = 17", Integer.class);
        assertEquals(1, deleted.intValue(),
                "menu 17 登录日志 must be soft-deleted (deleted = 1) by V25 - the "
                + "sign-log page leaves the IA (coordination D4/D6)");
        Integer liveGrants = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu WHERE menu_id = 17 AND deleted = 0",
                Integer.class);
        assertEquals(0, liveGrants.intValue(),
                "no live role_menu grant may point at the retired menu 17 (V22 "
                + "node-retirement precedent)");
    }

    /**
     * The 「审计日志类型」 dict catalog (coordination D7): catalog id 1030 with its
     * eight deterministic details 1030001-1030008 - operation codes 1/2/3/4/5 plus the
     * sign codes 10/11/12 (verbatim {@code Constants.SUCCESS/FAIL/SIGN_OUT} reuse),
     * sort 1-8, all enabled.
     */
    @Test
    void v25SeedsAuditLogTypeDictCatalog() {
        Map<String, Object> catal = jdbcTemplate.queryForMap(
                "SELECT name, enable, deleted FROM public.dict_catal WHERE id = 1030");
        assertEquals("审计日志类型", catal.get("name"),
                "catalog 1030 anchors BY NAME (V23 discipline - the by-name lookup "
                + "endpoint resolves it)");
        assertEquals(1, ((Number) catal.get("enable")).intValue(),
                "the seeded catalog must be enabled");
        assertEquals(0, ((Number) catal.get("deleted")).intValue(),
                "the seeded catalog must be live");

        Object[][] expectedDetails = {
                {1, "新增"}, {2, "修改"}, {3, "删除"}, {4, "授权"}, {5, "重置密码"},
                {10, "登录成功"}, {11, "登录失败"}, {12, "登出"}};
        for (int i = 0; i < expectedDetails.length; i++) {
            Map<String, Object> detail = jdbcTemplate.queryForMap(
                    "SELECT code, value, sort, enable, deleted FROM public.dict_detail "
                    + "WHERE id = ? AND ca_id = 1030", 1030001 + i);
            assertEquals(((Number) expectedDetails[i][0]).intValue(),
                    ((Number) detail.get("code")).intValue(),
                    "detail 103000" + (i + 1) + " must carry the numeric machine code "
                    + expectedDetails[i][0] + " (V9/V12 model: code stays int)");
            assertEquals(expectedDetails[i][1], detail.get("value"),
                    "detail 103000" + (i + 1) + " must carry the display text");
            assertEquals(i + 1, ((Number) detail.get("sort")).intValue(),
                    "detail 103000" + (i + 1) + " must carry sort " + (i + 1));
            assertEquals(1, ((Number) detail.get("enable")).intValue(),
                    "every seeded detail is a live dropdown option");
            assertEquals(0, ((Number) detail.get("deleted")).intValue(),
                    "every seeded detail must be live");
        }

        Integer liveCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.dict_detail WHERE ca_id = 1030 AND deleted = 0",
                Integer.class);
        assertEquals(8, liveCount.intValue(),
                "catalog 1030 must carry exactly the eight seeded details (no extras "
                + "on the shared test DB)");
    }

    /**
     * V25's guard against sequence regression: the two dict sequences must sit at or
     * past the seeded explicit ids after the {@code setval} resync (V23 shipped WITHOUT
     * its setval tail - V25 must not repeat that gap), so the next application-side
     * dict insert never collides with a seeded id.
     */
    @Test
    void v25ResyncsDictSequencesPastTheSeededIds() {
        Long catalSeq = jdbcTemplate.queryForObject(
                "SELECT last_value FROM public.dict_catal_id_seq", Long.class);
        assertTrue(catalSeq >= 1030,
                "dict_catal_id_seq must sit past the seeded catalog id 1030 after "
                + "V25's setval resync, but was " + catalSeq);
        Long detailSeq = jdbcTemplate.queryForObject(
                "SELECT last_value FROM public.dict_detail_id_seq", Long.class);
        assertTrue(detailSeq >= 1030008,
                "dict_detail_id_seq must sit past the seeded detail id 1030008 after "
                + "V25's setval resync, but was " + detailSeq);
    }

    /**
     * Write into an unpartitioned year lands in the default partition (spec delta
     * § System Log Tables, scenario 4 - the b2-review flagged coverage gap, closed
     * 2026-11-10): a row whose {@code date_ym} year has no child partition (1799
     * predates every yearly child) must land in {@code log_info_def} instead of
     * failing the write. The probe row is inserted AND removed in one JDBC round
     * (no @Async machinery involved, so no polling); a failure leaves nothing behind
     * because the delete runs in a finally.
     */
    @Test
    void writeIntoAnUnpartitionedYearLandsInTheDefaultPartition() {
        String marker = "b4-def-probe-" + System.currentTimeMillis();
        try {
            jdbcTemplate.update(
                    "INSERT INTO public.log_info (date_ym, type, account, action_time) "
                    + "VALUES (179901, 10, ?, now())", marker);
            String landedPartition = jdbcTemplate.queryForObject(
                    "SELECT c.relname FROM pg_class c "
                    + "JOIN public.log_info t ON t.tableoid = c.oid "
                    + "WHERE t.account = ?", String.class, marker);
            assertEquals("log_info_def", landedPartition,
                    "a write into year 1799 (no yearly child) must land in the "
                    + "log_info_def default partition instead of failing");
        } finally {
            jdbcTemplate.update("DELETE FROM public.log_info WHERE account = ?", marker);
        }
        Integer residue = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.log_info WHERE account = ?", Integer.class, marker);
        assertEquals(0, residue.intValue(), "the probe row must be swept");
    }
}
