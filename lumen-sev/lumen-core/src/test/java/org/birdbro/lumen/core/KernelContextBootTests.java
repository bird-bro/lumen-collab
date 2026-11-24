package org.birdbro.lumen.core;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Context boot + canonical-DDL proof (lumen-core-registration-kernel task 2.1, design
 * D9): the test host boots against the shared test server, Flyway applies the CANONICAL
 * kernel DDL to the kernel-tests-only {@code core} schema, and all five kernel tables
 * exist. This is the schema-level guard for every later batch - if a table renames or
 * the DDL file drifts, this fails first.
 *
 * @author birdbro
 */
class KernelContextBootTests extends KernelIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextBootsAndKernelDdlApplies() {
        List<Map<String, Object>> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = 'core' ORDER BY table_name");
        List<String> names = tables.stream()
                .map(row -> String.valueOf(row.get("table_name")))
                .collect(java.util.stream.Collectors.toList());
        assertTrue(names.contains("exam_order"), "exam_order must exist in core: " + names);
        assertTrue(names.contains("exam_execution"), "exam_execution must exist: " + names);
        assertTrue(names.contains("exam_report"), "exam_report must exist: " + names);
        assertTrue(names.contains("exam_apply_cache"), "exam_apply_cache must exist: " + names);
        // flyway_schema_history shares the core schema (the history-stays-in-service-
        // schema ruling) - the KERNEL tables are exactly FIVE (V2 adds exam_slot_config,
        // kernel-pre-registration design D3/D6; the number-rule table left the kernel
        // for the platform's system schema, 2026-11-18 rewind):
        assertTrue(names.contains("exam_slot_config"), "exam_slot_config must exist: " + names);
        List<String> kernelTables = names.stream()
                .filter(name -> !"flyway_schema_history".equals(name))
                .collect(java.util.stream.Collectors.toList());
        assertEquals(5, kernelTables.size(), "the canonical DDL carries exactly five tables: " + names);
    }
}
