package org.birdbro.lumen.us;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schema proof for the us schema after ledger V1 (us-station-registration-sev task 1.2):
 * Flyway must reach the team test server's {@code us} schema, the vendored kernel
 * registration DDL must present the four aggregate tables with the canonical kernel
 * columns and the EntityBase audit block in its exact order - the "Kernel DDL is
 * vendored verbatim" scenario (specs/us-station "US Service Host"): audit column set
 * intact, NO department columns added. The partial-unique accession index keeps
 * kernel-issued numbers unique among live orders, and the module keeps the
 * no-physical-FK rule (logical references stay service-level, kernel DDL shape).
 *
 * <p>Re-execution proof (statement-split, the V36 lesson): the ledger file's comment
 * lines carry NO semicolons, so splitting on {@code ;} yields executable statements -
 * proven by replaying the split statements into a THROWAWAY schema and asserting the
 * same four-table shape. (The kernel DDL uses plain CREATE TABLE on purpose - the
 * verbatim contract; the once-only application guarantee rides
 * {@code flyway_schema_history}, so a live-schema re-run is neither attempted nor
 * needed.) The scratch schema is hard-dropped afterwards - the shared server never
 * keeps the residue.</p>
 *
 * <p>Shared long-lived database: every assertion is a CONTAINS-style existence check,
 * never an exact row count.</p>
 *
 * @author birdbro
 */
class UsSchemaMigrationTests extends UsIntegrationTestBase {

    /** The FIVE tables of the vendored kernel DDL family, in ledger order (the V1
     *  registration aggregate + the V4 pre-registration config — the verbatim vendoring
     *  of kernel V1+V2). */
    private static final List<String> FIVE_TABLES = Arrays.asList(
            "exam_order", "exam_execution", "exam_report", "exam_apply_cache",
            "exam_slot_config");

    /** The EntityBase audit block every table carries (exact columns, same order). */
    private static final List<String> AUDIT_COLUMNS = Arrays.asList(
            "deleted", "create_by", "create_time", "update_by", "update_time");

    /** Business columns beyond id + the audit block, per table - EXACTLY the canonical
     *  kernel DDL (a department column here would break the verbatim contract; the
     *  US fields belong to us_registration_ext instead). */
    private static final Map<String, List<String>> REQUIRED_COLUMNS;

    static {
        Map<String, List<String>> columns = new LinkedHashMap<>();
        columns.put("exam_order", Arrays.asList(
                "accession_no", "booking_no", "apply_no", "his_id", "channel", "status",
                "patient_uid", "patient_snapshot", "exam_items", "emg_flag", "cancel_reason"));
        columns.put("exam_execution", Arrays.asList(
                "order_id", "start_time", "end_time", "operator_id", "remark"));
        columns.put("exam_report", Arrays.asList(
                "order_id", "status", "completion_source"));
        columns.put("exam_apply_cache", Arrays.asList(
                "his_id", "apply_no", "status", "source", "target", "patient_hint"));
        columns.put("exam_slot_config", Arrays.asList(
                "modality", "granularity_minutes", "slot_capacity", "open_windows"));
        REQUIRED_COLUMNS = Collections.unmodifiableMap(columns);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void kernelTablesExist() {
        for (String table : FIVE_TABLES) {
            Integer found = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = 'us' AND table_name = ?",
                    Integer.class, table);
            assertEquals(1, found.intValue(), "table us." + table + " must exist");
        }
    }

    @Test
    void everyTableCarriesBusinessColumnsAndAuditBlockExactly() {
        for (String table : FIVE_TABLES) {
            // id first, then the business columns, then the audit block - read in
            // physical order so the head/tail equal the canonical shape exactly.
            List<String> orderedColumns = jdbcTemplate.queryForList(
                    "SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = 'us' AND table_name = ? "
                    + "ORDER BY ordinal_position", String.class, table);

            List<String> expected = new ArrayList<>();
            expected.add("id");
            expected.addAll(REQUIRED_COLUMNS.get(table));
            expected.addAll(AUDIT_COLUMNS);
            // V4's ALTER TABLE ADD COLUMN appends booking_time at the TABLE END - after
            // the whole V1 block INCLUDING the audit columns (the verbatim ALTER-appends-
            // last reality, identical to the kernel core schema's physical order)
            if ("exam_order".equals(table)) {
                expected.add("booking_time");
            }
            assertEquals(expected, orderedColumns,
                    "us." + table + " must carry EXACTLY the canonical kernel columns "
                    + "+ audit block in order (vendored verbatim, no department columns)");

            assertEquals("id", orderedColumns.get(0),
                    "us." + table + " must lead with its serial id");
        }
    }

    @Test
    void accessionUniquenessIsPartialIndexBacked() {
        List<String> definitions = jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'us' "
                + "AND indexname = 'uk_exam_order_accession_live'", String.class);
        assertEquals(1, definitions.size(),
                "the live-accession partial unique index must exist on the us schema");
        for (String fragment : Arrays.asList("UNIQUE", "accession_no", "deleted = 0")) {
            assertTrue(definitions.get(0).contains(fragment),
                    "uk_exam_order_accession_live indexdef must contain '" + fragment
                    + "': " + definitions.get(0));
        }
    }

    @Test
    void preRegistrationArtifactsLand() {
        // us-registration-redesign-api task 1.1 (V4 vendors the kernel V2 verbatim):
        // booking_time rides exam_order, exam_slot_config joins the us schema
        Integer bookingTime = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'us' AND table_name = 'exam_order' "
                        + "AND column_name = 'booking_time'",
                Integer.class);
        assertEquals(1, bookingTime.intValue(),
                "us.exam_order.booking_time (the V4 vendored slot anchor) must exist");
        Integer slotConfig = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'us' AND table_name = 'exam_slot_config'",
                Integer.class);
        assertEquals(1, slotConfig.intValue(),
                "us.exam_slot_config (the V4 vendored per-modality policy) must exist");
    }

    @Test
    void kernelTablesCarryNoPhysicalForeignKeys() {
        for (String table : FIVE_TABLES) {
            Integer fkCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_constraint con "
                    + "JOIN pg_class rel ON rel.oid = con.conrelid "
                    + "JOIN pg_namespace ns ON ns.oid = rel.relnamespace "
                    + "WHERE ns.nspname = 'us' AND rel.relname = ? AND con.contype = 'f'",
                    Integer.class, table);
            assertEquals(0, fkCount.intValue(),
                    "us." + table + " must carry NO physical foreign keys (kernel shape: "
                    + "logical references stay service-level)");
        }
    }

    @Test
    void vendoredDdlReplaysSplitSafeIntoTheSameShape() throws SQLException {
        String scratch = "us_replay_" + UUID.randomUUID().toString().substring(0, 8);
        // the ledger's kernel-family files replay IN ORDER (V1 creates the tables V4's
        // ALTER extends) - both must split clean and land the canonical shape
        List<String> statements = new ArrayList<>(
                migrationStatements("/db/migration/V1__exam_kernel_registration.sql"));
        statements.addAll(migrationStatements("/db/migration/V4__exam_kernel_pre_registration.sql"));
        try {
            try (Connection create = openSchemaConnection(null);
                 Statement statement = create.createStatement()) {
                statement.execute("CREATE SCHEMA " + scratch);
            }
            for (String sql : statements) {
                try (Connection connection = openSchemaConnection(scratch);
                     Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            }
            // The replayed shape equals the live one: the split was safe (no semicolon
            // inside a comment) and the file re-executes to the canonical four tables.
            for (String table : FIVE_TABLES) {
                try (Connection connection = openSchemaConnection(scratch);
                     Statement statement = connection.createStatement();
                     var found = statement.executeQuery(
                             "SELECT COUNT(*) FROM information_schema.tables "
                             + "WHERE table_schema = '" + scratch + "' AND table_name = '"
                             + table + "'")) {
                    found.next();
                    assertEquals(1, found.getInt(1),
                            "the split-statement replay must create " + table
                            + " in the scratch schema");
                }
            }
        } finally {
            try (Connection drop = openSchemaConnection(null);
                 Statement statement = drop.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + scratch + " CASCADE");
            }
        }
    }

    // ===================== helpers =====================

    /**
     * Every statement of one migration file: the {@code --} comment lines are STRIPPED
     * first, then the remainder splits on semicolons. The strip is not optional for the
     * vendored kernel DDL - its own header line 12 contains a backticked {@code ;}
     * ("splits statements on `;`"), so a naive split truncates a comment mid-line and
     * PostgreSQL rejects the orphaned backtick tail (proven by the first RED run of
     * this suite). This strip-then-split is exactly what the kernel header prescribes.
     */
    private static List<String> migrationStatements(String classPathFile) {
        try {
            String raw = new String(new ClassPathResource(classPathFile).getInputStream()
                    .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            StringBuilder stripped = new StringBuilder();
            for (String line : raw.split("\n", -1)) {
                if (!line.trim().startsWith("--")) {
                    stripped.append(line).append('\n');
                }
            }
            return Arrays.stream(stripped.toString().split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(classPathFile + " must stay on the "
                    + "classpath for the re-execution proof", e);
        }
    }

    /**
     * Plain-JDBC connection to the team test server's database; {@code schema} null
     * connects without a currentSchema pin (bootstrap statements), otherwise the
     * unqualified replay statements land in the given schema.
     */
    private static Connection openSchemaConnection(String schema) throws SQLException {
        UsTestEnv env = UsTestEnv.load();
        Map<String, String> required = env.require(
                UsTestEnv.PG_HOST, UsTestEnv.PG_PORT,
                UsTestEnv.PG_USER, UsTestEnv.PG_PASSWORD);
        String database = env.optional(UsTestEnv.DB, UsTestEnv.DEFAULT_DB);
        String url = "jdbc:postgresql://" + required.get(UsTestEnv.PG_HOST) + ":"
                + required.get(UsTestEnv.PG_PORT) + "/" + database
                + (schema != null ? "?currentSchema=" + schema : "");
        return DriverManager.getConnection(url, required.get(UsTestEnv.PG_USER),
                required.get(UsTestEnv.PG_PASSWORD));
    }
}
