package org.birdbro.lumen.manage;

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
 * Exam-setting menu seed proof (exam-setting-api task 6.1; spec deltas system-data
 * "Exam Setting Menu Pages" / "Exam Setting Button Perms"; re-pointed to the
 * menu-settings-root IA 2026-11-13): V28 seeds the four C pages at the ruled
 * paths/perms and twelve F button nodes, and V30 promotes the group to the L1
 * 「设置」 directory beside the renamed 「系统」 root with grant carry-over. The
 * re-execution proof runs the V28→V30 tail and must be a live-row no-op (the
 * AuthDeviceRoomSeedTests discipline).
 *
 * <p>Shared long-lived database: every assertion is an existence/contains-style check,
 * never an exact row count outside the migration's own seed set.</p>
 *
 * @author birdbro
 */
class AuthExamSettingSeedTests extends AuthIntegrationTestBase {

    /** {perms, path, component, title} of the four seeded C pages (perms = contract). */
    private static final String[][] PAGES = {
            {"setting:item:list", "exam-item", "setting/exam-item/index", "检查项目"},
            {"setting:body:list", "exam-body", "setting/exam-body/index", "检查部位"},
            {"setting:method:list", "exam-method", "setting/exam-method/index", "检查方法"},
            {"setting:temp:list", "rep-template", "setting/rep-template/index", "报告模板"}};

    /** The twelve F-node perms (create/update/delete × four resources). */
    private static final String[] BUTTON_PERMS = {
            "setting:item:create", "setting:item:update", "setting:item:delete",
            "setting:body:create", "setting:body:update", "setting:body:delete",
            "setting:method:create", "setting:method:update", "setting:method:delete",
            "setting:temp:create", "setting:temp:update", "setting:temp:delete"};

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void examSettingDirectoryAndPagesSeeded() {
        // the group is the L1 「设置」 directory now (menu-settings-root): beside the
        // renamed 「系统」 root, carrying the pages unchanged
        Integer directory = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE up_id = 0 AND type = 'M' "
                        + "AND title = '设置' AND deleted = 0", Integer.class);
        assertEquals(1, directory.intValue(),
                "the 设置 directory must sit at the top level beside the 系统 root");
        Integer renamedRoot = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE id = 1 AND type = 'M' "
                        + "AND title = '系统' AND deleted = 0", Integer.class);
        assertEquals(1, renamedRoot.intValue(),
                "the system root (menu 1) must be titled 系统");
        Integer staleGroup = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE type = 'M' "
                        + "AND title = '检查设置' AND deleted = 0", Integer.class);
        assertEquals(0, staleGroup.intValue(),
                "no live L2 检查设置 directory may remain after the promotion");

        for (String[] page : PAGES) {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT path, component, up_id FROM system.menu_info "
                            + "WHERE type = 'C' AND perms = ? AND deleted = 0", page[0]);
            assertEquals(page[1], row.get("path"),
                    "the " + page[3] + " page must carry the ruled path");
            assertEquals(page[2], row.get("component"),
                    "the " + page[3] + " page must carry the ruled component");
            Integer underDirectory = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system.menu_info WHERE id = ? AND up_id = 0 "
                            + "AND type = 'M' AND title = '设置' AND deleted = 0", Integer.class,
                    row.get("up_id"));
            assertEquals(1, underDirectory.intValue(),
                    "the " + page[3] + " page must live under the 设置 directory");
        }
    }

    @Test
    void buttonNodesSeededAndAdminAutoGranted() {
        for (String perms : BUTTON_PERMS) {
            Integer node = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system.menu_info WHERE type = 'F' AND perms = ? "
                            + "AND deleted = 0", Integer.class, perms);
            assertEquals(1, node.intValue(), "the F node " + perms + " must exist");

            Integer granted = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system.role_menu rm "
                            + "JOIN system.menu_info m ON m.id = rm.menu_id "
                            + "WHERE rm.role_id = 1 AND m.perms = ? AND rm.deleted = 0",
                    Integer.class, perms);
            assertEquals(1, granted.intValue(),
                    "the admin role must be auto-granted " + perms);
        }
        // The directory itself is granted too (the console needs the group visible).
        Integer directoryGranted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu rm "
                        + "JOIN system.menu_info m ON m.id = rm.menu_id "
                        + "WHERE rm.role_id = 1 AND m.type = 'M' AND m.title = '设置' "
                        + "AND m.up_id = 0 AND rm.deleted = 0", Integer.class);
        assertEquals(1, directoryGranted.intValue(),
                "the admin role must see the 设置 directory");
    }

    @Test
    void seedReExecutionIsARowCountNoOp() throws SQLException {
        // LIVE rows only: a V28 re-run re-inserts a fresh L2 检查设置 dup (its guard
        // keys on the moved row) that the V30 re-run soft-deletes again - dead rows
        // are not state (menu-settings-root design D2).
        String liveCount = "SELECT COUNT(*) FROM system.menu_info WHERE deleted = 0 "
                + "AND (perms LIKE 'setting:%' OR (type = 'M' AND title IN ('检查设置', '设置')))";
        Integer before = jdbcTemplate.queryForObject(liveCount, Integer.class);

        for (String sql : migrationTailStatements()) {
            try (Connection connection = openMigrationConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }

        Integer after = jdbcTemplate.queryForObject(liveCount, Integer.class);
        assertEquals(before, after,
                "re-running the V28→V30 tail must change no live row");
        assertTrue(before != null && before >= 17,
                "the live seed set (1 directory + 4 pages + 12 F nodes) must be present");
    }

    // ===================== helpers =====================

    /**
     * The V28→V30 tail statements (the convergence unit once V30 mutates V28's rows),
     * split on the semicolons the comments never contain.
     */
    private static List<String> migrationTailStatements() {
        List<String> statements = new java.util.ArrayList<>();
        statements.addAll(migrationStatements("/db/migration/V28__exam_setting_menu.sql"));
        statements.addAll(migrationStatements("/db/migration/V30__menu_settings_root.sql"));
        return statements;
    }

    /** Every statement of one migration file, split on semicolons. */
    private static List<String> migrationStatements(String classPathFile) {
        try {
            String raw = new String(new ClassPathResource(classPathFile).getInputStream()
                    .readAllBytes(), StandardCharsets.UTF_8);
            return java.util.Arrays.stream(raw.split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(classPathFile + " must stay on the "
                    + "classpath for the re-execution proof", e);
        }
    }

    /** Plain-JDBC connection to the team test server's database. */
    private static Connection openMigrationConnection() throws SQLException {
        AuthTestEnv env = AuthTestEnv.load();
        Map<String, String> required = env.require(
                AuthTestEnv.PG_HOST, AuthTestEnv.PG_PORT,
                AuthTestEnv.PG_USER, AuthTestEnv.PG_PASSWORD);
        String database = env.optional(AuthTestEnv.DB, AuthTestEnv.DEFAULT_DB);
        String url = "jdbc:postgresql://" + required.get(AuthTestEnv.PG_HOST) + ":"
                + required.get(AuthTestEnv.PG_PORT) + "/" + database
                + "?currentSchema=system,public";
        return DriverManager.getConnection(url, required.get(AuthTestEnv.PG_USER),
                required.get(AuthTestEnv.PG_PASSWORD));
    }
}
