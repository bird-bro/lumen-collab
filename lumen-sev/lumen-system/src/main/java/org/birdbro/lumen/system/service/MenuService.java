package org.birdbro.lumen.system.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.hutool.core.bean.BeanUtil;
import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.system.dto.CreateMenuRequest;
import org.birdbro.lumen.system.dto.UpdateMenuRequest;
import org.birdbro.lumen.system.entity.MenuInfo;
import org.birdbro.lumen.system.mapper.MenuInfoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Menu node lifecycle management (menu-management-api; renamed from the former
 * {@code MenuManageService} 寄住 form by role-list-api per the 2026-11-06 ruling):
 * create / update / delete behind the {@code /api/v1/system/menus} family, beside
 * (never inside) the read-side
 * {@link org.birdbro.lumen.system.service.support.MenuTreeService}. Tree integrity is
 * SERVICE-level - {@code menu_info} has no
 * self-FK, so every structural guard reads one live-node map per request (console-scale
 * data; the same map serves parent validation, the cycle walk and delete's child check
 * with zero extra queries).
 *
 * <p>The {@code role_menu} binding writes this class used to host (the create-time
 * admin auto-grant and the delete-time binding cleanup) now live in their true domain -
 * {@code RoleService#grantNewMenuToAdmin} / {@code RoleService#unbindMenu} (user ruling
 * 2026-11-06: 授权/绑定本质是角色域逻辑; role-list-api design D4). This service calls
 * them inside its own transactions, so the write stays atomic exactly as before.
 * Recorded decisions (menu-management-api design.md D1-D5):</p>
 * <ol>
 *   <li><b>Auto-grant on create</b> - lumen keeps the explicit {@code role_menu} model
 *       (no {@code *:*:*} wildcard), so every new node is granted to the admin role
 *       (role 1) in the same transaction (delegated to the role family). UPDATE never
 *       touches grants.</li>
 *   <li><b>Rejections are {@code A0400}</b> - unknown/non-live parent, button ({@code F})
 *       parent (buttons are leaves), {@code upId} = self or inside the node's own
 *       descendant subtree (cycle), and deleting a node with live children. A type flip
 *       to {@code F} while children are live is rejected as the back-door leaf
 *       violation.</li>
 *   <li><b>Delete = guarded soft delete + binding cleanup</b> - {@code deleteById}
 *       renders {@code UPDATE deleted = 1} via {@code @TableLogic}, and the node's
 *       {@code role_menu} rows are soft-cleaned (delegated to the role family) so a
 *       deleted button stops granting its perms. No cascade: live children block the
 *       delete.</li>
 * </ol>
 *
 * <p>Cycle detection walks UP the {@code upId} chain from the candidate parent: the
 * candidate is inside the node's subtree exactly when the walk reaches the node.</p>
 *
 * @author birdbro
 */
@Service
public class MenuService {

    /** Node type: button - a leaf that must never gain children. */
    private static final String TYPE_BUTTON = "F";

    private final MenuInfoMapper menuInfoMapper;
    private final RoleService roleService;

    public MenuService(MenuInfoMapper menuInfoMapper, RoleService roleService) {
        this.menuInfoMapper = menuInfoMapper;
        this.roleService = roleService;
    }

    /**
     * Creates a menu node from the request ({@code BeanUtil.copyProperties} 1:1; the
     * fields match) and auto-grants it to the admin role - both in one transaction.
     *
     * @param request the create body ({@code upId = 0} = top level)
     * @return the new node id ({@code IdType.AUTO} backfill)
     */
    @Transactional
    public Integer create(CreateMenuRequest request) {
        Map<Integer, MenuInfo> live = liveMenuMap();
        if (request.getUpId() != 0) {
            MenuInfo parent = requireLiveParent(live, request.getUpId());
            rejectButtonParent(parent);
        }
        MenuInfo menu = new MenuInfo();
        BeanUtil.copyProperties(request, menu, true);
        menuInfoMapper.insert(menu);
        roleService.grantNewMenuToAdmin(menu.getId());
        return menu.getId();
    }

    /**
     * Updates a node's full field set, including reparenting via {@code upId}. Guards
     * run before the write: the node must exist live; the new parent must be live and
     * not a button; the move must not target the node itself or its own subtree; a
     * type flip to {@code F} must not orphan children semantics. Grants are untouched.
     *
     * @param id      the node to update
     * @param request the full-field update body
     * @return {@code true} on success
     */
    @Transactional
    public Boolean update(Integer id, UpdateMenuRequest request) {
        Map<Integer, MenuInfo> live = liveMenuMap();
        MenuInfo target = live.get(id);
        if (target == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "菜单不存在或已删除");
        }
        if (request.getUpId() != 0) {
            MenuInfo parent = requireLiveParent(live, request.getUpId());
            rejectButtonParent(parent);
            if (isInsideSubtree(live, request.getUpId(), id)) {
                throw new BusinessException(ExceptionEnum.ERROR_A0400, "不能移动到自身或自己的子孙节点下");
            }
        }
        if (TYPE_BUTTON.equals(request.getType()) && hasLiveChildren(live, id)) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "存在下级节点，不能改为按钮类型");
        }
        BeanUtil.copyProperties(request, target, true);
        menuInfoMapper.updateById(target);
        return true;
    }

    /**
     * Deletes a childless node: soft-deletes the row ({@code @TableLogic}) and
     * soft-cleans its {@code role_menu} bindings in one transaction - the same cascade
     * shape as {@code RoleServiceImpl.delete}. Live children block the delete.
     *
     * @param id the node to delete
     * @return {@code true} on success
     */
    @Transactional
    public Boolean delete(Integer id) {
        Map<Integer, MenuInfo> live = liveMenuMap();
        if (!live.containsKey(id)) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "菜单不存在或已删除");
        }
        if (hasLiveChildren(live, id)) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "存在下级节点，请先处理下级再删除");
        }
        menuInfoMapper.deleteById(id);
        roleService.unbindMenu(id);
        return true;
    }

    // ===== guards (shared single-map reads) =====

    /**
     * Every live menu row keyed by id ({@code deleted = 0} applied by the
     * {@code @TableLogic} on the shared {@code EntityBase}).
     */
    private Map<Integer, MenuInfo> liveMenuMap() {
        List<MenuInfo> menus = menuInfoMapper.selectList(null);
        Map<Integer, MenuInfo> byId = new HashMap<>(menus.size());
        for (MenuInfo menu : menus) {
            byId.put(menu.getId(), menu);
        }
        return byId;
    }

    /** A non-zero upId must reference a live node. */
    private MenuInfo requireLiveParent(Map<Integer, MenuInfo> live, Integer upId) {
        MenuInfo parent = live.get(upId);
        if (parent == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "上级菜单不存在或已删除");
        }
        return parent;
    }

    /** Buttons are leaves: a child under an {@code F} parent is always rejected. */
    private void rejectButtonParent(MenuInfo parent) {
        if (TYPE_BUTTON.equals(parent.getType())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "按钮节点下不能创建子节点");
        }
    }

    /**
     * Whether {@code candidate} sits inside the subtree of {@code rootId} (root
     * inclusive - callers reject {@code upId = id} here too, keeping one walk for
     * both cycle shapes).
     */
    private boolean isInsideSubtree(Map<Integer, MenuInfo> live, Integer candidate, Integer rootId) {
        Integer current = candidate;
        while (current != null && current != 0) {
            if (current.equals(rootId)) {
                return true;
            }
            MenuInfo node = live.get(current);
            current = node == null ? null : node.getUpId();
        }
        return false;
    }

    /** Whether any live node hangs directly under {@code id}. */
    private boolean hasLiveChildren(Map<Integer, MenuInfo> live, Integer id) {
        for (MenuInfo menu : live.values()) {
            if (id.equals(menu.getUpId())) {
                return true;
            }
        }
        return false;
    }
}
