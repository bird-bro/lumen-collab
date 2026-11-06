package org.birdbro.lumen.system;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Button-perm seed proof (button-perm-enforcement-api task 1.1; spec delta auth-rbac
 * ADDED "Button-Level Permission Enforcement"): the V15 migration seeds 22 button
 * ({@code F}) nodes under the console C pages with the coordination inventory's perms
 * strings, the admin role owns every one of them, and re-executing the seed statements
 * is a row-count no-op (the V5 guarded-insert discipline, proven - not assumed).
 *
 * <p>The inventory table below is the TEST-SIDE copy of
 * {@code openspec/coordination/button-perm-enforcement.md}'s shared design (22 rows:
 * 用户 3 / 角色 4 / 菜单 3 / 机构 3 / 科室 3 / 字典 6). Drift between the migration and
 * this table fails here first.</p>
 *
 * @author birdbro
 */
class AuthButtonSeedTests extends AuthIntegrationTestBase {

    /** {upId (the C page), perms} for every seeded button node - 22 rows. */
    private static final String[][] INVENTORY = {
            {"2", "system:user:create"},
            {"2", "system:user:reset-pwd"},
            {"2", "system:user:enable"},
            {"9", "system:role:create"},
            {"9", "system:role:update"},
            {"9", "system:role:delete"},
            {"9", "system:role:assign"},
            {"10", "system:menu:create"},
            {"10", "system:menu:update"},
            {"10", "system:menu:delete"},
            {"7", "system:org:create"},
            {"7", "system:org:update"},
            {"7", "system:org:delete"},
            {"8", "system:dep:create"},
            {"8", "system:dep:update"},
            {"8", "system:dep:delete"},
            {"11", "system:dict-catal:create"},
            {"11", "system:dict-catal:update"},
            {"11", "system:dict-catal:delete"},
            {"11", "system:dict-detail:create"},
            {"11", "system:dict-detail:update"},
            {"11", "system:dict-detail:delete"},
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void everyInventoryButtonNodeIsSeededUnderItsPage() {
        for (String[] entry : INVENTORY) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system.menu_info "
                            + "WHERE up_id = ? AND type = 'F' AND perms = ? AND deleted = 0",
                    Integer.class, Integer.valueOf(entry[0]), entry[1]);
            assertEquals(1, count, "button node missing under page " + entry[0] + ": " + entry[1]);
        }
        // no strict total: other suites may legitimately create their own F nodes
        // (the enforcement matrix does, transiently) - the seed's contract is the
        // inventory above, each entry exactly once.
    }

    @Test
    void adminRoleOwnsEveryButtonNode() {
        String in = java.util.Arrays.stream(INVENTORY).map(e -> "'" + e[1] + "'")
                .collect(java.util.stream.Collectors.joining(","));
        Integer granted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu rm "
                        + "JOIN system.menu_info m ON m.id = rm.menu_id AND m.deleted = 0 "
                        + "WHERE rm.role_id = 1 AND rm.deleted = 0 AND m.type = 'F' "
                        + "AND m.perms IN (" + in + ")",
                Integer.class);
        assertEquals(22, granted,
                "the admin role (role 1) must hold every button node grant (explicit "
                        + "role_menu model - no wildcard)");
    }

    @Test
    void seedStatementsReExecuteAsARowCountNoOp() throws Exception {
        long menusBefore = menuRowCount();
        long grantsBefore = buttonGrantCount();

        // Re-execute the migration's statements verbatim: the guarded inserts must
        // leave every row count untouched (the hand-apply-before-boot discipline).
        String sql = Files.readString(Path.of(getClass()
                .getResource("/db/migration/V15__button_perm_nodes.sql").toURI()));
        for (String statement : splitStatements(sql)) {
            jdbcTemplate.execute(statement);
        }

        assertEquals(menusBefore, menuRowCount(), "re-executed seed must not add menu rows");
        assertEquals(grantsBefore, buttonGrantCount(), "re-executed seed must not add grants");
    }

    /** Strips comment lines, drops empties - the executable statement stream. */
    private static List<String> splitStatements(String sql) {
        return java.util.Arrays.stream(sql.split(";"))
                .map(part -> part.lines()
                        .filter(line -> !line.strip().startsWith("--"))
                        .reduce((a, b) -> a + "\n" + b)
                        .map(String::strip)
                        .orElse(""))
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }

    private long menuRowCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE type = 'F' AND deleted = 0",
                Long.class);
        return count == null ? 0 : count;
    }

    private long buttonGrantCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu rm "
                        + "JOIN system.menu_info m ON m.id = rm.menu_id AND m.deleted = 0 "
                        + "WHERE rm.role_id = 1 AND rm.deleted = 0 AND m.type = 'F'",
                Long.class);
        return count == null ? 0 : count;
    }
}
