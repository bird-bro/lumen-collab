package org.birdbro.lumen.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pre-registration schema guard (kernel-pre-registration task 1.2): the kernel test
 * harness must apply BOTH copy-from sources to the test-only {@code core} schema -
 * the canonical V1 registration DDL already rides {@code classpath:db/kernel} (design
 * D2/D9); the V2 pre-registration source adds the {@code exam_order.booking_time}
 * slot anchor and the {@code exam_slot_config} table. The assertion is RED until V2
 * exists (the designed failing-first signal), GREEN once the Flyway ledger carries
 * both versions.
 *
 * @author birdbro
 */
class PreRegistrationSchemaTests extends KernelIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void v2SchemaArtifactsAppliedToTheCoreSchema() {
        Integer bookingTimeColumns = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns"
                + " WHERE table_schema = ? AND table_name = 'exam_order'"
                + " AND column_name = 'booking_time'",
                Integer.class, KernelTestServerBootstrap.CORE_SCHEMA);
        assertEquals(1, bookingTimeColumns,
                "exam_order.booking_time (V2 pre-registration slot anchor) must exist");

        Integer slotConfigTables = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables"
                + " WHERE table_schema = ? AND table_name = 'exam_slot_config'",
                Integer.class, KernelTestServerBootstrap.CORE_SCHEMA);
        assertEquals(1, slotConfigTables,
                "exam_slot_config (V2 per-modality slot config) must exist");

        Integer v2LedgerRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + KernelTestServerBootstrap.CORE_SCHEMA
                + ".flyway_schema_history WHERE version = '2'",
                Integer.class);
        assertEquals(1, v2LedgerRows, "the Flyway ledger must record kernel V2");
    }
}
