package org.birdbro.lumen.patient;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schema proof for the patient schema after V1__patient_domain (patient-domain task 1.3):
 * Flyway must reach the team test server's {@code patient} schema, information_schema
 * must hold the five ledger tables with the binding DDL columns and the EntityBase
 * audit block in its exact order, the two partial unique indexes must exist with their
 * exact predicates (identity drop-out on merge; exactly-one-active-rule backstop), the
 * guarded default {@code pat_id} rule seed row must be live, and the module's
 * NO-physical-FK rule must hold (logical references stay service-level).
 *
 * <p>Shared long-lived database: every assertion is a CONTAINS-style existence check,
 * never an exact row count.</p>
 *
 * @author birdbro
 */
class PatientSchemaMigrationTests extends PatientIntegrationTestBase {

    /** The five tables of the binding DDL design, in creation order. */
    private static final List<String> FIVE_TABLES = Arrays.asList(
            "patient_master", "patient_identifier", "patient_number_rule",
            "patient_event_outbox", "patient_merge_log");

    /** The EntityBase audit block every table carries (exact columns, same order). */
    private static final List<String> AUDIT_COLUMNS = Arrays.asList(
            "deleted", "create_by", "create_time", "update_by", "update_time");

    /** Business columns beyond id + the audit block, per table (binding DDL design;
     *  patient_number_rule carries the B1-amendment stamp_value runtime-state column). */
    private static final Map<String, List<String>> REQUIRED_COLUMNS;

    /** Partial-unique index name -> indexdef fragments that MUST all appear (uniqueness,
     *  indexed columns, predicate clauses - verbatim from the binding decisions). */
    private static final Map<String, List<String>> PARTIAL_UNIQUE_INDEXES;

    static {
        Map<String, List<String>> columns = new LinkedHashMap<>();
        columns.put("patient_master", Arrays.asList("pat_id", "id_type", "id_no", "name",
                "pinyin", "gender", "birth_date", "phone", "nation", "address",
                "archive_status", "merged_into", "oid"));
        columns.put("patient_identifier", Arrays.asList("patient_id", "id_type", "id_no"));
        columns.put("patient_number_rule", Arrays.asList("rule_type", "prefix", "suffix",
                "serial_length", "serial_cur", "is_stamp", "stamp", "stamp_value", "active"));
        columns.put("patient_event_outbox", Arrays.asList(
                "event_type", "patient_id", "payload", "status"));
        columns.put("patient_merge_log", Arrays.asList(
                "merged_id", "survivor_id", "reason"));
        REQUIRED_COLUMNS = Collections.unmodifiableMap(columns);

        Map<String, List<String>> indexes = new LinkedHashMap<>();
        indexes.put("uk_patient_master_identity", Arrays.asList(
                "UNIQUE", "id_type, id_no", "id_no IS NOT NULL", "merged_into IS NULL"));
        indexes.put("uk_patient_number_rule_active", Arrays.asList(
                "UNIQUE", "rule_type", "active = 1", "deleted = 0"));
        PARTIAL_UNIQUE_INDEXES = Collections.unmodifiableMap(indexes);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void fiveTablesExist() {
        for (String table : FIVE_TABLES) {
            Integer found = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = 'patient' AND table_name = ?",
                    Integer.class, table);
            assertEquals(1, found.intValue(), "table patient." + table + " must exist");
        }
    }

    @Test
    void everyTableCarriesIdBusinessColumnsAndAuditBlock() {
        for (String table : FIVE_TABLES) {
            // id first, then the business columns, then the audit block - read in
            // physical order so the tail equals the audit block exactly.
            List<String> orderedColumns = jdbcTemplate.queryForList(
                    "SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = 'patient' AND table_name = ? "
                    + "ORDER BY ordinal_position", String.class, table);

            List<String> expected = new ArrayList<>();
            expected.add("id");
            expected.addAll(REQUIRED_COLUMNS.get(table));
            expected.addAll(AUDIT_COLUMNS);
            assertTrue(orderedColumns.containsAll(expected),
                    "patient." + table + " must carry " + expected + " but had "
                    + orderedColumns);

            assertEquals("id", orderedColumns.get(0),
                    "patient." + table + " must lead with its serial id");
            List<String> tail = orderedColumns.subList(
                    orderedColumns.size() - AUDIT_COLUMNS.size(), orderedColumns.size());
            assertEquals(AUDIT_COLUMNS, tail,
                    "patient." + table + " must end with the EntityBase audit block in order");
        }
    }

    @Test
    void partialUniqueIndexesCarryTheirExactPredicates() {
        for (Map.Entry<String, List<String>> index : PARTIAL_UNIQUE_INDEXES.entrySet()) {
            List<String> definitions = jdbcTemplate.queryForList(
                    "SELECT indexdef FROM pg_indexes WHERE schemaname = 'patient' "
                    + "AND indexname = ?", String.class, index.getKey());
            assertEquals(1, definitions.size(),
                    "partial-unique index " + index.getKey() + " must exist on patient schema");
            for (String fragment : index.getValue()) {
                assertTrue(definitions.get(0).contains(fragment),
                        index.getKey() + " indexdef must contain '" + fragment + "': "
                        + definitions.get(0));
            }
        }
    }

    @Test
    void moduleCarriesNoPhysicalForeignKeys() {
        for (String table : FIVE_TABLES) {
            Integer fkCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_constraint con "
                    + "JOIN pg_class rel ON rel.oid = con.conrelid "
                    + "JOIN pg_namespace ns ON ns.oid = rel.relnamespace "
                    + "WHERE ns.nspname = 'patient' AND rel.relname = ? AND con.contype = 'f'",
                    Integer.class, table);
            assertEquals(0, fkCount.intValue(),
                    "patient." + table + " must carry NO physical foreign keys (module rule: "
                    + "logical references stay service-level)");
        }
    }

    @Test
    void defaultPatIdRuleSeedRowIsLive() {
        // CONTAINS-style: at least one live pat_id rule in the ruled seed shape
        // (yyMMdd Java-pattern stamp + 4-digit serial; stamp_value NULL until the
        // first issuance).
        Integer seeds = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient.patient_number_rule "
                + "WHERE rule_type = 'pat_id' AND active = 1 AND deleted = 0 "
                + "AND serial_length = 4 AND stamp = 'yyMMdd' AND stamp_value IS NULL",
                Integer.class);
        assertTrue(seeds != null && seeds >= 1,
                "the guarded default pat_id rule seed (yyMMdd stamp, 4-digit serial) "
                + "must be live, found " + seeds);
    }
}
