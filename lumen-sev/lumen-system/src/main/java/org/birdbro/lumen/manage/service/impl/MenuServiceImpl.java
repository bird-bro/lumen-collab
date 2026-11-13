package org.birdbro.lumen.manage.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.hutool.core.bean.BeanUtil;
import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.manage.dto.CreateMenuRequest;
import org.birdbro.lumen.manage.dto.UpdateMenuRequest;
import org.birdbro.lumen.manage.entity.MenuInfo;
import org.birdbro.lumen.manage.mapper.MenuInfoMapper;
import org.birdbro.lumen.manage.service.MenuService;
import org.birdbro.lumen.manage.service.RoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link MenuService} implementation - the body is unchanged from the former concrete
 * class (2026-11-13 all-services interface+impl ruling; injection points keep
 * autowiring the interface FQN, so controllers and tests are untouched).
 *
 * @author birdbro
 */
@Service
public class MenuServiceImpl implements MenuService {

    /** Node type: button - a leaf that must never gain children. */
    private static final String TYPE_BUTTON = "F";

    private final MenuInfoMapper menuInfoMapper;
    private final RoleService roleService;

    public MenuServiceImpl(MenuInfoMapper menuInfoMapper, RoleService roleService) {
        this.menuInfoMapper = menuInfoMapper;
        this.roleService = roleService;
    }

    @Override
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

    @Override
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

    @Override
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
