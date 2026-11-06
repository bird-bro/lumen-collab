package org.birdbro.lumen.system;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Workstation W-node seed proof (workstation-w-nodes-api task 1.1; spec deltas api +
 * auth-rbac): the V22 migration tears the per-user direct-grant machinery down
 * ({@code user_workstation} table, the assign-ws F node, the
 * {@code menu_info.workstation} column) and seeds the workstation as MENU NODES —
 * three type-W L1 entries (放射工作站/超声工作站/内镜工作站) with the admin
 * auto-grant, re-execution a row-count no-op.
 *
 * @author birdbro
 */
class AuthWorkstationSeedTests extends AuthIntegrationTestBase {

    /** {title} for every seeded W node. */
    private static final String[] W_NODES = {"放射工作站", "超声工作站", "内镜工作站"};

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void wrongMachineryTornDown() {
        Integer table = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'system' AND table_name = 'user_workstation'",
                Integer.class);
        assertEquals(0, table, "the user_workstation table must be dropped");
        Integer column = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'system' AND table_name = 'menu_info' AND column_name = 'workstation'",
                Integer.class);
        assertEquals(0, column, "the workstation marker column must be dropped");
        Integer node = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE perms = 'system:user:assign-ws'",
                Integer.class);
        assertEquals(0, node, "the assign-ws button node must be gone (live or dead)");
        Integer grants = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu rm JOIN system.menu_info m ON m.id = rm.menu_id "
                        + "WHERE m.perms = 'system:user:assign-ws'",
                Integer.class);
        assertEquals(0, grants, "the assign-ws grants must be gone");
    }

    @Test
    void wNodesSeededAtL1WithAdminGrants() {
        for (String title : W_NODES) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system.menu_info "
                            + "WHERE up_id = 0 AND type = 'W' AND title = ? AND deleted = 0",
                    Integer.class, title);
            assertEquals(1, count, "the W node must exist at L1: " + title);
        }
        Integer granted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu rm "
                        + "JOIN system.menu_info m ON m.id = rm.menu_id AND m.deleted = 0 "
                        + "WHERE rm.role_id = 1 AND rm.deleted = 0 AND m.type = 'W'",
                Integer.class);
        assertEquals(3, granted, "the admin role must hold all three W-node grants");
    }

    @Test
    void legacyMarkerColumnDataIsGoneButConsoleL1Stays() {
        // the V21 placeholder L1s (放射科/超声科/内镜科 with the marker column) were
        // deleted by V22 - the console root stays
        Integer placeholders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info "
                        + "WHERE id IN (298, 299, 300) AND deleted = 0",
                Integer.class);
        assertEquals(0, placeholders, "the V21 placeholder L1s must be gone");
        Integer console = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE id = 1 AND deleted = 0",
                Integer.class);
        assertEquals(1, console, "the seeded console root stays");
    }

    @Test
    void seedStatementsReExecuteAsARowCountNoOp() throws Exception {
        long wNodesBefore = wNodeCount();
        long grantsBefore = wGrantCount();

        String sql = Files.readString(Path.of(getClass()
                .getResource("/db/migration/V22__workstation_w_nodes.sql").toURI()));
        for (String statement : splitStatements(sql)) {
            jdbcTemplate.execute(statement);
        }

        assertEquals(wNodesBefore, wNodeCount(), "re-executed seed must not add W nodes");
        assertEquals(grantsBefore, wGrantCount(), "re-executed seed must not add grants");
    }

    private long wNodeCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE type = 'W' AND deleted = 0",
                Long.class);
        return count == null ? 0 : count;
    }

    private long wGrantCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu rm "
                        + "JOIN system.menu_info m ON m.id = rm.menu_id AND m.deleted = 0 "
                        + "WHERE rm.role_id = 1 AND rm.deleted = 0 AND m.type = 'W'",
                Long.class);
        return count == null ? 0 : count;
    }

    /** Strips comment lines, drops empties - the executable statement stream. */
    private static List<String> splitStatements(String sql) {
        return Arrays.stream(sql.split(";"))
                .map(part -> part.lines()
                        .filter(line -> !line.strip().startsWith("--"))
                        .reduce((a, b) -> a + "\n" + b)
                        .map(String::strip)
                        .orElse(""))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
