package org.birdbro.lumen.auth;

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
 * Schema proof for the seven-table auth schema (auth-rbac-foundation task 2.1): after
 * Flyway applies {@code V1__auth_schema.sql} to the team test server's {@code auth}
 * schema, {@code information_schema} must hold the seven tables with the binding DDL
 * columns, the EntityBase audit block in its exact order, the logical-delete
 * partial-unique indexes, and the foreign keys exactly where the binding design puts
 * them (org_info deliberately carries NO self-FK - root rows use {@code up_id = 0}).
 *
 * <p>Shared long-lived database: every assertion is a CONTAINS-style existence check,
 * never an exact row count.</p>
 *
 * @author birdbro
 */
class AuthSchemaMigrationTests extends AuthIntegrationTestBase {

    /** The seven tables of the binding DDL design, in creation order. */
    private static final List<String> SEVEN_TABLES = Arrays.asList(
            "org_info", "org_dep", "user_info", "role_info", "user_role",
            "menu_info", "role_menu");

    /** The EntityBase audit block every table carries (exact columns, same order). */
    private static final List<String> AUDIT_COLUMNS = Arrays.asList(
            "deleted", "create_by", "create_time", "update_by", "update_time");

    /** Business columns beyond id + the audit block, per table (binding DDL design). */
    private static final Map<String, List<String>> REQUIRED_COLUMNS;

    /** Partial-unique index name -> indexed columns (logical delete must not block
     *  re-creation; the WHERE deleted = 0 clause is asserted via indexdef). */
    private static final Map<String, String> PARTIAL_UNIQUE_INDEXES;

    /** Foreign keys per table (0 = deliberately none, e.g. org_info's no-self-FK rule). */
    private static final Map<String, Integer> EXPECTED_FK_COUNTS;

    static {
        Map<String, List<String>> columns = new LinkedHashMap<>();
        columns.put("org_info", Arrays.asList("up_id", "level", "type", "name"));
        columns.put("org_dep", Arrays.asList("org_id", "code", "name"));
        columns.put("user_info", Arrays.asList("username", "password",
                "must_change_password", "enable", "name"));
        columns.put("role_info", Arrays.asList("name", "type", "remark"));
        columns.put("user_role", Arrays.asList("user_id", "role_id"));
        columns.put("menu_info", Arrays.asList("up_id", "type", "title", "perms",
                "path", "component", "icon", "sort", "visible"));
        columns.put("role_menu", Arrays.asList("role_id", "menu_id"));
        REQUIRED_COLUMNS = Collections.unmodifiableMap(columns);

        Map<String, String> indexes = new LinkedHashMap<>();
        indexes.put("uk_org_dep_code", "code");
        indexes.put("uk_user_info_username", "username");
        indexes.put("uk_role_info_name", "name");
        indexes.put("uk_user_role_user_role", "user_id, role_id");
        indexes.put("uk_role_menu_role_menu", "role_id, menu_id");
        PARTIAL_UNIQUE_INDEXES = Collections.unmodifiableMap(indexes);

        Map<String, Integer> foreignKeys = new LinkedHashMap<>();
        foreignKeys.put("org_info", 0);
        foreignKeys.put("org_dep", 1);
        foreignKeys.put("user_info", 0);
        foreignKeys.put("role_info", 0);
        foreignKeys.put("user_role", 2);
        foreignKeys.put("menu_info", 0);
        foreignKeys.put("role_menu", 2);
        EXPECTED_FK_COUNTS = Collections.unmodifiableMap(foreignKeys);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void sevenTablesExist() {
        for (String table : SEVEN_TABLES) {
            Integer found = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = 'auth' AND table_name = ?",
                    Integer.class, table);
            assertEquals(1, found.intValue(), "table auth." + table + " must exist");
        }
    }

    @Test
    void everyTableCarriesIdBusinessColumnsAndAuditBlock() {
        for (String table : SEVEN_TABLES) {
            // id first, then the business columns, then the audit block - read in
            // physical order so the tail equals the audit block exactly.
            List<String> orderedColumns = jdbcTemplate.queryForList(
                    "SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = 'auth' AND table_name = ? "
                    + "ORDER BY ordinal_position", String.class, table);

            List<String> expected = new ArrayList<>();
            expected.add("id");
            expected.addAll(REQUIRED_COLUMNS.get(table));
            expected.addAll(AUDIT_COLUMNS);
            assertTrue(orderedColumns.containsAll(expected),
                    "auth." + table + " must carry " + expected + " but had "
                    + orderedColumns);

            assertEquals("id", orderedColumns.get(0),
                    "auth." + table + " must lead with its serial id");
            List<String> tail = orderedColumns.subList(
                    orderedColumns.size() - AUDIT_COLUMNS.size(), orderedColumns.size());
            assertEquals(AUDIT_COLUMNS, tail,
                    "auth." + table + " must end with the EntityBase audit block in order");
        }
    }

    @Test
    void logicalDeletePartialUniqueIndexesExist() {
        for (Map.Entry<String, String> index : PARTIAL_UNIQUE_INDEXES.entrySet()) {
            List<String> definitions = jdbcTemplate.queryForList(
                    "SELECT indexdef FROM pg_indexes WHERE schemaname = 'auth' "
                    + "AND indexname = ?", String.class, index.getKey());
            assertEquals(1, definitions.size(),
                    "partial-unique index " + index.getKey() + " must exist on auth schema");
            String definition = definitions.get(0);
            assertTrue(definition.contains("UNIQUE"),
                    index.getKey() + " must be UNIQUE: " + definition);
            assertTrue(definition.contains(index.getValue()),
                    index.getKey() + " must index (" + index.getValue() + "): " + definition);
            assertTrue(definition.contains("deleted = 0"),
                    index.getKey() + " must be partial on deleted = 0 so a logical delete "
                    + "never blocks re-creating the value: " + definition);
        }
    }

    @Test
    void foreignKeysExistExactlyWhereTheBindingDesignPutsThem() {
        for (String table : SEVEN_TABLES) {
            Integer fkCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_constraint con "
                    + "JOIN pg_class rel ON rel.oid = con.conrelid "
                    + "JOIN pg_namespace ns ON ns.oid = rel.relnamespace "
                    + "WHERE ns.nspname = 'auth' AND rel.relname = ? AND con.contype = 'f'",
                    Integer.class, table);
            assertEquals(EXPECTED_FK_COUNTS.get(table).intValue(), fkCount.intValue(),
                    "auth." + table + " must carry exactly " + EXPECTED_FK_COUNTS.get(table)
                    + " foreign keys (org_info deliberately has NO self-FK)");
        }
    }
}
