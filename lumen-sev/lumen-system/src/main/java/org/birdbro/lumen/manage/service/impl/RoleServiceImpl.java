package org.birdbro.lumen.manage.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.manage.dto.AssignRoleMenusRequest;
import org.birdbro.lumen.manage.dto.CreateRoleRequest;
import org.birdbro.lumen.manage.dto.RoleItemDto;
import org.birdbro.lumen.manage.dto.RoleListQuery;
import org.birdbro.lumen.manage.dto.UpdateRoleRequest;
import org.birdbro.lumen.manage.entity.RoleInfo;
import org.birdbro.lumen.manage.entity.RoleMenu;
import org.birdbro.lumen.manage.entity.UserRole;
import org.birdbro.lumen.manage.mapper.MenuInfoMapper;
import org.birdbro.lumen.manage.mapper.RoleInfoMapper;
import org.birdbro.lumen.manage.mapper.RoleMenuMapper;
import org.birdbro.lumen.manage.mapper.UserRoleMapper;
import org.birdbro.lumen.manage.service.RoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RoleInfo management implementation (auth-rbac-foundation task 4.4).
 *
 * <p>Recorded decisions. (1) FULL-REPLACE assignment: {@code assignMenus} deletes the
 * role's live {@code role_menu} rows (logical, via {@code @TableLogic}) then inserts
 * the requested set - an empty list legitimately clears all grants; the partial unique
 * index {@code uk_role_menu_role_menu} never fights the dead rows. (2) The logical role
 * delete CASCADES to the role's {@code user_role}/{@code role_menu} rows in one
 * transaction: the Batch 3 perms/menu walks do not join {@code role} (they go
 * {@code user_role -> role_menu -> menu_info}), so live bindings to a deleted role
 * would KEEP granting - "leave bindings inert" is not achievable by leaving them. The
 * cascade keeps the walks truthful. (3) Error-code mapping: missing referenced role or
 * menu ids are request-parameter errors ({@code A0400}), a duplicate live name is
 * {@code A0714} 数据重复, an unknown update target is {@code B0031}.</p>
 *
 * <p>The audit identity is NOT set here on purpose: the copied {@code MetaObjectConfig}
 * fill owns it (bridged from the JWT principal by the framework bridge filter, adaptation (6)).</p>
 *
 * @author birdbro
 */
@Service
public class RoleServiceImpl implements RoleService {

    /** The admin role (role 1) every new menu node is auto-granted to (V5 seed discipline). */
    private static final int ADMIN_ROLE_ID = 1;

    private final RoleInfoMapper roleInfoMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final UserRoleMapper userRoleMapper;
    private final MenuInfoMapper menuInfoMapper;

    public RoleServiceImpl(RoleInfoMapper roleInfoMapper, RoleMenuMapper roleMenuMapper,
            UserRoleMapper userRoleMapper, MenuInfoMapper menuInfoMapper) {
        this.roleInfoMapper = roleInfoMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.userRoleMapper = userRoleMapper;
        this.menuInfoMapper = menuInfoMapper;
    }

    @Override
    public Integer create(CreateRoleRequest request) {
        rejectDuplicateName(request.getName(), null);

        RoleInfo role = new RoleInfo();
        // a null type falls to the V1 column default 11 (NOT-NULL insert strategy)
        BeanUtil.copyProperties(request, role, true);
        roleInfoMapper.insert(role);
        return role.getId();
    }

    @Override
    public Boolean update(Integer id, UpdateRoleRequest request) {
        if (roleInfoMapper.selectById(id) == null) {
            throw new BusinessException(ExceptionEnum.ERROR_B0031, "role id " + id + " does not exist");
        }
        if (request.getName() != null) {
            rejectDuplicateName(request.getName(), id);
        }

        RoleInfo patch = new RoleInfo();
        BeanUtil.copyProperties(request, patch, true);
        patch.setId(id); // path param, not a request field
        roleInfoMapper.updateById(patch);
        return Boolean.TRUE;
    }

    @Override
    @Transactional
    public Boolean delete(Integer id) {
        // Cascade (recorded decision): dead role, dead bindings - the perms/menu walks
        // never join role, so live bindings would keep granting past the delete.
        // @TableLogic turns every delete into UPDATE deleted = 1.
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, id));
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, id));
        roleInfoMapper.deleteById(id);
        return Boolean.TRUE;
    }

    @Override
    public Boolean setEnable(Integer roleId, Integer enable) {
        if (roleInfoMapper.selectById(roleId) == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "role does not exist: role id " + roleId);
        }

        // A focused patch: only the enable column changes (the OrgServiceImpl.setStatus
        // mirror; the strict 0/1 body check lives on SetRoleEnableRequest's validation).
        RoleInfo patch = new RoleInfo();
        patch.setId(roleId);
        patch.setEnable(enable);
        roleInfoMapper.updateById(patch);
        return Boolean.TRUE;
    }

    @Override
    @Transactional
    public Boolean assignMenus(Integer roleId, AssignRoleMenusRequest request) {
        if (roleInfoMapper.selectById(roleId) == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "role does not exist: role id " + roleId);
        }
        Set<Integer> menuIds = new LinkedHashSet<>(request.getMenuIds()); // dedupe, keep order
        if (!menuIds.isEmpty() && menuInfoMapper.selectBatchIds(menuIds).size() != menuIds.size()) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "some menu ids do not exist: " + menuIds);
        }

        // Full replace: drop the role's current grants, then insert the requested set.
        // @TableLogic renders the delete as UPDATE deleted = 1, so the partial unique
        // index never fights the dead rows.
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleId));
        for (Integer menuId : menuIds) {
            RoleMenu binding = new RoleMenu();
            binding.setRoleId(roleId);
            binding.setMenuId(menuId);
            roleMenuMapper.insert(binding);
        }
        return Boolean.TRUE;
    }

    /**
     * Paged list (role-list-api): the 随访 {@code getPage} semantics - LIKE name, EQ
     * type, id-ascending - plus one batched {@code role_menu} echo query for the whole
     * page (live rows via {@code @TableLogic}), menu ids sorted ascending.
     */
    @Override
    public IPage<RoleItemDto> page(RoleListQuery query) {
        LambdaQueryWrapper<RoleInfo> wrapper = new LambdaQueryWrapper<RoleInfo>()
                .like(StrUtil.isNotBlank(query.getName()), RoleInfo::getName, query.getName())
                .eq(query.getType() != null, RoleInfo::getType, query.getType())
                .orderByAsc(RoleInfo::getId);
        IPage<RoleInfo> rows = roleInfoMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);

        Map<Integer, List<Integer>> grants = new HashMap<>();
        List<Integer> roleIds = new ArrayList<>();
        for (RoleInfo role : rows.getRecords()) {
            roleIds.add(role.getId());
        }
        if (!roleIds.isEmpty()) {
            for (RoleMenu binding : roleMenuMapper.selectList(
                    new LambdaQueryWrapper<RoleMenu>().in(RoleMenu::getRoleId, roleIds))) {
                grants.computeIfAbsent(binding.getRoleId(), key -> new ArrayList<>())
                        .add(binding.getMenuId());
            }
        }
        for (List<Integer> menuIds : grants.values()) {
            Collections.sort(menuIds);
        }

        Page<RoleItemDto> result = new Page<>(rows.getCurrent(), rows.getSize(), rows.getTotal());
        List<RoleItemDto> items = new ArrayList<>(rows.getRecords().size());
        for (RoleInfo role : rows.getRecords()) {
            items.add(toDto(role, grants));
        }
        result.setRecords(items);
        return result;
    }

    /** 1:1 field copy (the BeanUtil house idiom) plus the explicitly-set grant echo. */
    private RoleItemDto toDto(RoleInfo role, Map<Integer, List<Integer>> grants) {
        RoleItemDto dto = new RoleItemDto();
        BeanUtil.copyProperties(role, dto, true);
        dto.setMenuIds(grants.getOrDefault(role.getId(), new ArrayList<>()));
        return dto;
    }

    /**
     * The menu-create auto-grant, verbatim from the former {@code MenuManageService}
     * (role-list-api D4): a NOT-EXISTS-guarded insert into role 1's grant.
     */
    @Override
    public void grantNewMenuToAdmin(Integer menuId) {
        Long existing = roleMenuMapper.selectCount(new LambdaQueryWrapper<RoleMenu>()
                .eq(RoleMenu::getRoleId, ADMIN_ROLE_ID)
                .eq(RoleMenu::getMenuId, menuId));
        if (existing == 0) {
            roleMenuMapper.insert(RoleMenu.builder()
                    .roleId(ADMIN_ROLE_ID)
                    .menuId(menuId)
                    .build());
        }
    }

    /**
     * The menu-delete binding cleanup, verbatim from the former
     * {@code MenuManageService}: {@code @TableLogic} renders the delete as
     * {@code UPDATE deleted = 1}.
     */
    @Override
    public void unbindMenu(Integer menuId) {
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getMenuId, menuId));
    }

    /** The role name is unique among live rows; {@code excludeId} spares the updated role itself. */
    private void rejectDuplicateName(String name, Integer excludeId) {
        LambdaQueryWrapper<RoleInfo> wrapper = new LambdaQueryWrapper<RoleInfo>().eq(RoleInfo::getName, name);
        if (excludeId != null) {
            wrapper.ne(RoleInfo::getId, excludeId);
        }
        if (roleInfoMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0714, "role name already exists: " + name);
        }
    }
}
