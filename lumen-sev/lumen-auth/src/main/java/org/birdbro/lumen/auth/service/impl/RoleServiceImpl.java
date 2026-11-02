package org.birdbro.lumen.auth.service.impl;

import java.util.LinkedHashSet;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.auth.dto.AssignRoleMenusRequest;
import org.birdbro.lumen.auth.dto.CreateRoleRequest;
import org.birdbro.lumen.auth.dto.UpdateRoleRequest;
import org.birdbro.lumen.auth.entity.Role;
import org.birdbro.lumen.auth.entity.RoleMenu;
import org.birdbro.lumen.auth.entity.UserRole;
import org.birdbro.lumen.auth.mapper.MenuInfoMapper;
import org.birdbro.lumen.auth.mapper.RoleMapper;
import org.birdbro.lumen.auth.mapper.RoleMenuMapper;
import org.birdbro.lumen.auth.mapper.UserRoleMapper;
import org.birdbro.lumen.auth.service.RoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Role management implementation (auth-rbac-foundation task 4.4).
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
 * fill owns it (bridged from the JWT principal by the lumen filter).</p>
 *
 * @author birdbro
 */
@Service
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final UserRoleMapper userRoleMapper;
    private final MenuInfoMapper menuInfoMapper;

    public RoleServiceImpl(RoleMapper roleMapper, RoleMenuMapper roleMenuMapper,
            UserRoleMapper userRoleMapper, MenuInfoMapper menuInfoMapper) {
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.userRoleMapper = userRoleMapper;
        this.menuInfoMapper = menuInfoMapper;
    }

    @Override
    public Integer create(CreateRoleRequest request) {
        rejectDuplicateName(request.getName(), null);

        Role role = new Role();
        role.setName(request.getName());
        role.setType(request.getType()); // null -> V1 column default 11 (NOT-NULL insert strategy)
        role.setRemark(request.getRemark());
        roleMapper.insert(role);
        return role.getId();
    }

    @Override
    public Boolean update(Integer id, UpdateRoleRequest request) {
        if (roleMapper.selectById(id) == null) {
            throw new BusinessException(ExceptionEnum.ERROR_B0031, "role id " + id + " does not exist");
        }
        if (request.getName() != null) {
            rejectDuplicateName(request.getName(), id);
        }

        Role patch = new Role();
        patch.setId(id);
        patch.setName(request.getName());
        patch.setType(request.getType());
        patch.setRemark(request.getRemark());
        roleMapper.updateById(patch);
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
        roleMapper.deleteById(id);
        return Boolean.TRUE;
    }

    @Override
    @Transactional
    public Boolean assignMenus(Integer roleId, AssignRoleMenusRequest request) {
        if (roleMapper.selectById(roleId) == null) {
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

    /** The role name is unique among live rows; {@code excludeId} spares the updated role itself. */
    private void rejectDuplicateName(String name, Integer excludeId) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<Role>().eq(Role::getName, name);
        if (excludeId != null) {
            wrapper.ne(Role::getId, excludeId);
        }
        if (roleMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0714, "role name already exists: " + name);
        }
    }
}
