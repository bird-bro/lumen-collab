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
import org.birdbro.lumen.system.service.support.MenuTreeService;
import org.birdbro.lumen.system.service.support.OrgTreeService;
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
        // V12 dropped dict_catal.code - the entity carries no code field and the name
        // carries a timestamp suffix (unique among live rows on the shared DB).
        String name = "数据层往返目录_" + System.currentTimeMillis();
        DictCatalEntity catal = DictCatalEntity.builder()
                .name(name)
                .enable(1)
                .sort(0)
                .build();
        assertTrue(dictCatalService.save(catal), "the dict_catal insert must succeed");
        assertNotNull(catal.getId(), "IdType.AUTO must backfill the database-generated id");
        assertTrue(catal.getId() > 2,
                "the sequence must run past the seeded ids 1/2 (V3 setval advance)");

        DictCatalEntity loaded = dictCatalService.getById(catal.getId());
        assertNotNull(loaded, "the inserted catalog must select back");
        assertEquals(name, loaded.getName());
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

        // HARD cleanup (user ruling 2026-11-07): plain JDBC DELETE - removeById is the
        // @TableLogic soft delete the ruling retires for test residue.
        jdbcTemplate.update("DELETE FROM public.dict_catal WHERE id = ?", catal.getId());
        assertNull(dictCatalService.getById(catal.getId()), "cleanup must remove the catalog");
    }

    @Test
    void dictDetailServiceCollectsSeededLiveDetails() {
        // lumen enable semantics: 1 = live. The seed carries two enable=1 details under
        // the common_status catalog - re-based to id 1000 by V7 (one contiguous 1000
        // range with the V6 real dictionaries). The copied OPS filter used
        // EnableConstants.TRUE = 11 - the red run proved the seeded rows were invisible
        // until the copy adaptation. V9: the display text lives in value (label dropped).
        Map<Integer, List<DictDetailEntity>> map =
                dictDetailService.mapByCatalIds(idListOf(1000));
        assertNotNull(map.get(1000), "catalog 1000 (common_status) must expose its live details");
        assertEquals(2, map.get(1000).size(), "the two seeded status details must be live");
        Set<String> values = new HashSet<>();
        map.get(1000).forEach(item -> values.add(item.getValue()));
        assertTrue(values.contains("启用") && values.contains("停用"),
                "the seeded 启用/停用 details must be the live set: " + values);
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

        // The soft-deleted row was the ASSERTION SUBJECT; it must not become residue
        // (user ruling 2026-11-07): the shared database carries no dead test rows.
        jdbcTemplate.update("DELETE FROM system.user_info WHERE id = ?", user.getId());
    }

    @Test
    void orgTreeAssemblesFromSeedRoot() {
        String childName = "ut.smoke." + System.currentTimeMillis(); // unique - shared DB
        OrgInfo child = new OrgInfo();
        child.setUpId(1); // the seeded hospital root
        child.setLevel(2);
        child.setType(1); // a raw mapper smoke row still models the v1 campus child
        child.setName(childName);
        child.setCode("UT-SMOKE-" + System.currentTimeMillis());
        assertEquals(1, orgInfoMapper.insert(child), "child org insert must affect one row");

        List<OrgInfo> tree = orgTreeService.orgTree();
        OrgInfo root = tree.stream()
                .filter(org -> Integer.valueOf(1).equals(org.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(root, "the seeded hospital root (id 1, up_id 0) must be a tree root");
        // RE-BASELINED 2026-11-12 (user ruling): the root's display name is
        // operator-editable (the shared test DB renamed it) - assert non-blank only.
        assertTrue(root.getName() != null && !root.getName().isBlank(),
                "the tree root must carry a non-blank display name");
        assertNotNull(root.getChildren(), "the root must nest the inserted child");
        assertTrue(root.getChildren().stream()
                        .anyMatch(org -> child.getId().equals(org.getId())),
                "the inserted child must nest under the root via up_id");

        // Name-only campus filtering keeps the hospital root and narrows only the
        // children; the generated code must not be treated as a search needle.
        List<OrgInfo> filtered = orgTreeService.orgTree(childName);
        OrgInfo filteredRoot = filtered.stream()
                .filter(org -> Integer.valueOf(1).equals(org.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(filteredRoot, "the name filter must keep the hospital root");
        assertTrue(filteredRoot.getChildren().stream()
                        .anyMatch(org -> child.getId().equals(org.getId())),
                "the exact campus-name match must survive the filter");
        assertTrue(filteredRoot.getChildren().stream()
                        .allMatch(org -> org.getName().toLowerCase().contains(childName)),
                "the name filter must narrow only campus-name matches");

        List<OrgInfo> codeFiltered = orgTreeService.orgTree(child.getCode());
        OrgInfo codeFilteredRoot = codeFiltered.stream()
                .filter(org -> Integer.valueOf(1).equals(org.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(codeFilteredRoot, "the hospital root stays visible for the profile card");
        List<OrgInfo> codeFilteredChildren = codeFilteredRoot.getChildren();
        assertTrue(codeFilteredChildren == null || codeFilteredChildren.stream()
                        .noneMatch(org -> child.getId().equals(org.getId())),
                "campus code must not be a search field");

        // HARD cleanup (user ruling 2026-11-07): plain JDBC DELETE, not the @TableLogic
        // soft delete the ruling retires for test residue.
        jdbcTemplate.update("DELETE FROM system.org_info WHERE id = ?", child.getId());
        assertNull(orgInfoMapper.selectById(child.getId()), "cleanup must remove the child");
    }

    @Test
    void menuTreeAssemblesFromSeedMenus() {
        List<MenuTree> tree = menuTreeService.menuTree();

        Set<Integer> rootIds = new HashSet<>();
        for (MenuTree node : tree) {
            rootIds.add(node.getMid());
        }
        // V10 parked the department placeholders (3/4/5 + their audit button 6); the
        // console roots are the 系统 directory (1), the L1 设置 group and the
        // workstation W nodes (V22+).
        assertTrue(rootIds.contains(1),
                "the 系统 directory must be a tree root; roots were " + rootIds);
        assertFalse(rootIds.contains(2), "menu 2 (up_id = 14) must nest under the 用户权限 group");
        assertFalse(rootIds.contains(3), "the radiology placeholder is parked (V10)");
        assertFalse(rootIds.contains(4), "the ultrasound placeholder is parked (V10)");
        assertFalse(rootIds.contains(5), "the endoscopy placeholder is parked (V10)");

        MenuTree systemDirectory = nodeWithMid(tree, 1);
        assertNotNull(systemDirectory);
        assertEquals("M", systemDirectory.getType());
        assertEquals("系统", systemDirectory.getTitle());
        assertTrue(Boolean.TRUE.equals(systemDirectory.getVisible()),
                "visible integer 1 must map to Boolean true on MenuTree");
        assertTrue(systemDirectory.getChildren().stream()
                        .anyMatch(menu -> Integer.valueOf(14).equals(menu.getMid())),
                "the 用户权限 group (14) must nest under the system directory (V5 IA)");
        MenuTree userPermGroup = nodeWithMid(tree, 14);
        assertNotNull(userPermGroup);
        assertTrue(userPermGroup.getChildren().stream()
                        .anyMatch(menu -> Integer.valueOf(2).equals(menu.getMid())),
                "user management must nest under the 用户权限 group (V5 IA)");
        MenuTree securityGroup = nodeWithMid(tree, 16);
        assertNotNull(securityGroup);
        assertTrue(securityGroup.getChildren().stream()
                        .anyMatch(menu -> Integer.valueOf(12).equals(menu.getMid())),
                "log query must nest under the 安全 group (V8 IA)");

        Set<String> allPerms = new HashSet<>();
        collectPerms(tree, allPerms);
        assertTrue(allPerms.contains("system:user:list"),
                "the assembled tree must carry the user-management perms string");
    }

    @Test
    void permsSetCollectsPermsOfTheGivenMenuIds() {
        Set<String> perms = menuTreeService.permsSet(Arrays.asList(2, 9));
        assertTrue(perms.containsAll(Arrays.asList("system:user:list",
                        "system:role:list")),
                "permsSet must collect the perms of the given live menu ids: " + perms);
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
