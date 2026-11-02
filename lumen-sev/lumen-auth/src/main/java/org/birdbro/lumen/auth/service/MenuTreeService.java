package org.birdbro.lumen.auth.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.birdbro.common.core.pojo.user.MenuTree;
import org.birdbro.common.utils.TreeUtils;
import org.birdbro.lumen.auth.entity.MenuInfo;
import org.birdbro.lumen.auth.mapper.MenuInfoMapper;
import org.springframework.stereotype.Service;

/**
 * Assembles the flat {@code menu_info} table into the frontend menu tree and produces
 * the permission strings (auth-rbac-foundation task 2.3, design D5) - the FIRST use of
 * the copied {@link MenuTree} POJO. The login/me responses (Batch 3) consume both: the
 * tree renders the dynamic menus, {@link #permsSet(Collection)} gates the buttons.
 *
 * <p>Field mapping into the copied POJO: {@code id -> mid}, {@code title}, {@code upId},
 * {@code type} (M/C/F), {@code perms}, {@code path}, {@code component}, {@code icon},
 * {@code sort}, and {@code visible} as Integer 1/0 mapped to Boolean. The trimmed lumen
 * columns (query/redirect/app/breadcrumb/action/isCache/isFrame) stay null on the POJO.
 * {@code TreeUtils.buildTree} semantics: roots are the nodes with {@code upId = 0};
 * orphans are dropped; leaf children stay {@code null}.</p>
 *
 * @author birdbro
 */
@Service
public class MenuTreeService {

    private final MenuInfoMapper menuInfoMapper;

    public MenuTreeService(MenuInfoMapper menuInfoMapper) {
        this.menuInfoMapper = menuInfoMapper;
    }

    /**
     * Loads every live menu ({@code deleted = 0} is applied by the copied EntityBase
     * {@code @TableLogic}) and assembles the menu tree.
     *
     * @return the top-level menus with their nested children
     */
    public List<MenuTree> menuTree() {
        return build(menuInfoMapper.selectList(null));
    }

    /**
     * Converts a flat menu list into the {@link MenuTree} contract and assembles the
     * tree (also the seam for tests and future scoped queries).
     *
     * @param menus flat menu rows
     * @return the top-level menus with their nested children
     */
    public List<MenuTree> build(List<MenuInfo> menus) {
        List<MenuTree> nodes = new ArrayList<>(menus.size());
        for (MenuInfo menu : menus) {
            nodes.add(toTreeNode(menu));
        }
        return TreeUtils.buildTree(nodes,
                MenuTree::getMid, MenuTree::getUpId, 0,
                MenuTree::getChildren, MenuTree::setChildren);
    }

    /**
     * The permission strings of the given menu ids - the user's perms Set source for the
     * login/me endpoints (collect the bound menu ids of the user's roles first, then
     * call this). Blank perms are skipped; unknown ids contribute nothing.
     *
     * @param menuIds the menu ids to read perms from (empty in -> empty set out)
     * @return the non-blank perms strings, deduplicated
     */
    public Set<String> permsSet(Collection<Integer> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> perms = new HashSet<>();
        for (MenuInfo menu : menuInfoMapper.selectBatchIds(menuIds)) {
            if (menu.getPerms() != null && !menu.getPerms().trim().isEmpty()) {
                perms.add(menu.getPerms().trim());
            }
        }
        return perms;
    }

    private static MenuTree toTreeNode(MenuInfo menu) {
        MenuTree node = new MenuTree();
        node.setMid(menu.getId());
        node.setUpId(menu.getUpId());
        node.setType(menu.getType());
        node.setTitle(menu.getTitle());
        node.setPerms(menu.getPerms());
        node.setPath(menu.getPath());
        node.setComponent(menu.getComponent());
        node.setIcon(menu.getIcon());
        node.setSort(menu.getSort());
        node.setVisible(Integer.valueOf(1).equals(menu.getVisible()));
        return node;
    }
}
