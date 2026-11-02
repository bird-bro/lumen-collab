package org.birdbro.lumen.auth;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.birdbro.common.core.pojo.user.MenuTree;
import org.birdbro.lumen.auth.entity.UnitInfo;
import org.birdbro.lumen.auth.entity.UserInfo;
import org.birdbro.lumen.auth.mapper.UnitInfoMapper;
import org.birdbro.lumen.auth.mapper.UserInfoMapper;
import org.birdbro.lumen.auth.service.MenuTreeService;
import org.birdbro.lumen.auth.service.UnitTreeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Data-layer proof (auth-rbac-foundation task 2.3): the seven MyBatis-Plus mappers and
 * the tree-assembly services work against the team test server - a mapper insert/select
 * round trip on {@code user_info} (including the logical-delete semantics of the copied
 * {@code EntityBase @TableLogic} contract), plus round trips through {@link
 * UnitTreeService} and {@link MenuTreeService} over the V2 seed rows.
 *
 * <p>Shared long-lived database: anything this test inserts uses a timestamp-suffixed
 * unique name and is cleaned up (logical delete) afterwards.</p>
 *
 * @author birdbro
 */
@Import(AuthTestStubConfig.class)
class AuthDataLayerTests extends AuthIntegrationTestBase {

    /** The seeded admin hash doubles as a realistic stored password for the round trip. */
    private static final String HASH_SAMPLE =
            "$2a$10$/WMgAykBpAYjFFYxZkttUuOkA7HOeSpkUtPTosdRPvIE.uKFYeK2m";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private UnitInfoMapper unitInfoMapper;

    @Autowired
    private UnitTreeService unitTreeService;

    @Autowired
    private MenuTreeService menuTreeService;

    @Test
    void userInfoMapperInsertSelectRoundTrip() {
        String username = "mp.smoke." + System.currentTimeMillis(); // unique - shared DB
        UserInfo user = new UserInfo();
        user.setUsername(username);
        user.setPassword(HASH_SAMPLE);
        user.setMustChangePassword(1);
        user.setEnable(1);
        user.setName("mapper smoke");
        assertEquals(1, userInfoMapper.insert(user), "insert must affect one row");
        assertNotNull(user.getId(), "IdType.AUTO must backfill the database-generated id");
        assertTrue(user.getId() > 1,
                "the sequence must run past the seeded id 1 (V2 setval advance)");

        UserInfo loaded = userInfoMapper.selectById(user.getId());
        assertNotNull(loaded, "the inserted row must select back");
        assertEquals(username, loaded.getUsername());
        assertEquals("mapper smoke", loaded.getName());
        assertEquals(HASH_SAMPLE, loaded.getPassword());
        assertEquals(Integer.valueOf(1), loaded.getMustChangePassword());
        assertEquals(Integer.valueOf(1), loaded.getEnable());
        assertEquals(Integer.valueOf(0), loaded.getDeleted(),
                "the DB default deleted = 0 must surface on the read-back");
        assertNotNull(loaded.getCreateTime(),
                "create_time must come from the DB default (EntityBase declares no fill on it)");

        // Logical delete (EntityBase @TableLogic): MyBatis-Plus SELECTs hide the row,
        // while the physical row stays with deleted = 1.
        assertEquals(1, userInfoMapper.deleteById(user.getId()),
                "the logical delete must affect one row");
        assertNull(userInfoMapper.selectById(user.getId()),
                "a logically deleted row must no longer select back");
        Integer physicalDeleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM auth.user_info WHERE id = ?",
                Integer.class, user.getId());
        assertEquals(Integer.valueOf(1), physicalDeleted,
                "deleteById must UPDATE deleted = 1, not remove the row");
    }

    @Test
    void unitTreeAssemblesFromSeedRoot() {
        String childName = "ut.smoke." + System.currentTimeMillis(); // unique - shared DB
        UnitInfo child = new UnitInfo();
        child.setUpId(1); // the seeded hospital root
        child.setLevel(2);
        child.setType(0);
        child.setName(childName);
        assertEquals(1, unitInfoMapper.insert(child), "child unit insert must affect one row");

        List<UnitInfo> tree = unitTreeService.unitTree();
        UnitInfo root = tree.stream()
                .filter(unit -> Integer.valueOf(1).equals(unit.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(root, "the seeded hospital root (id 1, up_id 0) must be a tree root");
        assertEquals("示例医院", root.getName());
        assertNotNull(root.getChildren(), "the root must nest the inserted child");
        assertTrue(root.getChildren().stream()
                        .anyMatch(unit -> child.getId().equals(unit.getId())),
                "the inserted child must nest under the root via up_id");

        unitInfoMapper.deleteById(child.getId()); // logical cleanup - shared DB
        assertNull(unitInfoMapper.selectById(child.getId()), "cleanup must hide the child");
    }

    @Test
    void menuTreeAssemblesFromSeedMenus() {
        List<MenuTree> tree = menuTreeService.menuTree();

        Set<Integer> rootIds = new HashSet<>();
        for (MenuTree node : tree) {
            rootIds.add(node.getMid());
        }
        assertTrue(rootIds.containsAll(Arrays.asList(1, 3, 4, 5)),
                "seed menus 1/3/4/5 (up_id = 0) must be tree roots; roots were " + rootIds);
        assertFalse(rootIds.contains(2), "menu 2 (up_id = 1) must nest under menu 1");
        assertFalse(rootIds.contains(6), "menu 6 (up_id = 3) must nest under menu 3");

        MenuTree systemDirectory = nodeWithMid(tree, 1);
        assertNotNull(systemDirectory);
        assertEquals("M", systemDirectory.getType());
        assertEquals("系统管理", systemDirectory.getTitle());
        assertTrue(Boolean.TRUE.equals(systemDirectory.getVisible()),
                "visible integer 1 must map to Boolean true on MenuTree");
        assertTrue(systemDirectory.getChildren().stream()
                        .anyMatch(menu -> Integer.valueOf(2).equals(menu.getMid())),
                "user management must nest under the system directory");

        MenuTree radiologyPage = nodeWithMid(tree, 3);
        assertNotNull(radiologyPage);
        assertTrue(radiologyPage.getChildren().stream()
                        .anyMatch(menu -> Integer.valueOf(6).equals(menu.getMid())),
                "the audit button must nest under the radiology page");

        Set<String> allPerms = new HashSet<>();
        collectPerms(tree, allPerms);
        assertTrue(allPerms.contains("system:user:list"),
                "the assembled tree must carry the user-management perms string");
        assertTrue(allPerms.contains("radiology:report:audit"),
                "the assembled tree must carry the audit-button perms string");
    }

    @Test
    void permsSetCollectsPermsOfTheGivenMenuIds() {
        Set<String> perms = menuTreeService.permsSet(Arrays.asList(2, 6));
        assertTrue(perms.containsAll(Arrays.asList("system:user:list",
                        "radiology:report:audit")),
                "permsSet must collect the perms of the given seed menu ids: " + perms);
        assertTrue(menuTreeService.permsSet(Collections.emptyList()).isEmpty(),
                "an empty menu-id collection must produce an empty perms set");
    }

    private static MenuTree nodeWithMid(List<MenuTree> nodes, int mid) {
        for (MenuTree node : nodes) {
            if (Integer.valueOf(mid).equals(node.getMid())) {
                return node;
            }
            if (node.getChildren() != null) {
                MenuTree found = nodeWithMid(node.getChildren(), mid);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void collectPerms(List<MenuTree> nodes, Set<String> into) {
        for (MenuTree node : nodes) {
            if (node.getPerms() != null && !node.getPerms().isEmpty()) {
                into.add(node.getPerms());
            }
            if (node.getChildren() != null) {
                collectPerms(node.getChildren(), into);
            }
        }
    }
}
