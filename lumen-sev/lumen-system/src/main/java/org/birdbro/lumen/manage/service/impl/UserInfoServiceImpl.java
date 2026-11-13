package org.birdbro.lumen.manage.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.birdbro.common.core.pojo.user.UserHeader;
import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.common.utils.SecurityUtils;
import org.birdbro.lumen.manage.dto.CreateUserRequest;
import org.birdbro.lumen.manage.dto.UpdateUserRequest;
import org.birdbro.lumen.manage.dto.UserDetailDto;
import org.birdbro.lumen.manage.dto.UserItemDto;
import org.birdbro.lumen.manage.entity.OrgDep;
import org.birdbro.lumen.manage.entity.RoleInfo;
import org.birdbro.lumen.manage.entity.UserInfo;
import org.birdbro.lumen.manage.entity.UserRole;
import org.birdbro.lumen.manage.mapper.OrgDepMapper;
import org.birdbro.lumen.manage.mapper.RoleInfoMapper;
import org.birdbro.lumen.manage.mapper.UserInfoMapper;
import org.birdbro.lumen.manage.mapper.UserRoleMapper;
import org.birdbro.lumen.manage.service.UserInfoService;
import org.birdbro.lumen.manage.service.support.LoginLockoutService;
import org.birdbro.lumen.manage.service.support.PasswordCipherHelper;
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * Additive DI (never-modify rule; documented precedent {@code LogController}): the
     * existing constructor stays byte-identical, the password-cipher helper joins as a
     * field (password-transport-encryption-api task 2.4) - create and admin-reset decrypt
     * their SM2 ciphertext fields before the untouched BCrypt encode.
     */
    @Autowired
    private PasswordCipherHelper passwordCipherHelper;

    /**
     * Additive DI (never-modify rule; documented precedent {@code LogController}): the
     * lockout-state clearer joins as a field (login-lockout task 2.3) - the admin reset
     * doubles as the emergency unlock channel.
     */
    @Autowired
    private LoginLockoutService loginLockoutService;

    /**
     * Additive DI (never-modify rule; documented precedent {@code LogController}): the
     * department mapper joins as a field (user-edit-delete-profile-api task 2.1) - the
     * batch depName resolution plus the update path's service-level department check.
     */
    @Autowired
    private OrgDepMapper orgDepMapper;

    @Override
    @Transactional
    public Integer createUser(CreateUserRequest request) {
        rejectDuplicateUsername(request.getUsername());

        // Referential integrity is SERVICE-level (user-edit-delete-profile-api): the
        // V26 profile fields may carry a depId - a dead department rejects A0400 and
        // NO row is created (the same rule the update path enforces).
        if (request.getDepId() != null && orgDepMapper.selectById(request.getDepId()) == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "department id " + request.getDepId() + " does not exist");
        }

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
        BeanUtil.copyProperties(request, user, true);
        // The initial password arrives as SM2 ciphertext (password-transport-encryption-api
        // task 2.4) - decrypt to the raw value BEFORE the untouched BCrypt encode. A format
        // failure is A0400 from the helper, before any row is written.
        user.setPassword(passwordEncoder.encode(passwordCipherHelper.decrypt(request.getPassword()))); // hash replaces the decrypted raw text
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

        UserInfo patch = UserInfo.builder()
                .id(user.getId())
                // The replacement arrives as SM2 ciphertext (password-transport-encryption-api
                // task 2.4) - decrypt BEFORE the untouched BCrypt encode.
                .password(passwordEncoder.encode(passwordCipherHelper.decrypt(newPassword)))
                .mustChangePassword(1) // the user must change it at the next login
                .build();
        userInfoMapper.updateById(patch);
        // Emergency unlock channel (login-lockout task 2.3, design D6): the admin reset
        // clears the target account's failure counter and lock key - zero new endpoints,
        // the reset flow doubles as the unlock (auto-expiry remains the normal path).
        loginLockoutService.clear(user.getUsername());
        return Boolean.TRUE;
    }

    @Override
    public Boolean setEnable(Integer userId, Integer enable) {
        UserInfo user = requireLiveUser(userId);

        UserInfo patch = UserInfo.builder()
                .id(user.getId())
                .enable(enable)
                .build();
        userInfoMapper.updateById(patch);
        return Boolean.TRUE;
    }

    // ===================== user-edit-delete-profile-api (tasks 2.1-3.3) =====================
    // NEW methods on the same impl - the existing four above stay byte-identical (the
    // never-modify rule). The profile surface reads/writes the V26 columns and
    // batch-resolves the role/department display names; the delete is the protected
    // logical delete (self + seed-admin).

    @Override
    public List<UserItemDto> listUsersWithProfile() {
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<UserInfo>()
                .orderByAsc(UserInfo::getId); // @TableLogic appends deleted = 0
        List<UserInfo> users = userInfoMapper.selectList(wrapper);
        Map<Integer, List<Integer>> roleIdsByUser = loadRoleIdsByUser(
                users.stream().map(UserInfo::getId).collect(Collectors.toList()));

        // ONE batch fetch of every referenced role, ONE of every referenced department
        // - the whole page resolves in a constant number of queries, never N+1 per row.
        Set<Integer> allRoleIds = roleIdsByUser.values().stream()
                .flatMap(List::stream).collect(Collectors.toSet());
        Map<Integer, String> roleNames = allRoleIds.isEmpty()
                ? Collections.emptyMap()
                : roleInfoMapper.selectBatchIds(allRoleIds).stream()
                        .collect(Collectors.toMap(RoleInfo::getId, RoleInfo::getName));

        Set<Integer> depIds = users.stream().map(UserInfo::getDepId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, String> depNames = depIds.isEmpty()
                ? Collections.emptyMap()
                : orgDepMapper.selectBatchIds(depIds).stream()
                        .collect(Collectors.toMap(OrgDep::getId, OrgDep::getName));

        List<UserItemDto> items = new ArrayList<>();
        for (UserInfo user : users) {
            UserItemDto item = toItem(user);
            List<Integer> ids = roleIdsByUser.getOrDefault(user.getId(), Collections.emptyList());
            item.setRoleNames(ids.stream()
                    .map(roleNames::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
            item.setDepName(user.getDepId() == null ? null : depNames.get(user.getDepId()));
            items.add(item);
        }
        return items;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetailDto getUserDetail(Integer userId) {
        UserInfo user = requireLiveUser(userId);
        UserDetailDto detail = new UserDetailDto();
        BeanUtil.copyProperties(user, detail, true);
        detail.setUid(user.getId());
        detail.setRoleIds(loadRoleIdsByUser(Collections.singletonList(user.getId()))
                .getOrDefault(user.getId(), Collections.emptyList()));
        return detail;
    }

    @Override
    @Transactional
    public Boolean updateUser(Integer userId, UpdateUserRequest request) {
        requireLiveUser(userId);

        // Referential integrity is SERVICE-level (the V1 binding design: user_info
        // stays at zero physical FKs) - a dead reference rejects A0400 and the
        // transaction rolls back with NO column applied.
        if (request.getDepId() != null && orgDepMapper.selectById(request.getDepId()) == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "department id " + request.getDepId() + " does not exist");
        }
        validateRoleIds(request.getRoleIds());

        // The patch touches ONLY the mutable profile columns - username/password/enable/
        // mustChangePassword are not fields of UpdateUserRequest, so a posted
        // "username" is absent from the patch by construction (the spec: ignored, not
        // applied). The explicit builder (not copyProperties) keeps that guarantee
        // visible: every written column appears literally below.
        UserInfo patch = UserInfo.builder()
                .id(userId)
                .name(request.getName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .gender(request.getGender())
                .title(request.getTitle())
                .depId(request.getDepId())
                .modalitys(request.getModalitys())
                .repAudit(request.getRepAudit())
                .signature(request.getSignature())
                .build();
        userInfoMapper.updateById(patch);

        // A present roleIds REPLACES the bindings (full-replace, the role-menu
        // assignment precedent); an absent one leaves them untouched.
        if (request.getRoleIds() != null) {
            rebindRoles(userId, request.getRoleIds());
        }
        return Boolean.TRUE;
    }

    @Override
    @Transactional
    public Boolean deleteUser(Integer userId) {
        // Protection FIRST, before any write (user ruling 2026-11-10: the 双保护
        // matrix). The caller identity is the same bridged USER-HEADER the copied
        // MetaObjectConfig consumes; null (an unbridged context) fails CLOSED on the
        // self check only - the seed-admin check needs no identity.
        UserHeader caller = SecurityUtils.getUserHeader();
        if (caller != null && userId.equals(caller.getUid())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "the current account cannot delete itself");
        }
        if (userId != null && userId == SEED_ADMIN_ID) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "the seeded administrator (id 1) cannot be deleted - the bootstrap "
                            + "login must survive");
        }
        UserInfo user = requireLiveUser(userId);

        // Logical delete of the row + logical unbind of its live bindings, one
        // transaction; the freed username is re-creatable (the partial unique index
        // covers live rows only).
        userInfoMapper.deleteById(userId);
        List<UserRole> liveBindings = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        for (UserRole binding : liveBindings) {
            userRoleMapper.deleteById(binding.getId());
        }
        return Boolean.TRUE;
    }

    /** The seed administrator's id (the V2 explicit-id seed makes it a contract). */
    private static final int SEED_ADMIN_ID = 1;

    /** The A0111 service check: the username must not exist among LIVE rows (@TableLogic). */
    private void rejectDuplicateUsername(String username) {
        Long existing = userInfoMapper.selectCount(
                new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUsername, username));
        if (existing != null && existing > 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0111);
        }
    }

    /**
     * The profile surface's role-id loader: ONE query for the users' live bindings.
     * Absent users simply have no entry (getOrDefault covers them).
     */
    private Map<Integer, List<Integer>> loadRoleIdsByUser(List<Integer> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UserRole> bindings = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().in(UserRole::getUserId, userIds));
        return bindings.stream()
                .collect(Collectors.groupingBy(UserRole::getUserId,
                        Collectors.mapping(UserRole::getRoleId, Collectors.toList())));
    }

    /**
     * Every posted role id must reference a live role (A0400 on a miss) - the create
     * path's rule (never-modify: its inline check stays there), re-homed here for the
     * update path.
     */
    private void validateRoleIds(List<Integer> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        Set<Integer> ids = new LinkedHashSet<>(roleIds);
        long liveRoles = roleInfoMapper.selectCount(
                new LambdaQueryWrapper<RoleInfo>().in(RoleInfo::getId, ids));
        if (liveRoles != ids.size()) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "some role ids do not exist: " + ids);
        }
    }

    /**
     * The full-replace writer: the LIVE bindings go first (logical delete), the posted
     * set inserts - both inside the caller's transaction (the profile update or the
     * delete cascade).
     */
    private void rebindRoles(Integer userId, List<Integer> roleIds) {
        List<UserRole> liveBindings = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        for (UserRole binding : liveBindings) {
            userRoleMapper.deleteById(binding.getId());
        }
        for (Integer roleId : new LinkedHashSet<>(roleIds)) {
            UserRole binding = new UserRole();
            binding.setUserId(userId);
            binding.setRoleId(roleId);
            userRoleMapper.insert(binding);
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

    /**
     * Same-name fields flow via copyProperties; {@code uid} is the renamed {@code id}.
     * The entity's password hash cannot reach the DTO - {@code UserItemDto} carries no
     * password property for the copy to bind.
     */
    private static UserItemDto toItem(UserInfo user) {
        UserItemDto item = new UserItemDto();
        BeanUtil.copyProperties(user, item, true);
        item.setUid(user.getId());
        return item;
    }
}
