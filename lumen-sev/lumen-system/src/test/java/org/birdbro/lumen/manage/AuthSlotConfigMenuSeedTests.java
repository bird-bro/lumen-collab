package org.birdbro.lumen.manage;

import java.sql.Connection;
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
 * The 预约设置 menu-seed proof (us-registration-redesign-api task 5.3; specs/system-data
 * "Exam Setting Menu Pages" / "Exam Setting Button Perms" - the additive-leaf scenario):
 * the C leaf 预约设置 hangs under the EXISTING L2 「检查配置」 group (NO rename - the
 * menu-settings-root migration already shipped it, design D6), the three F buttons
 * carry the {@code setting:slot:*} family, the admin role is auto-granted, and the V51
 * statements re-execute as a row-count no-op (the V15/V28 CONTENT-guard discipline).
 *
 * @author birdbro
 */
class AuthSlotConfigMenuSeedTests extends AuthIntegrationTestBase {

    /** The slot F-button perms (the V15 CONTENT-guard family key). */
    private static final String[] SLOT_BUTTON_PERMS = {
            "setting:slot:create", "setting:slot:update", "setting:slot:delete"};

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void slotConfigLeafSeededUnderTheExamConfigGroup() {
        // the C leaf, addressed by perms among live rows (ids are data)
        Map<String, Object> leaf = jdbcTemplate.queryForMap(
                "SELECT path, component, up_id FROM system.menu_info "
                        + "WHERE type = 'C' AND perms = 'setting:slot:list' AND deleted = 0");
        assertEquals("exam-slot", leaf.get("path"), "the leaf carries the ruled path");
        assertEquals("setting/exam-slot/index", leaf.get("component"),
                "the leaf carries the ruled component key");

        // the full ancestor chain: leaf -> 检查配置 -> 设置 (up_id = 0)
        Map<String, Object> group = jdbcTemplate.queryForMap(
                "SELECT up_id FROM system.menu_info WHERE id = ? AND type = 'M' "
                        + "AND title = '检查配置' AND deleted = 0", leaf.get("up_id"));
        Integer underDirectory = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE id = ? AND up_id = 0 "
                        + "AND type = 'M' AND title = '设置' AND deleted = 0",
                Integer.class, group.get("up_id"));
        assertEquals(1, underDirectory.intValue(),
                "the 预约设置 leaf must live under 检查配置 inside the 设置 directory");
    }

    @Test
    void slotButtonNodesSeededAndAdminAutoGranted() {
        Integer leafId = jdbcTemplate.queryForObject(
                "SELECT id FROM system.menu_info WHERE type = 'C' "
                        + "AND perms = 'setting:slot:list' AND deleted = 0", Integer.class);
        for (String perm : SLOT_BUTTON_PERMS) {
            Integer button = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system.menu_info WHERE up_id = ? AND type = 'F' "
                            + "AND perms = ? AND deleted = 0", Integer.class, leafId, perm);
            assertEquals(1, button.intValue(), "the " + perm + " F node must exist");
            Integer grant = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system.role_menu rm JOIN system.menu_info m "
                            + "ON m.id = rm.menu_id AND m.deleted = 0 "
                            + "WHERE rm.role_id = 1 AND rm.deleted = 0 AND m.perms = ?",
                    Integer.class, perm);
            assertEquals(1, grant.intValue(),
                    "the admin role must be auto-granted " + perm);
        }
        Integer leafGrant = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu rm JOIN system.menu_info m "
                        + "ON m.id = rm.menu_id AND m.deleted = 0 "
                        + "WHERE rm.role_id = 1 AND rm.deleted = 0 "
                        + "AND m.perms = 'setting:slot:list'",
                Integer.class);
        assertEquals(1, leafGrant.intValue(), "the admin role must hold the page grant");
    }

    @Test
    void slotConfigSeedReExecutionIsARowCountNoOp() throws SQLException {
        int nodesBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE perms LIKE 'setting:slot:%' "
                        + "AND deleted = 0", Integer.class);
        int grantsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu rm JOIN system.menu_info m "
                        + "ON m.id = rm.menu_id AND m.deleted = 0 "
                        + "WHERE rm.role_id = 1 AND m.perms LIKE 'setting:slot:%'",
                Integer.class);

        replayMigration("/db/migration/V51__slot_config_menu.sql");

        int nodesAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE perms LIKE 'setting:slot:%' "
                        + "AND deleted = 0", Integer.class);
        int grantsAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu rm JOIN system.menu_info m "
                        + "ON m.id = rm.menu_id AND m.deleted = 0 "
                        + "WHERE rm.role_id = 1 AND m.perms LIKE 'setting:slot:%'",
                Integer.class);
        assertEquals(nodesBefore, nodesAfter, "the re-executed seed must not add nodes");
        assertEquals(grantsBefore, grantsAfter, "the re-executed seed must not add grants");
        assertTrue(nodesBefore >= 4, "the leaf + three buttons must exist before the replay");
    }

    // ---------------------------------------------------------------- helpers

    /** Strips comment lines, splits on semicolons, executes into the live schema
     *  (the CONTENT-guard discipline makes the replay a no-op). */
    private void replayMigration(String classPathFile) throws SQLException {
        try {
            String raw = new String(new ClassPathResource(classPathFile).getInputStream()
                    .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            StringBuilder stripped = new StringBuilder();
            for (String line : raw.split("\n", -1)) {
                if (!line.trim().startsWith("--")) {
                    stripped.append(line).append('\n');
                }
            }
            List<String> statements = java.util.Arrays.stream(stripped.toString().split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
            for (String sql : statements) {
                try (Connection connection = openMigrationConnection();
                     Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(classPathFile + " must stay on the "
                    + "classpath for the re-execution proof", e);
        }
    }

    /** The live-schema connection (the AuthExamSettingSeedTests replay precedent -
     *  the same env contract, a plain duplicate to keep the suites decoupled). */
    private static Connection openMigrationConnection() throws SQLException {
        AuthTestEnv env = AuthTestEnv.load();
        Map<String, String> required = env.require(
                AuthTestEnv.PG_HOST, AuthTestEnv.PG_PORT,
                AuthTestEnv.PG_USER, AuthTestEnv.PG_PASSWORD);
        String database = env.optional(AuthTestEnv.DB, AuthTestEnv.DEFAULT_DB);
        String url = "jdbc:postgresql://" + required.get(AuthTestEnv.PG_HOST) + ":"
                + required.get(AuthTestEnv.PG_PORT) + "/" + database
                + "?currentSchema=system,public";
        return java.sql.DriverManager.getConnection(url, required.get(AuthTestEnv.PG_USER),
                required.get(AuthTestEnv.PG_PASSWORD));
    }
}
