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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * User-profile migration proof (user-edit-delete-profile-api task 1.1; spec deltas data +
 * system-data + auth-rbac): the V26 migration adds the eight nullable profile columns to
 * {@code system.user_info}, seeds the 「检查类型」/「审核权限」 dict catalogs (deterministic
 * ids 1021/1022, details +200000 band), and seeds the two F nodes (编辑用户/删除用户)
 * under 用户管理 with the admin auto-grant - re-execution a row-count no-op.
 *
 * <p>Shared long-lived database: every assertion is an existence/contains-style check,
 * never an exact row count outside the migration's own seed set.</p>
 *
 * @author birdbro
 */
class AuthUserProfileSeedTests extends AuthIntegrationTestBase {

    /** The eight profile columns V26 adds (spec data "User Profile Columns"). */
    private static final String[] PROFILE_COLUMNS = {
            "phone", "email", "gender", "title", "dep_id", "modalitys", "rep_audit", "signature"};

    /* The 「检查类型」 catalog (V26 id 1021) was RETIRED by V27's unification
     * (ruling 2026-11-12): 1024 设备类型 is the single modality vocabulary - its
     * retirement is asserted in AuthDeviceRoomSeedTests, and the re-execution test
     * below replays V26 THEN V27 so the cumulative stream stays a no-op. */

    /** {code, value} pairs of the seeded 「审核权限」 catalog (id 1022). */
    private static final String[][] REP_AUDIT_ITEMS = {
            {"0", "不参与"}, {"1", "报告医生"}, {"2", "初审医生"}, {"3", "终审医生"}};

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void profileColumnsExistOnUserInfo() {
        for (String column : PROFILE_COLUMNS) {
            Integer found = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = 'system' AND table_name = 'user_info' "
                            + "AND column_name = ?",
                    Integer.class, column);
            assertEquals(1, found.intValue(),
                    "system.user_info must carry the profile column " + column);
        }
        // Nullable with no default (the spec's "all nullable, no defaults" clause):
        // an INSERT that predates the change must keep working.
        Integer nonNullable = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'system' AND table_name = 'user_info' "
                        + "AND column_name IN ('phone','email','gender','title','dep_id',"
                        + "'modalitys','rep_audit','signature') AND is_nullable = 'NO'",
                Integer.class);
        assertEquals(0, nonNullable.intValue(),
                "every profile column must be nullable - pre-change INSERTs keep working");
    }

    @Test
    void user_infoStaysAtZeroPhysicalForeignKeys() {
        // The binding design's D1 shape (spec data, clarified 2026-11-11): dep_id is a
        // SERVICE-enforced reference (the update path A0400s a dead department);
        // user_info carries NO physical FK - the same rule that keeps org_info
        // without its self-FK, and AuthSchemaMigrationTests pins the count at 0.
        Integer fk = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint con "
                        + "JOIN pg_class rel ON rel.oid = con.conrelid "
                        + "JOIN pg_namespace ns ON ns.oid = rel.relnamespace "
                        + "WHERE ns.nspname = 'system' AND rel.relname = 'user_info' "
                        + "AND con.contype = 'f'",
                Integer.class);
        assertEquals(0, fk.intValue(),
                "user_info must stay at zero physical FKs (dep_id integrity is service-level)");
    }

    @Test
    void examTypeCatalogRetiredByUnification() {
        Integer retired = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.dict_catal WHERE name = '检查类型' AND deleted = 0",
                Integer.class);
        assertEquals(0, retired.intValue(),
                "the 检查类型 catalog must stay retired (1024 设备类型 is the single source)");
    }

    @Test
    void repAuditCatalogSeededWithDetails() {
        assertCatalogDetails("审核权限", 1022, REP_AUDIT_ITEMS);
    }

    @Test
    void editDeleteButtonNodesSeededWithAdminGrants() {
        assertButtonNode("编辑用户", "system:user:update", 5);
        assertButtonNode("删除用户", "system:user:delete", 6);
        for (String perms : new String[] {"system:user:update", "system:user:delete"}) {
            Integer granted = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system.role_menu rm "
                            + "JOIN system.menu_info m ON m.id = rm.menu_id AND m.deleted = 0 "
                            + "WHERE rm.role_id = 1 AND rm.deleted = 0 AND m.perms = ?",
                    Integer.class, perms);
            assertEquals(1, granted,
                    "the admin role must hold the " + perms + " grant");
        }
    }

    @Test
    void seedStatementsReExecuteAsARowCountNoOp() throws Exception {
        long catalogsBefore = dictCatalogCount();
        long detailsBefore = dictDetailCount();
        long nodesBefore = profileNodeCount();
        long grantsBefore = profileGrantCount();

        // The CUMULATIVE stream re-executes: V26 alone would resurrect the retired
        // 检查类型 1021 that V27's unification deletes - replaying V27 right after
        // converges the pair back to the ruled state (a true row-count no-op).
        for (String migration : new String[] {"V26__user_profile_columns.sql",
                "V27__org_device_room.sql"}) {
            String sql = Files.readString(Path.of(getClass()
                    .getResource("/db/migration/" + migration).toURI()));
            for (String statement : splitStatements(sql)) {
                jdbcTemplate.execute(statement);
            }
        }

        assertEquals(catalogsBefore, dictCatalogCount(), "re-run must not add catalogs");
        assertEquals(detailsBefore, dictDetailCount(), "re-run must not add details");
        assertEquals(nodesBefore, profileNodeCount(), "re-run must not add F nodes");
        assertEquals(grantsBefore, profileGrantCount(), "re-run must not add grants");
    }

    // ===================== helpers =====================

    private void assertCatalogDetails(String catalogName, int catalogId, String[][] items) {
        Integer catalog = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.dict_catal WHERE id = ? AND name = ? AND deleted = 0",
                Integer.class, catalogId, catalogName);
        assertEquals(1, catalog.intValue(),
                "the dict catalog " + catalogName + " (id " + catalogId + ") must exist");
        for (String[] item : items) {
            Integer detail = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.dict_detail "
                            + "WHERE ca_id = ? AND code = ? AND value = ? AND enable = 1 AND deleted = 0",
                    Integer.class, catalogId, Integer.valueOf(item[0]), item[1]);
            assertEquals(1, detail.intValue(),
                    "the catalog " + catalogName + " must carry detail code " + item[0]
                            + " = " + item[1]);
        }
    }

    private void assertButtonNode(String title, String perms, int sort) {
        Integer node = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info "
                        + "WHERE up_id = 2 AND type = 'F' AND title = ? AND perms = ? "
                        + "AND sort = ? AND deleted = 0",
                Integer.class, title, perms, sort);
        assertEquals(1, node.intValue(),
                "the F node " + title + " (" + perms + ", sort " + sort + ") must exist under 用户管理");
    }

    private long dictCatalogCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.dict_catal WHERE id IN (1021, 1022)",
                Long.class);
        return count == null ? 0 : count;
    }

    private long dictDetailCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.dict_detail WHERE ca_id IN (1021, 1022)",
                Long.class);
        return count == null ? 0 : count;
    }

    private long profileNodeCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.menu_info WHERE up_id = 2 AND type = 'F' "
                        + "AND perms IN ('system:user:update', 'system:user:delete') AND deleted = 0",
                Long.class);
        return count == null ? 0 : count;
    }

    private long profileGrantCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system.role_menu rm "
                        + "JOIN system.menu_info m ON m.id = rm.menu_id AND m.deleted = 0 "
                        + "WHERE rm.role_id = 1 AND rm.deleted = 0 "
                        + "AND m.perms IN ('system:user:update', 'system:user:delete')",
                Long.class);
        return count == null ? 0 : count;
    }

    /**
     * Strips comment lines, drops empties - the executable statement stream. Splitting
     * is DOLLAR-QUOTE-AWARE: the naive {@code sql.split(";")} of the V15/V22 house
     * splitter cannot carry a DO block (its inner {@code END IF;}/{@code ALTER ...;}
     * semicolons truncate the block mid-body and PostgreSQL answers "Unterminated
     * dollar quote"). V26's FK guard is the family's first DO block under a re-run
     * test, so the splitter scans for {@code $tag$ ... $tag$} spans and passes them
     * through unsplit - which is also exactly how Flyway's own parser treats them.
     */
    private static List<String> splitStatements(String sql) {
        List<String> statements = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        String openTag = null;
        for (String line : sql.split("\n", -1)) {
            String stripped = line.strip();
            if (openTag == null && stripped.startsWith("--")) {
                continue; // comment line outside a dollar-quote span
            }
            if (openTag == null) {
                java.util.regex.Matcher opener = java.util.regex.Pattern
                        .compile("\\$(\\w+)\\$").matcher(line);
                if (stripped.startsWith("DO ") && opener.find()) {
                    openTag = "$" + opener.group(1) + "$";
                }
            } else if (line.contains(openTag)) {
                openTag = null; // the closing tag may ride its own line - after this
                                // line the block is complete
                current.append(line).append('\n');
                statements.add(current.toString().strip());
                current.setLength(0);
                continue;
            }
            current.append(line).append('\n');
            if (openTag == null && line.contains(";")) {
                String statement = current.toString().strip();
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }
                current.setLength(0);
            }
        }
        String tail = current.toString().strip();
        if (!tail.isEmpty()) {
            statements.add(tail);
        }
        return statements;
    }
}
