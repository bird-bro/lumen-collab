package org.birdbro.lumen.system;

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
 * "Exam Setting Menu Pages" / "Exam Setting Button Perms"): the V28 migration seeds the
 * 「检查设置」 L2 directory under 系统管理 with the four C pages at the ruled
 * paths/perms and twelve F button nodes, auto-grants them to the admin role, and
 * re-executes as a row-count no-op (the AuthDeviceRoomSeedTests discipline).
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
        Integer directory = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE up_id = 1 AND type = 'M' "
                        + "AND title = '检查设置' AND deleted = 0", Integer.class);
        assertEquals(1, directory.intValue(),
                "the 检查设置 directory must sit under the 系统管理 root");

        for (String[] page : PAGES) {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT path, component, up_id FROM system.menu_info "
                            + "WHERE type = 'C' AND perms = ? AND deleted = 0", page[0]);
            assertEquals(page[1], row.get("path"),
                    "the " + page[3] + " page must carry the ruled path");
            assertEquals(page[2], row.get("component"),
                    "the " + page[3] + " page must carry the ruled component");
            Integer underDirectory = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system.menu_info WHERE id = ?", Integer.class,
                    row.get("up_id"));
            assertEquals(1, underDirectory.intValue(),
                    "the " + page[3] + " page must live under the 检查设置 directory");
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
                        + "WHERE rm.role_id = 1 AND m.type = 'M' AND m.title = '检查设置' "
                        + "AND rm.deleted = 0", Integer.class);
        assertEquals(1, directoryGranted.intValue(),
                "the admin role must see the 检查设置 directory");
    }

    @Test
    void seedReExecutionIsARowCountNoOp() throws SQLException {
        Integer before = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE perms LIKE 'setting:%' "
                        + "OR (type = 'M' AND title = '检查设置')", Integer.class);

        for (String sql : migrationStatements()) {
            try (Connection connection = openMigrationConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }

        Integer after = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE perms LIKE 'setting:%' "
                        + "OR (type = 'M' AND title = '检查设置')", Integer.class);
        assertEquals(before, after, "re-running V28 must seed nothing new");
        assertTrue(before != null && before >= 17,
                "the seed set (1 directory + 4 pages + 12 F nodes) must be present");
    }

    // ===================== helpers =====================

    /** Every V28 statement, split on the semicolons the comments never contain. */
    private static List<String> migrationStatements() {
        try {
            String raw = new String(new ClassPathResource(
                    "/db/migration/V28__exam_setting_menu.sql").getInputStream()
                    .readAllBytes(), StandardCharsets.UTF_8);
            return java.util.Arrays.stream(raw.split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("V28__exam_setting_menu.sql must stay on the "
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
