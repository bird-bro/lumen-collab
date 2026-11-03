package org.birdbro.lumen.auth.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.auth.dto.CreateUserRequest;
import org.birdbro.lumen.auth.dto.UserItemDto;
import org.birdbro.lumen.auth.entity.RoleInfo;
import org.birdbro.lumen.auth.entity.UserInfo;
import org.birdbro.lumen.auth.entity.UserRole;
import org.birdbro.lumen.auth.mapper.RoleInfoMapper;
import org.birdbro.lumen.auth.mapper.UserInfoMapper;
import org.birdbro.lumen.auth.mapper.UserRoleMapper;
import org.birdbro.lumen.auth.service.UserInfoService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User management implementation (auth-rbac-foundation task 4.3, extending the Batch 3
 * minimal create). The duplicate-username rejection is SERVICE-level ({@code A0111},
 * the Batch-3 review handoff): the RED run of the 4.3 test showed the pre-check gap -
 * without it the DB partial unique index surfaced as a generic {@code "500"} envelope.
 * The index stays as the backstop for concurrent creates.
 *
 * <p>The optional {@code roleIds} of the create request (task 4.4's recorded user-role
 * creation path) are validated against live roles ({@code A0400} on a miss) and bound
 * as {@code user_role} rows in the SAME transaction as the user insert.</p>
 *
 * <p>The audit identity ({@code create_by}/{@code update_by}) is NOT set here on
 * purpose: the copied {@code MetaObjectConfig} insert/update fill owns it, sourced from
 * the USER-HEADER request header that the framework {@code UserHeaderBridgeFilter}
 * derives (lumen adaptation (6))
 * from the JWT principal. The {@link BCryptPasswordEncoder} is the SHARED lumen bean
 * (Batch-3 review polish - one encoder instead of per-service {@code new} fields).</p>
 *
 * @author birdbro
 */
@Service
public class UserInfoServiceImpl implements UserInfoService {

    private final UserInfoMapper userInfoMapper;
    private final RoleInfoMapper roleInfoMapper;
    private final UserRoleMapper userRoleMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserInfoServiceImpl(UserInfoMapper userInfoMapper, RoleInfoMapper roleInfoMapper,
            UserRoleMapper userRoleMapper, BCryptPasswordEncoder passwordEncoder) {
        this.userInfoMapper = userInfoMapper;
        this.roleInfoMapper = roleInfoMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public Integer createUser(CreateUserRequest request) {
        rejectDuplicateUsername(request.getUsername());

        // Dedupe first: a repeated id must not inflate the expected count, and binding
        // the same role twice would fight the user_role partial unique index.
        Set<Integer> roleIds = request.getRoleIds() == null
                ? java.util.Collections.emptySet()
                : new LinkedHashSet<>(request.getRoleIds());
        if (!roleIds.isEmpty()) {
            long liveRoles = roleInfoMapper.selectCount(
                    new LambdaQueryWrapper<RoleInfo>().in(RoleInfo::getId, roleIds));
            if (liveRoles != roleIds.size()) {
                throw new BusinessException(ExceptionEnum.ERROR_A0400,
                        "some role ids do not exist: " + roleIds);
            }
        }

        UserInfo user = new UserInfo();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());
        // must_change_password defaults 0 and enable defaults 1 (V1 column defaults).
        userInfoMapper.insert(user);

        for (Integer roleId : roleIds) {
            UserRole binding = new UserRole();
            binding.setUserId(user.getId());
            binding.setRoleId(roleId);
            userRoleMapper.insert(binding);
        }
        return user.getId();
    }

    @Override
    public List<UserItemDto> listUsers() {
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<UserInfo>()
                .orderByAsc(UserInfo::getId); // @TableLogic appends deleted = 0
        List<UserItemDto> items = new ArrayList<>();
        for (UserInfo user : userInfoMapper.selectList(wrapper)) {
            items.add(toItem(user));
        }
        return items;
    }

    @Override
    public Boolean resetPassword(Integer userId, String newPassword) {
        UserInfo user = requireLiveUser(userId);

        UserInfo patch = new UserInfo();
        patch.setId(user.getId());
        patch.setPassword(passwordEncoder.encode(newPassword));
        patch.setMustChangePassword(1); // the user must change it at the next login
        userInfoMapper.updateById(patch);
        return Boolean.TRUE;
    }

    @Override
    public Boolean setEnable(Integer userId, Integer enable) {
        UserInfo user = requireLiveUser(userId);

        UserInfo patch = new UserInfo();
        patch.setId(user.getId());
        patch.setEnable(enable);
        userInfoMapper.updateById(patch);
        return Boolean.TRUE;
    }

    /** The A0111 service check: the username must not exist among LIVE rows (@TableLogic). */
    private void rejectDuplicateUsername(String username) {
        Long existing = userInfoMapper.selectCount(
                new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUsername, username));
        if (existing != null && existing > 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0111);
        }
    }

    /** The update target must be live (@TableLogic); otherwise the update is B0031. */
    private UserInfo requireLiveUser(Integer userId) {
        UserInfo user = userInfoMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ExceptionEnum.ERROR_B0031,
                    "user id " + userId + " does not exist");
        }
        return user;
    }

    /** Explicit field mapping - the entity's password hash never reaches the DTO. */
    private static UserItemDto toItem(UserInfo user) {
        UserItemDto item = new UserItemDto();
        item.setUid(user.getId());
        item.setUsername(user.getUsername());
        item.setName(user.getName());
        item.setEnable(user.getEnable());
        item.setMustChangePassword(user.getMustChangePassword());
        item.setCreateTime(user.getCreateTime());
        return item;
    }
}
