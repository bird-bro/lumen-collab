package org.birdbro.lumen.system;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seed proof for {@code V2__seed.sql} (auth-rbac-foundation task 2.2): after Flyway
 * applies the seed to the team test server's {@code system} schema, the hospital root,
 * the three departments, the default administrator (BCrypt hash + must-change-password),
 * the default role + binding, and the minimal menu set with the audit-button example
 * must all be present.
 *
 * <p>Shared long-lived database: Flyway applies each migration exactly once, so the
 * seed rows are keyed assertions (id / username / code) rather than exact row counts;
 * where counts are asserted at all they are lower bounds (CONTAINS-style).</p>
 *
 * @author birdbro
 */
class AuthSeedMigrationTests extends AuthIntegrationTestBase {

    /**
     * BCrypt hash of the documented initial credentials admin/admin123 (spring-security-
     * crypto 5.5.8 - verified against the exact artifact on the Boot 2.5.15 train before
     * V2 was ever applied to the shared database).
     */
    private static final String SEED_ADMIN_HASH =
            "$2a$10$/WMgAykBpAYjFFYxZkttUuOkA7HOeSpkUtPTosdRPvIE.uKFYeK2m";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void hospitalRootOrgSeeded() {
        Map<String, Object> root = jdbcTemplate.queryForMap(
                "SELECT up_id, level, type, name, code, status FROM system.org_info WHERE id = 1");
        assertEquals(0, ((Number) root.get("up_id")).intValue(),
                "the hospital root uses up_id = 0 (TreeUtils rootParentValue convention)");
        assertEquals(1, ((Number) root.get("level")).intValue(),
                "the hospital root sits at level 1");
        assertEquals(0, ((Number) root.get("type")).intValue(),
                "the hospital root carries type 0 (lumen-owned hospital-root type)");
        assertEquals("未命名医院", root.get("name"),
                "the first-run hospital profile uses the Option B placeholder name");
        assertEquals("HOSPITAL", root.get("code"),
                "the first-run hospital uses the deterministic bootstrap code");
        assertEquals(1, ((Number) root.get("status")).intValue(),
                "the first-run hospital is enabled");

        Map<String, Object> campus = jdbcTemplate.queryForMap(
                "SELECT id, up_id, level, type, name, code, status, is_default "
                + "FROM system.org_info WHERE up_id = 1 AND deleted = 0 AND is_default = 1");
        assertEquals(1, ((Number) campus.get("up_id")).intValue(),
                "the default campus belongs to the hospital");
        assertEquals(2, ((Number) campus.get("level")).intValue(),
                "the default campus sits at level 2");
        assertEquals(1, ((Number) campus.get("type")).intValue(),
                "the default campus carries the campus type");
        assertEquals("总院区", campus.get("name"),
                "the default campus uses the Option B placeholder name");
        assertEquals(1, ((Number) campus.get("status")).intValue(),
                "the default campus is enabled");
        assertEquals(1, ((Number) campus.get("is_default")).intValue(),
                "the bootstrap campus is marked default");
    }

    @Test
    void threeDepartmentsSeededUnderTheDefaultCampus() {
        String[][] codeAndName = {
                {"radiology", "放射科"},
                {"us", "超声科"},
                {"endo", "内镜科"}};
        Integer campusId = defaultCampusId();
        for (String[] department : codeAndName) {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT org_id, name, status FROM system.org_dep "
                    + "WHERE code = ? AND deleted = 0", department[0]);
            assertEquals(campusId.intValue(), ((Number) row.get("org_id")).intValue(),
                    "department " + department[0] + " must anchor to the default campus");
            assertEquals(department[1], row.get("name"),
                    "department " + department[0] + " display name");
            assertEquals(1, ((Number) row.get("status")).intValue(),
                    "the migrated department is enabled");
        }
    }

    private Integer defaultCampusId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM system.org_info WHERE up_id = 1 AND deleted = 0 "
                + "AND is_default = 1 ORDER BY id LIMIT 1", Integer.class);
    }

    @Test
    void defaultAdministratorSeeded() {
        Map<String, Object> admin = jdbcTemplate.queryForMap(
                "SELECT password, must_change_password, enable, name FROM system.user_info "
                + "WHERE username = 'admin' AND deleted = 0");
        assertEquals(1, ((Number) admin.get("must_change_password")).intValue(),
                "the seeded admin must carry the must-change-password flag on first login");
        assertEquals(1, ((Number) admin.get("enable")).intValue());
        assertEquals("系统管理员", admin.get("name"));
        String seededHash = (String) admin.get("password");
        assertTrue(new BCryptPasswordEncoder().matches("admin123", seededHash),
                "the seeded admin hash must verify the documented initial credentials "
                + "admin/admin123 so a fresh deployment can log in");
    }

    @Test
    void defaultRoleAndUserBindingSeeded() {
        Map<String, Object> role = jdbcTemplate.queryForMap(
                "SELECT name, type, remark FROM system.role_info WHERE id = 1");
        assertEquals("系统管理员", role.get("name"));
        assertEquals(10, ((Number) role.get("type")).intValue(),
                "seed role type 10 = system (copied RoleConstants lineage)");
        assertEquals("seed default administrator role", role.get("remark"));

        Integer bindings = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.user_role "
                + "WHERE user_id = 1 AND role_id = 1 AND deleted = 0", Integer.class);
        assertTrue(bindings >= 1, "user 1 (admin) must be bound to role 1");
    }

    @Test
    void menuSetAndRoleMenuSeeded() {
        // The live menu set with node types (M directory / C menu / F button) and
        // parent links after the console-IA migrations: V5 three-level structure,
        // V8 安全 group, and V10 parking the department placeholders (3/4/5 and the
        // audit button 6 are logically deleted - asserted absent below). 用户管理 (2)
        // nests under the 用户权限 group (14); the six console pages hang off groups
        // 13/14/15/16.
        Object[][] expectedMenus = {
                {1, "M", 0},
                {13, "M", 1}, {14, "M", 1}, {15, "M", 1}, {16, "M", 1},
                {7, "C", 13}, {8, "C", 13}, {2, "C", 14}, {9, "C", 14},
                {10, "C", 15}, {11, "C", 15}, {12, "C", 16}};
        for (Object[] expected : expectedMenus) {
            int menuId = (Integer) expected[0];
            Map<String, Object> menu = jdbcTemplate.queryForMap(
                    "SELECT type, up_id, title FROM system.menu_info "
                    + "WHERE id = ? AND deleted = 0", menuId);
            assertEquals(expected[1], ((String) menu.get("type")).trim(),
                    "menu " + menuId + " node type");
            assertEquals(expected[2], ((Number) menu.get("up_id")).intValue(),
                    "menu " + menuId + " parent id");
            assertNotNull(menu.get("title"), "menu " + menuId + " title");
        }

        Integer parkedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info "
                + "WHERE id IN (3, 4, 5, 6) AND deleted = 0", Integer.class);
        assertEquals(Integer.valueOf(0), parkedCount,
                "the department placeholder menus (3/4/5/6) must stay parked (V10)");

        Integer ownedMenus = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT menu_id) FROM system.role_menu "
                + "WHERE role_id = 1 AND deleted = 0", Integer.class);
        assertTrue(ownedMenus >= 12,
                "role 1 must own at least the twelve live seeded menus (CONTAINS-style lower bound)");
    }
}
