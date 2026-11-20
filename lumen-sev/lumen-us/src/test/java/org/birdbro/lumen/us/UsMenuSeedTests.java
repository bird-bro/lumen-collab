package org.birdbro.lumen.us;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Menu-seed proof for ledger V3 (us-station-registration-sev task 1.3, design D2;
 * specs/us-station "Menu and Perms Seed"): the 登记 C page joins the existing US
 * workstation W node (anchored by CONTENT - up_id 0 + type W + title 超声工作站, the
 * V22 contract keys; ids are never hardcoded) with component {@code
 * us/registration/index} and perm {@code us:order:list}, the F button family
 * 新建/登记/取消 rides {@code us:order:create/update/cancel}, and the admin role holds
 * every seeded node (the whole ancestor chain the console needs). All assertions key
 * on CONTENT (perms/titles), never ids - the ruled V22/V32 CONTENT-guard discipline.
 *
 * <p>Re-execution proof (the patient-V2 discipline): every V3 statement is guarded and
 * re-runs as a row-count no-op on the live shared schema - proven, not assumed.</p>
 *
 * @author birdbro
 */
class UsMenuSeedTests extends UsIntegrationTestBase {

    /** The W-node content anchor (the V22 contract keys - never an assumed id). */
    private static final String W_ANCHOR_SQL =
            "SELECT id FROM system.menu_info "
            + "WHERE up_id = 0 AND type = 'W' AND title = '超声工作站' AND deleted = 0";

    /** The perms family this seed owns. */
    private static final List<String> US_PERMS = Arrays.asList(
            "us:order:list", "us:order:create", "us:order:update", "us:order:cancel");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void registrationPageJoinsTheUsWorkstationTree() {
        Integer wCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" + W_ANCHOR_SQL + ") w", Integer.class);
        assertEquals(1, wCount.intValue(),
                "the US workstation W node must exist exactly once (the V22 anchor)");

        List<Map<String, Object>> page = jdbcTemplate.queryForList(
                "SELECT title, path, component, sort FROM system.menu_info "
                + "WHERE type = 'C' AND perms = 'us:order:list' AND deleted = 0 "
                + "AND up_id = (" + W_ANCHOR_SQL + ")");
        assertEquals(1, page.size(),
                "exactly one 登记 C page must hang under the US workstation W node");
        assertEquals("登记", page.get(0).get("title"), "the C page title is 登记");
        assertEquals("registration", page.get(0).get("path"), "the C page path is registration");
        assertEquals("us/registration/index", page.get(0).get("component"),
                "the C page component key is us/registration/index");
    }

    @Test
    void buttonFamilyRidesThePage() {
        for (int i = 0; i < US_PERMS.size() - 1; i++) {
            String perms = US_PERMS.get(i + 1);
            List<Map<String, Object>> button = jdbcTemplate.queryForList(
                    "SELECT title, sort FROM system.menu_info "
                    + "WHERE type = 'F' AND perms = ? AND deleted = 0 "
                    + "AND up_id = (SELECT id FROM system.menu_info "
                    + "WHERE type = 'C' AND perms = 'us:order:list' AND deleted = 0)",
                    perms);
            assertEquals(1, button.size(),
                    "exactly one F button with perm " + perms + " must hang under the 登记 page");
            assertEquals(i + 1, ((Number) button.get(0).get("sort")).intValue(),
                    "the " + perms + " button sort must be " + (i + 1));
        }
        // the titles, content-guarded
        for (String title : Arrays.asList("新建", "登记", "取消")) {
            Integer found = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system.menu_info "
                    + "WHERE type = 'F' AND title = ? AND deleted = 0 "
                    + "AND perms LIKE 'us:order:%'", Integer.class, title);
            assertEquals(1, found.intValue(),
                    "exactly one F button titled " + title + " must exist in the us family");
        }
    }

    @Test
    void adminRoleHoldsEverySeededNode() {
        for (String perms : US_PERMS) {
            Integer granted = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system.role_menu rm "
                    + "JOIN system.menu_info m ON m.id = rm.menu_id "
                    + "WHERE rm.role_id = 1 AND m.perms = ? AND m.deleted = 0 AND rm.deleted = 0",
                    Integer.class, perms);
            assertEquals(1, granted.intValue(),
                    "the admin role must hold the seeded node " + perms);
        }
        Integer wGranted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu rm "
                + "JOIN system.menu_info m ON m.id = rm.menu_id "
                + "WHERE rm.role_id = 1 AND m.type = 'W' AND m.title = '超声工作站' "
                + "AND m.deleted = 0 AND rm.deleted = 0", Integer.class);
        assertEquals(1, wGranted.intValue(),
                "the admin role must hold the US workstation W ancestor (the V22 grant)");
    }

    @Test
    void buttonHoldersAlwaysHoldTheWorkstationAncestor() {
        // The carry-over invariant: any role holding the C page or an F button also
        // holds the W ancestor - a broken ancestor chain would drop the subtree in the
        // console's TreeUtils rendering (the V32 defensive shape).
        Integer broken = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT rm.role_id) FROM system.role_menu rm "
                + "JOIN system.menu_info m ON m.id = rm.menu_id "
                + "WHERE m.perms IN ('us:order:list', 'us:order:create', "
                + "'us:order:update', 'us:order:cancel') AND m.deleted = 0 AND rm.deleted = 0 "
                + "AND NOT EXISTS (SELECT 1 FROM system.role_menu rm2 "
                + "JOIN system.menu_info w ON w.id = rm2.menu_id "
                + "WHERE rm2.role_id = rm.role_id AND w.type = 'W' "
                + "AND w.title = '超声工作站' AND w.deleted = 0 AND rm2.deleted = 0)",
                Integer.class);
        assertEquals(0, broken.intValue(),
                "no role may hold a us:order:* node without its W ancestor");
    }

    @Test
    void reExecutionIsARowCountNoOp() throws Exception {
        List<Map<String, Object>> before = seedState();

        reExecuteSeed();

        List<Map<String, Object>> after = seedState();
        assertEquals(before, after,
                "re-running V3 must not add, remove, or change any menu/role row");
    }

    // ===================== helpers =====================

    /** Row counts of the seed-owned menu rows + admin grants (the re-execution yardstick). */
    private List<Map<String, Object>> seedState() {
        return jdbcTemplate.queryForList(
                "SELECT (SELECT COUNT(*) FROM system.menu_info WHERE perms LIKE 'us:order:%' "
                + "AND deleted = 0) AS menu_rows, "
                + "(SELECT COUNT(*) FROM system.menu_info WHERE type = 'C' "
                + "AND perms = 'us:order:list' AND deleted = 0) AS page_rows, "
                + "(SELECT COUNT(*) FROM system.role_menu rm JOIN system.menu_info m "
                + "ON m.id = rm.menu_id WHERE rm.role_id = 1 AND m.perms LIKE 'us:order:%' "
                + "AND m.deleted = 0 AND rm.deleted = 0) AS admin_grants");
    }

    /** Re-executes every statement of the us menu seed migration. */
    private void reExecuteSeed() throws SQLException {
        for (String sql : migrationStatements("/db/migration/V3__us_menu_seed.sql")) {
            try (Connection connection = openMigrationConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }

    /**
     * Every statement of one migration file: the {@code --} comment lines are stripped
     * first, then the remainder splits on semicolons (the UsSchemaMigrationTests
     * discipline - the strip-then-split the kernel header prescribes).
     */
    private static List<String> migrationStatements(String classPathFile) {
        try {
            String raw = new String(new ClassPathResource(classPathFile).getInputStream()
                    .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            StringBuilder stripped = new StringBuilder();
            for (String line : raw.split("\n", -1)) {
                if (!line.trim().startsWith("--")) {
                    stripped.append(line).append('\n');
                }
            }
            return Arrays.stream(stripped.toString().split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(classPathFile + " must stay on the "
                    + "classpath for the re-execution proof", e);
        }
    }

    /** Plain-JDBC connection to the team test server's database (system-schema writes). */
    private static Connection openMigrationConnection() throws SQLException {
        UsTestEnv env = UsTestEnv.load();
        Map<String, String> required = env.require(
                UsTestEnv.PG_HOST, UsTestEnv.PG_PORT,
                UsTestEnv.PG_USER, UsTestEnv.PG_PASSWORD);
        String database = env.optional(UsTestEnv.DB, UsTestEnv.DEFAULT_DB);
        String url = "jdbc:postgresql://" + required.get(UsTestEnv.PG_HOST) + ":"
                + required.get(UsTestEnv.PG_PORT) + "/" + database
                + "?currentSchema=us,public";
        return DriverManager.getConnection(url, required.get(UsTestEnv.PG_USER),
                required.get(UsTestEnv.PG_PASSWORD));
    }
}
