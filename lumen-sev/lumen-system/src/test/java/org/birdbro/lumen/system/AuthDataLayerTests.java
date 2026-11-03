package org.birdbro.lumen.system;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.birdbro.common.core.pojo.IdList;
import org.birdbro.common.core.pojo.user.MenuTree;
import org.birdbro.lumen.system.entity.DictCatalEntity;
import org.birdbro.lumen.system.entity.DictDetailEntity;
import org.birdbro.lumen.system.service.IDictCatalService;
import org.birdbro.lumen.system.service.IDictDetailService;
import org.birdbro.lumen.system.entity.OrgInfo;
import org.birdbro.lumen.system.entity.UserInfo;
import org.birdbro.lumen.system.mapper.OrgInfoMapper;
import org.birdbro.lumen.system.mapper.UserInfoMapper;
import org.birdbro.lumen.system.service.MenuTreeService;
import org.birdbro.lumen.system.service.OrgTreeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * OrgTreeService} and {@link MenuTreeService} over the V2 seed rows.
 *
 * <p>Shared long-lived database: anything this test inserts uses a timestamp-suffixed
 * unique name and is cleaned up (logical delete) afterwards.</p>
 *
 * @author birdbro
 */
class AuthDataLayerTests extends AuthIntegrationTestBase {

    /** The seeded admin hash doubles as a realistic stored password for the round trip. */
    private static final String HASH_SAMPLE =
            "$2a$10$/WMgAykBpAYjFFYxZkttUuOkA7HOeSpkUtPTosdRPvIE.uKFYeK2m";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private OrgInfoMapper orgInfoMapper;

    @Autowired
    private OrgTreeService orgTreeService;

    @Autowired
    private MenuTreeService menuTreeService;

    @Autowired
    private IDictDetailService dictDetailService;

    @Autowired
    private IDictCatalService dictCatalService;

    @Test
    void dictCatalEntityRoundTripResolvesIntoPublicSchema() {
        // system-data task 4.1 (spec scenario 8 at the data layer): the copied entity's
        // UNQUALIFIED @TableName must resolve through currentSchema=system,public to the
        // shared public table - the insert lands there and selects back.
        String code = "ut.dictcatal." + System.currentTimeMillis(); // unique - shared DB
        DictCatalEntity catal = DictCatalEntity.builder()
                .code(code)
                .name("数据层往返目录")
                .enable(1)
                .sort(0)
                .build();
        assertTrue(dictCatalService.save(catal), "the dict_catal insert must succeed");
        assertNotNull(catal.getId(), "IdType.AUTO must backfill the database-generated id");
        assertTrue(catal.getId() > 2,
                "the sequence must run past the seeded ids 1/2 (V3 setval advance)");

        DictCatalEntity loaded = dictCatalService.getById(catal.getId());
        assertNotNull(loaded, "the inserted catalog must select back");
        assertEquals(code, loaded.getCode());
        assertEquals("数据层往返目录", loaded.getName());
        assertEquals(Integer.valueOf(1), loaded.getEnable());

        // The unqualified write landed in PUBLIC: found via the explicitly qualified read.
        Integer inPublic = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.dict_catal WHERE id = ?",
                Integer.class, catal.getId());
        assertEquals(Integer.valueOf(1), inPublic,
                "the entity row must live in public.dict_catal (design D1 search_path resolution)");

        // And no system-schema shadow table exists that could have caught the write first.
        Integer inSystem = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = 'system' AND table_name = 'dict_catal'",
                Integer.class);
        assertEquals(Integer.valueOf(0), inSystem,
                "no system.dict_catal shadow table may exist - the unqualified name must resolve to public");

        dictCatalService.removeById(catal.getId()); // logical cleanup - shared DB
        assertNull(dictCatalService.getById(catal.getId()), "cleanup must hide the catalog");
    }

    @Test
    void dictDetailServiceCollectsSeededLiveDetails() {
        // lumen enable semantics: 1 = live. The V3 seed carries two enable=1 details under
        // catalog 1 (common_status). The copied OPS filter used EnableConstants.TRUE = 11 -
        // the red run proved the seeded rows were invisible until the copy adaptation.
        Map<Integer, List<DictDetailEntity>> map =
                dictDetailService.mapByCatalIds(idListOf(1));
        assertNotNull(map.get(1), "catalog 1 (common_status) must expose its live details");
        assertEquals(2, map.get(1).size(), "the two seeded status details must be live");
        Set<String> labels = new HashSet<>();
        map.get(1).forEach(item -> labels.add(item.getLabel()));
        assertTrue(labels.contains("启用") && labels.contains("停用"),
                "the seeded 启用/停用 details must be the live set: " + labels);
    }

    private static IdList idListOf(Integer... ids) {
        IdList idList = new IdList();
        idList.setIds(Arrays.asList(ids));
        return idList;
    }

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
                "SELECT deleted FROM system.user_info WHERE id = ?",
                Integer.class, user.getId());
        assertEquals(Integer.valueOf(1), physicalDeleted,
                "deleteById must UPDATE deleted = 1, not remove the row");
    }

    @Test
    void orgTreeAssemblesFromSeedRoot() {
        String childName = "ut.smoke." + System.currentTimeMillis(); // unique - shared DB
        OrgInfo child = new OrgInfo();
        child.setUpId(1); // the seeded hospital root
        child.setLevel(2);
        child.setType(0);
        child.setName(childName);
        assertEquals(1, orgInfoMapper.insert(child), "child org insert must affect one row");

        List<OrgInfo> tree = orgTreeService.orgTree();
        OrgInfo root = tree.stream()
                .filter(org -> Integer.valueOf(1).equals(org.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(root, "the seeded hospital root (id 1, up_id 0) must be a tree root");
        assertEquals("示例医院", root.getName());
        assertNotNull(root.getChildren(), "the root must nest the inserted child");
        assertTrue(root.getChildren().stream()
                        .anyMatch(org -> child.getId().equals(org.getId())),
                "the inserted child must nest under the root via up_id");

        orgInfoMapper.deleteById(child.getId()); // logical cleanup - shared DB
        assertNull(orgInfoMapper.selectById(child.getId()), "cleanup must hide the child");
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
