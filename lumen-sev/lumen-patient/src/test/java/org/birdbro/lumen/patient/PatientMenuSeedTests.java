package org.birdbro.lumen.patient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 患者配置 menu-seed proof (patient-domain task 4.3; spec delta system-data "Patient
 * Number Rule Menu Pages" / "Patient Number Rule Button Perms"): the patient ledger's
 * {@code V2__patient_menu.sql} writes CROSS-SCHEMA into {@code system.menu_info}/
 * {@code role_menu} (design D5, the ruled V22/V32 CONTENT-guard discipline) - the L2
 * 「患者配置」 group (type M, NULL perms, sort 3, the family-resident {@code team}
 * icon) under the L1 「设置」 row, the 号码规则 C page (path {@code number-rule},
 * component {@code patient/number-rule/index}, perms {@code patient:rule:list}), the
 * three F buttons ({@code patient:rule:{create,update,delete}}), the admin auto-grant,
 * and the sequence-converging setval tails.
 *
 * <p>The re-execution proof runs the migration statements again and must be a
 * live-row no-op (the {@code AuthExamSettingSeedTests}/{@code AuthWorkstationSeedTests}
 * discipline), including the ancestor-chain completion: a synthetic non-admin role
 * granted ONLY the page binding gets the 「患者配置」 group on the re-run (the V32
 * carry-over shape - TreeUtils drops subtrees whose ancestor chain is broken).</p>
 *
 * <p>Shared long-lived database: every assertion is an existence/contains-style check;
 * the synthetic role is hard-deleted at the end.</p>
 *
 * @author birdbro
 */
class PatientMenuSeedTests extends PatientIntegrationTestBase {

    /** The L2 group's ruled content (type M, NULL perms, sort 3, icon team). */
    private static final String GROUP_TITLE = "患者配置";
    private static final String GROUP_ICON = "team";

    /** The C page's ruled content. */
    private static final String PAGE_PERMS = "patient:rule:list";
    private static final String PAGE_PATH = "number-rule";
    private static final String PAGE_COMPONENT = "patient/number-rule/index";

    /** The three F-button perms (titles 新建/编辑/删除). */
    private static final String[] BUTTON_PERMS = {
            "patient:rule:create", "patient:rule:update", "patient:rule:delete"};

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void patientConfigGroupAndPageSeededUnderSettings() {
        // the L1 设置 anchor row (content-addressed, up_id 0 + type M + title 设置)
        Integer settingsId = jdbcTemplate.queryForObject(
                "SELECT id FROM system.menu_info WHERE up_id = 0 AND type = 'M' "
                        + "AND title = '设置' AND deleted = 0", Integer.class);
        assertTrue(settingsId != null, "the L1 设置 directory must exist");

        // the L2 group: type M, NULL perms, sort 3, icon team, under 设置
        Map<String, Object> group = jdbcTemplate.queryForMap(
                "SELECT id, perms, icon, sort FROM system.menu_info WHERE up_id = ? "
                        + "AND type = 'M' AND title = ? AND deleted = 0", settingsId, GROUP_TITLE);
        assertEquals(GROUP_ICON, group.get("icon"),
                "the 患者配置 group must carry the family-resident team icon");
        assertEquals(3, ((Number) group.get("sort")).intValue(),
                "the 患者配置 group must sit at sort 3 (after the setting groups)");
        assertTrue(group.get("perms") == null,
                "an L2 directory must carry NULL perms (the V28 group shape)");

        // the C page: perms/path/component contract, under the group
        Map<String, Object> page = jdbcTemplate.queryForMap(
                "SELECT id, up_id, path, component FROM system.menu_info WHERE type = 'C' "
                        + "AND perms = ? AND deleted = 0", PAGE_PERMS);
        assertEquals(PAGE_PATH, page.get("path"), "the page must carry the ruled path");
        assertEquals(PAGE_COMPONENT, page.get("component"),
                "the page must carry the ruled component");
        assertEquals(((Number) group.get("id")).intValue(),
                ((Number) page.get("up_id")).intValue(),
                "the page must live under the 患者配置 group");

        // the full ancestor chain the TreeUtils orphan rule demands: page -> group -> 设置
        Integer groupUnderSettings = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE id = ? AND up_id = ? "
                        + "AND type = 'M' AND deleted = 0", Integer.class,
                page.get("up_id"), settingsId);
        assertEquals(Integer.valueOf(1), groupUnderSettings,
                "the page's parent must be the 患者配置 group under the 设置 directory");
    }

    @Test
    void buttonNodesSeededAndAdminAutoGranted() {
        Integer pageId = jdbcTemplate.queryForObject(
                "SELECT id FROM system.menu_info WHERE type = 'C' AND perms = ? AND deleted = 0",
                Integer.class, PAGE_PERMS);
        assertTrue(pageId != null, "the 号码规则 page must exist");

        for (String perms : BUTTON_PERMS) {
            Map<String, Object> button = jdbcTemplate.queryForMap(
                    "SELECT id, up_id, title FROM system.menu_info WHERE type = 'F' "
                            + "AND perms = ? AND deleted = 0", perms);
            assertEquals(pageId.intValue(), ((Number) button.get("up_id")).intValue(),
                    "the F node " + perms + " must live under the 号码规则 page");

            Integer granted = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system.role_menu rm "
                            + "JOIN system.menu_info m ON m.id = rm.menu_id "
                            + "WHERE rm.role_id = 1 AND m.perms = ? AND rm.deleted = 0",
                    Integer.class, perms);
            assertEquals(Integer.valueOf(1), granted,
                    "the admin role must be auto-granted " + perms);
        }

        // admin holds the group and the page too (the console needs the whole chain)
        Integer adminGroupGrant = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu rm "
                        + "JOIN system.menu_info m ON m.id = rm.menu_id "
                        + "WHERE rm.role_id = 1 AND m.type = 'M' AND m.title = ? "
                        + "AND m.deleted = 0 AND rm.deleted = 0", Integer.class, GROUP_TITLE);
        assertEquals(Integer.valueOf(1), adminGroupGrant,
                "the admin role must see the 患者配置 group");
        Integer adminPageGrant = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu rm "
                        + "JOIN system.menu_info m ON m.id = rm.menu_id "
                        + "WHERE rm.role_id = 1 AND m.perms = ? AND rm.deleted = 0",
                Integer.class, PAGE_PERMS);
        assertEquals(Integer.valueOf(1), adminPageGrant,
                "the admin role must see the 号码规则 page");
    }

    @Test
    void nonAdminGrantChainSurvivesReExecution() throws Exception {
        long ts = System.currentTimeMillis();
        String roleName = "患者种子角色_" + ts;
        jdbcTemplate.update(
                "INSERT INTO system.role_info (name, type) VALUES (?, 11)", roleName);
        Integer roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM system.role_info WHERE name = ? AND deleted = 0",
                Integer.class, roleName);
        Integer pageId = jdbcTemplate.queryForObject(
                "SELECT id FROM system.menu_info WHERE type = 'C' AND perms = ? AND deleted = 0",
                Integer.class, PAGE_PERMS);
        Integer groupId = jdbcTemplate.queryForObject(
                "SELECT id FROM system.menu_info WHERE type = 'M' AND title = ? AND deleted = 0",
                Integer.class, GROUP_TITLE);
        try {
            // grant ONLY the page binding (a broken ancestor chain - the defensive case)
            jdbcTemplate.update(
                    "INSERT INTO system.role_menu (role_id, menu_id) VALUES (?, ?)",
                    roleId, pageId);
            Integer before = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system.role_menu WHERE role_id = ? AND menu_id = ? "
                            + "AND deleted = 0", Integer.class, roleId, groupId);

            reExecuteSeed();

            Integer after = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system.role_menu WHERE role_id = ? AND menu_id = ? "
                            + "AND deleted = 0", Integer.class, roleId, groupId);
            assertEquals(Integer.valueOf(1), after,
                    "the re-run must complete the ancestor chain (the group grant)");
            assertTrue(before.intValue() <= after.intValue(),
                    "the carry-over may only add the missing ancestor");
        } finally {
            jdbcTemplate.update("DELETE FROM system.role_menu WHERE role_id = ?", roleId);
            jdbcTemplate.update("DELETE FROM system.role_info WHERE id = ?", roleId);
        }
    }

    @Test
    void seedReExecutionIsARowCountNoOpAndSequenceConverges() throws Exception {
        String liveCount = "SELECT COUNT(*) FROM system.menu_info WHERE deleted = 0 "
                + "AND (perms LIKE 'patient:rule:%' OR (type = 'M' AND title = '"
                + GROUP_TITLE + "'))";
        Integer rowsBefore = jdbcTemplate.queryForObject(liveCount, Integer.class);
        Long grantsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu rm "
                        + "JOIN system.menu_info m ON m.id = rm.menu_id "
                        + "WHERE m.deleted = 0 AND rm.deleted = 0 "
                        + "AND (m.perms LIKE 'patient:rule:%' OR m.title = '" + GROUP_TITLE
                        + "')", Long.class);

        reExecuteSeed();

        Integer rowsAfter = jdbcTemplate.queryForObject(liveCount, Integer.class);
        assertEquals(rowsBefore, rowsAfter, "re-running the seed must change no live row");
        Long grantsAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu rm "
                        + "JOIN system.menu_info m ON m.id = rm.menu_id "
                        + "WHERE m.deleted = 0 AND rm.deleted = 0 "
                        + "AND (m.perms LIKE 'patient:rule:%' OR m.title = '" + GROUP_TITLE
                        + "')", Long.class);
        assertEquals(grantsBefore, grantsAfter, "re-running the seed must add no grant");

        assertTrue(rowsBefore >= 5,
                "the live seed set (1 group + 1 page + 3 F nodes) must be present");

        // sequence convergence: the setval tails park the counters at MAX(id), so the
        // next insert never collides with a seeded id
        long menuMax = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM system.menu_info", Long.class);
        long menuLast = jdbcTemplate.queryForObject(
                "SELECT last_value FROM system.menu_info_id_seq", Long.class);
        assertTrue(menuLast >= menuMax,
                "system.menu_info's sequence must sit at or past MAX(id)");
        long roleMenuMax = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM system.role_menu", Long.class);
        long roleMenuLast = jdbcTemplate.queryForObject(
                "SELECT last_value FROM system.role_menu_id_seq", Long.class);
        assertTrue(roleMenuLast >= roleMenuMax,
                "system.role_menu's sequence must sit at or past MAX(id)");
    }

    // ===================== helpers =====================

    /** Re-executes every statement of the patient seed migration. */
    private void reExecuteSeed() throws SQLException {
        for (String sql : migrationStatements("/db/migration/V2__patient_menu.sql")) {
            try (Connection connection = openMigrationConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }

    /**
     * Every statement of one migration file, split on semicolons - the comments never
     * contain one (the recorded re-execution footgun), so the split is safe.
     */
    private static List<String> migrationStatements(String classPathFile) {
        try {
            String raw = new String(new ClassPathResource(classPathFile).getInputStream()
                    .readAllBytes(), StandardCharsets.UTF_8);
            return Arrays.stream(raw.split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(classPathFile + " must stay on the "
                    + "classpath for the re-execution proof", e);
        }
    }

    /** Plain-JDBC connection to the team test server's database. */
    private static Connection openMigrationConnection() throws SQLException {
        PatientTestEnv env = PatientTestEnv.load();
        Map<String, String> required = env.require(
                PatientTestEnv.PG_HOST, PatientTestEnv.PG_PORT,
                PatientTestEnv.PG_USER, PatientTestEnv.PG_PASSWORD);
        String database = env.optional(PatientTestEnv.DB, PatientTestEnv.DEFAULT_DB);
        String url = "jdbc:postgresql://" + required.get(PatientTestEnv.PG_HOST) + ":"
                + required.get(PatientTestEnv.PG_PORT) + "/" + database
                + "?currentSchema=patient,public";
        return DriverManager.getConnection(url, required.get(PatientTestEnv.PG_USER),
                required.get(PatientTestEnv.PG_PASSWORD));
    }
}
