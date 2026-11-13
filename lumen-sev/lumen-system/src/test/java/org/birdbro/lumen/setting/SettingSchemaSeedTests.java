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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exam-setting schema migration proof (exam-setting-api task 1.2; spec deltas
 * exam-setting "Setting Schema Ownership" / "Exam Body Group Columns" / "Exam Body
 * Columns" / "Exam Method Columns" / "Exam Item Columns" / "Exam Item Method Binding" /
 * "Report Template Columns"): the V29 migration creates the six tables in the
 * {@code system} schema (serial Integer PK, EntityBase audit tail, zero physical FKs),
 * the partial unique indexes keep catalog identity unique among LIVE rows, and
 * re-execution is a row-count no-op.
 *
 * <p>Shared long-lived database: every assertion is an existence/shape check, never an
 * exact row count outside the migration's own (empty) seed set.</p>
 *
 * @author birdbro
 */
class SettingSchemaSeedTests extends SettingIntegrationTestBase {

    /** Audit tail every table carries (EntityBase columns, the ruled order). */
    private static final String[] AUDIT_TAIL = {
            "deleted", "create_by", "create_time", "update_by", "update_time"};

    private static final String[] GROUP_COLUMNS = {
            "id", "modality", "name", "sort", "enable"};
    private static final String[] BODY_COLUMNS = {
            "id", "modality", "group_id", "up_id", "name", "code", "sort", "enable"};
    private static final String[] METHOD_COLUMNS = {
            "id", "modality", "name", "code", "sort", "enable"};
    private static final String[] ITEM_COLUMNS = {
            "id", "modality", "exam", "code", "body_id", "price", "rate", "tag",
            "score_rep", "score_aud", "is_common", "sort", "enable", "remark"};
    private static final String[] ITEM_METHOD_COLUMNS = {
            "id", "exam_id", "method_id"};
    private static final String[] TEMP_COLUMNS = {
            "id", "type", "owner_uid", "modality", "flag", "up_id", "name",
            "finding", "impression"};

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void settingTablesExistWithRuledColumns() {
        assertTableColumns("exam_body_group", concat(GROUP_COLUMNS));
        assertTableColumns("exam_body", concat(BODY_COLUMNS));
        assertTableColumns("exam_method", concat(METHOD_COLUMNS));
        assertTableColumns("exam_item", concat(ITEM_COLUMNS));
        assertTableColumns("exam_item_method", concat(ITEM_METHOD_COLUMNS));
        assertTableColumns("rep_temp", concat(TEMP_COLUMNS));

        // NOT NULL anchors the service relies on (spec column rulings).
        assertEquals("NO", columnNullability("exam_body", "group_id"),
                "exam_body.group_id must be NOT NULL - a body always belongs to a group");
        assertEquals("YES", columnNullability("exam_body", "up_id"),
                "exam_body.up_id stays nullable - nesting is optional");
        assertEquals("NO", columnNullability("exam_item", "body_id"),
                "exam_item.body_id must be NOT NULL");
        assertEquals("YES", columnNullability("exam_item", "score_rep"),
                "score_rep is an optional weight - NULL excludes the item from scoring");
        assertEquals("YES", columnNullability("rep_temp", "owner_uid"),
                "rep_temp.owner_uid is NULL for public rows, set for private rows");
    }

    @Test
    void settingTablesStayAtZeroPhysicalForeignKeys() {
        for (String table : new String[] {"exam_body_group", "exam_body", "exam_method",
                "exam_item", "exam_item_method", "rep_temp"}) {
            Integer fk = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_constraint con "
                            + "JOIN pg_class rel ON rel.oid = con.conrelid "
                            + "JOIN pg_namespace ns ON ns.oid = rel.relnamespace "
                            + "WHERE ns.nspname = 'system' AND rel.relname = ? "
                            + "AND con.contype = 'f'",
                    Integer.class, table);
            assertEquals(0, fk.intValue(),
                    table + " must carry zero physical FKs - references are service-enforced");
        }
    }

    @Test
    void liveRowUniquenessIsPartialIndexBacked() {
        // (modality, name) group / (modality, code) item + method / (exam_id, method_id)
        // join - all scoped to LIVE rows (WHERE deleted = 0) so soft-deleted rows free
        // their identity for reuse.
        assertTrue(hasIndex("exam_body_group", "exam_body_group_live_name_uidx"),
                "the body-group live-identity partial unique index must exist");
        assertTrue(hasIndex("exam_item", "exam_item_live_code_uidx"),
                "the item live-code partial unique index must exist");
        assertTrue(hasIndex("exam_method", "exam_method_live_code_uidx"),
                "the method live-code partial unique index must exist");
        assertTrue(hasIndex("exam_item_method", "exam_item_method_uidx"),
                "the item-method join unique index must exist");

        Integer nonPartial = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'system' "
                        + "AND indexname IN ('exam_body_group_live_name_uidx', "
                        + "'exam_item_live_code_uidx', 'exam_method_live_code_uidx') "
                        + "AND position('deleted = 0' in indexdef) = 0",
                Integer.class);
        assertEquals(0, nonPartial.intValue(),
                "the catalog identity indexes must be PARTIAL (WHERE deleted = 0)");
    }

    @Test
    void migrationReExecutionIsARowCountNoOp() throws SQLException {
        // Shape before re-execution.
        List<Map<String, Object>> countsBefore = jdbcTemplate.queryForList(
                "SELECT relname AS tbl, n_live_tup AS live FROM pg_stat_user_tables "
                        + "WHERE schemaname = 'system' AND relname IN ('exam_body_group', "
                        + "'exam_body', 'exam_method', 'exam_item', 'exam_item_method', "
                        + "'rep_temp') ORDER BY relname");

        // Re-run every statement of V1 verbatim (the AuthDeviceRoomSeedTests discipline:
        // re-execution must be a no-op, proven not assumed).
        for (String sql : migrationStatements()) {
            try (Connection connection = openMigrationConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }

        List<Map<String, Object>> countsAfter = jdbcTemplate.queryForList(
                "SELECT relname AS tbl, n_live_tup AS live FROM pg_stat_user_tables "
                        + "WHERE schemaname = 'system' AND relname IN ('exam_body_group', "
                        + "'exam_body', 'exam_method', 'exam_item', 'exam_item_method', "
                        + "'rep_temp') ORDER BY relname");
        assertEquals(countsBefore, countsAfter,
                "re-running V29 must not add, remove, or change any row");
    }

    // ===================== helpers =====================

    private void assertTableColumns(String table, String[] expected) {
        for (String column : expected) {
            Integer found = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = 'system' AND table_name = ? "
                            + "AND column_name = ?",
                    Integer.class, table, column);
            assertEquals(1, found.intValue(),
                    "system." + table + " must carry column " + column);
        }
        Integer extras = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'system' AND table_name = ? "
                        + "AND (column_name = ANY (?) OR column_name = ANY (?))",
                Integer.class, table, expected, AUDIT_TAIL);
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'system' AND table_name = ?",
                Integer.class, table);
        assertEquals(extras.intValue(), total.intValue(),
                "system." + table + " must carry EXACTLY the ruled columns + audit tail");
    }

    private String columnNullability(String table, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = 'system' AND table_name = ? "
                        + "AND column_name = ?",
                String.class, table, column);
    }

    private boolean hasIndex(String table, String indexName) {
        Integer found = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'system' "
                        + "AND tablename = ? AND indexname = ?",
                Integer.class, table, indexName);
        return found != null && found == 1;
    }

    private static String[] concat(String[] business) {
        String[] all = new String[business.length + AUDIT_TAIL.length];
        System.arraycopy(business, 0, all, 0, business.length);
        System.arraycopy(AUDIT_TAIL, 0, all, business.length, AUDIT_TAIL.length);
        return all;
    }

    /** Every V29 statement, split on the semicolons the comments never contain. */
    private static List<String> migrationStatements() {
        try {
            String raw = new String(new ClassPathResource(
                    "/db/migration/V29__exam_setting_tables.sql").getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            return java.util.Arrays.stream(raw.split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("V29__exam_setting_tables.sql must stay on the "
                    + "classpath for the re-execution proof", e);
        }
    }

    /** Plain-JDBC connection (the migration DDL runs outside the pooled datasource). */
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
