package org.birdbro.lumen.auth;

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
 * applies the seed to the team test server's {@code auth} schema, the hospital root,
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
                "SELECT up_id, level, type, name FROM auth.org_info WHERE id = 1");
        assertEquals(0, ((Number) root.get("up_id")).intValue(),
                "the hospital root uses up_id = 0 (TreeUtils rootParentValue convention)");
        assertEquals(1, ((Number) root.get("level")).intValue(),
                "the hospital root sits at level 1");
        assertEquals(0, ((Number) root.get("type")).intValue(),
                "the hospital root carries type 0 (lumen-owned hospital-root type)");
        assertEquals("示例医院", root.get("name"),
                "hospital root display name");
    }

    @Test
    void threeDepartmentsSeededUnderTheRoot() {
        String[][] codeAndName = {
                {"radiology", "放射科"},
                {"us", "超声科"},
                {"endo", "内镜科"}};
        for (String[] department : codeAndName) {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT org_id, name FROM auth.org_dep "
                    + "WHERE code = ? AND deleted = 0", department[0]);
            assertEquals(1, ((Number) row.get("org_id")).intValue(),
                    "department " + department[0] + " must anchor to the hospital root");
            assertEquals(department[1], row.get("name"),
                    "department " + department[0] + " display name");
        }
    }

    @Test
    void defaultAdministratorSeeded() {
        Map<String, Object> admin = jdbcTemplate.queryForMap(
                "SELECT password, must_change_password, enable, name FROM auth.user_info "
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
                "SELECT name, type, remark FROM auth.role_info WHERE id = 1");
        assertEquals("系统管理员", role.get("name"));
        assertEquals(10, ((Number) role.get("type")).intValue(),
                "seed role type 10 = system (copied RoleConstants lineage)");
        assertEquals("seed default administrator role", role.get("remark"));

        Integer bindings = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth.user_role "
                + "WHERE user_id = 1 AND role_id = 1 AND deleted = 0", Integer.class);
        assertTrue(bindings >= 1, "user 1 (admin) must be bound to role 1");
    }

    @Test
    void menuSetAndRoleMenuSeeded() {
        // Six seed menus with their expected node types (M directory / C menu / F button)
        // and parent links: menu 2 nests under 1, menu 6 (audit button) nests under 3.
        Object[][] expectedMenus = {
                {1, "M", 0}, {2, "C", 1}, {3, "C", 0},
                {4, "C", 0}, {5, "C", 0}, {6, "F", 3}};
        for (Object[] expected : expectedMenus) {
            int menuId = (Integer) expected[0];
            Map<String, Object> menu = jdbcTemplate.queryForMap(
                    "SELECT type, up_id, title FROM auth.menu_info "
                    + "WHERE id = ? AND deleted = 0", menuId);
            assertEquals(expected[1], ((String) menu.get("type")).trim(),
                    "menu " + menuId + " node type");
            assertEquals(expected[2], ((Number) menu.get("up_id")).intValue(),
                    "menu " + menuId + " parent id");
            assertNotNull(menu.get("title"), "menu " + menuId + " title");
        }

        Integer ownedMenus = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT menu_id) FROM auth.role_menu "
                + "WHERE role_id = 1 AND deleted = 0", Integer.class);
        assertTrue(ownedMenus >= 6,
                "role 1 must own at least the six seed menus (CONTAINS-style lower bound)");

        Integer auditBinding = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth.role_menu rm "
                + "JOIN auth.menu_info m ON m.id = rm.menu_id "
                + "WHERE rm.role_id = 1 AND rm.deleted = 0 "
                + "AND m.perms = 'radiology:report:audit'", Integer.class);
        assertTrue(auditBinding >= 1,
                "role 1 must own the radiology report-audit button example");
    }
}
