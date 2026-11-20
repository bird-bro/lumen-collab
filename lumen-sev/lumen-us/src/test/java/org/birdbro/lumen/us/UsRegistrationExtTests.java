package org.birdbro.lumen.us;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Extension-table proof for ledger V2 (us-station-registration-sev task 1.3, design D2;
 * specs/us-station "US Registration Extension"): {@code us_registration_ext} carries the
 * US-specific registration fields (pregnancy status, gestational weeks, note) as a 1:1
 * extension of the vendored kernel order - {@code order_id} IS the primary key (the
 * 1:1 anchor), the ruled column types hold, the EntityBase audit block closes the
 * column list in its exact order, and the module keeps the no-physical-FK rule (the
 * order reference stays service-level, kernel DDL shape).
 *
 * <p>The vendored V1 stays verbatim - the US fields live HERE and nowhere in the four
 * kernel tables (asserted from the other side by {@link UsSchemaMigrationTests}).</p>
 *
 * @author birdbro
 */
class UsRegistrationExtTests extends UsIntegrationTestBase {

    /** The audit tail every table carries (EntityBase columns, the ruled order). */
    private static final List<String> AUDIT_TAIL = Arrays.asList(
            "deleted", "create_by", "create_time", "update_by", "update_time");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void extTableCarriesRuledColumnsExactly() {
        List<String> expected = new java.util.ArrayList<>(Arrays.asList(
                "order_id", "pregnancy_status", "gestational_weeks", "note"));
        expected.addAll(AUDIT_TAIL);
        List<String> orderedColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = 'us' AND table_name = 'us_registration_ext' "
                + "ORDER BY ordinal_position", String.class);
        assertEquals(expected, orderedColumns,
                "us_registration_ext must carry EXACTLY the ruled US extension columns "
                + "+ audit tail in order");
    }

    @Test
    void orderIdIsThePrimaryKeyAnchor() {
        Integer pkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint con "
                + "JOIN pg_class rel ON rel.oid = con.conrelid "
                + "JOIN pg_namespace ns ON ns.oid = rel.relnamespace "
                + "WHERE ns.nspname = 'us' AND rel.relname = 'us_registration_ext' "
                + "AND con.contype = 'p'", Integer.class);
        assertEquals(1, pkCount.intValue(),
                "us_registration_ext must carry exactly one primary key (the 1:1 anchor)");

        List<String> pkColumns = jdbcTemplate.queryForList(
                "SELECT a.attname FROM pg_constraint con "
                + "JOIN pg_class rel ON rel.oid = con.conrelid "
                + "JOIN pg_namespace ns ON ns.oid = rel.relnamespace "
                + "JOIN unnest(con.conkey) WITH ORDINALITY k(attnum, ord) ON true "
                + "JOIN pg_attribute a ON a.attrelid = rel.oid AND a.attnum = k.attnum "
                + "WHERE ns.nspname = 'us' AND rel.relname = 'us_registration_ext' "
                + "AND con.contype = 'p' ORDER BY k.ord", String.class);
        assertEquals(java.util.Collections.singletonList("order_id"), pkColumns,
                "order_id itself must be the primary key (1:1 with exam_order)");
    }

    @Test
    void ruledColumnTypesHold() {
        assertEquals("character varying", columnType("pregnancy_status"),
                "pregnancy_status is varchar(16) (design D2)");
        assertEquals("integer", columnType("gestational_weeks"),
                "gestational_weeks is integer (design D2)");
        assertEquals("character varying", columnType("note"),
                "note is varchar(255) (design D2)");
        Integer pregnancyLength = jdbcTemplate.queryForObject(
                "SELECT character_maximum_length FROM information_schema.columns "
                + "WHERE table_schema = 'us' AND table_name = 'us_registration_ext' "
                + "AND column_name = 'pregnancy_status'", Integer.class);
        assertEquals(Integer.valueOf(16), pregnancyLength, "pregnancy_status is varchar(16)");
    }

    @Test
    void extCarriesNoPhysicalForeignKey() {
        Integer fkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint con "
                + "JOIN pg_class rel ON rel.oid = con.conrelid "
                + "JOIN pg_namespace ns ON ns.oid = rel.relnamespace "
                + "WHERE ns.nspname = 'us' AND rel.relname = 'us_registration_ext' "
                + "AND con.contype = 'f'", Integer.class);
        assertEquals(0, fkCount.intValue(),
                "us_registration_ext must carry NO physical foreign key - the order "
                + "reference stays service-level (kernel DDL shape)");
    }

    private String columnType(String column) {
        return jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                + "WHERE table_schema = 'us' AND table_name = 'us_registration_ext' "
                + "AND column_name = ?", String.class, column);
    }
}
