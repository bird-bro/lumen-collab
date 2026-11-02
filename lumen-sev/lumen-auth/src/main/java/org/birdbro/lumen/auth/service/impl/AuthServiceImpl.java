package org.birdbro.lumen.auth.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.birdbro.common.core.pojo.user.MenuTree;
import org.birdbro.common.core.pojo.user.UserLogin;
import org.birdbro.common.core.pojo.user.UserSign;
import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.common.utils.SecurityUtils;
import org.birdbro.framework.security.TokenService;
import org.birdbro.lumen.auth.dto.LoginResponse;
import org.birdbro.lumen.auth.dto.MeResponse;
import org.birdbro.lumen.auth.dto.ProfileDto;
import org.birdbro.lumen.auth.entity.UserInfo;
import org.birdbro.lumen.auth.mapper.MenuInfoMapper;
import org.birdbro.lumen.auth.mapper.RoleMapper;
import org.birdbro.lumen.auth.mapper.RoleMenuMapper;
import org.birdbro.lumen.auth.mapper.UserInfoMapper;
import org.birdbro.lumen.auth.service.AuthService;
import org.birdbro.lumen.auth.service.MenuTreeService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Login loop implementation (auth-rbac-foundation task 3.1, design D6): BCrypt
 * verification against the {@code user_info} hash, then JWT issuance through the COPIED
 * {@link TokenService} (token state in Redis via {@code login_tokens:<uuid>}). Error
 * codes follow the copied {@code ExceptionEnum}: A0201 unknown account, A0202 disabled
 * account, A0210 wrong password - the copied {@code GlobalExceptionHandler} renders each
 * as the A-class envelope with {@code data} null.
 *
 * <p>The {@link UserLogin} stored in Redis carries identity only: {@link UserSign} is
 * built WITHOUT the password hash, so no secret ever reaches Redis or the responses
 * (Batch-2 review handoff). Profile/menu/perms data is always re-read from the database -
 * the Redis session copy is identity, not truth.</p>
 *
 * @author birdbro
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserInfoMapper userInfoMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuInfoMapper menuInfoMapper;
    private final RoleMapper roleMapper;
    private final MenuTreeService menuTreeService;
    private final TokenService tokenService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthServiceImpl(UserInfoMapper userInfoMapper, RoleMenuMapper roleMenuMapper,
            MenuInfoMapper menuInfoMapper, RoleMapper roleMapper,
            MenuTreeService menuTreeService, TokenService tokenService) {
        this.userInfoMapper = userInfoMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.menuInfoMapper = menuInfoMapper;
        this.roleMapper = roleMapper;
        this.menuTreeService = menuTreeService;
        this.tokenService = tokenService;
    }

    @Override
    public LoginResponse login(String username, String rawPassword) {
        UserInfo user = userInfoMapper.selectOne(
                new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUsername, username));
        if (user == null) {
            // TableLogic appends deleted = 0, so a logically deleted account is "unknown".
            throw new BusinessException(ExceptionEnum.ERROR_A0201);
        }
        if (!Integer.valueOf(1).equals(user.getEnable())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0202);
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0210);
        }

        UserSign sign = new UserSign();
        sign.setUid(user.getId());
        sign.setAccount(user.getUsername());
        sign.setName(user.getName());
        sign.setEnable(user.getEnable());
        UserLogin loginUser = new UserLogin(user.getId(), sign, permissionSet(user.getId()));
        // createToken RETURNS the signed JWT; the UUID it stashes on loginUser is the
        // Redis session key - only the returned string may leave as data.token.
        String jwt = tokenService.createToken(loginUser);

        LoginResponse response = new LoginResponse();
        response.setToken(jwt);
        response.setProfile(toProfile(user));
        response.setMenus(menuTree(user.getId()));
        response.setPerms(permissionSet(user.getId()));
        return response;
    }

    @Override
    public MeResponse me() {
        UserLogin principal = SecurityUtils.getLoginUser(); // set by the copied JWT filter
        UserInfo user = userInfoMapper.selectById(principal.getUserId());
        if (user == null) {
            // The account was removed after the session was issued - the session copy is
            // identity, the database is truth; a removed account no longer has a profile.
            throw new BusinessException(ExceptionEnum.ERROR_A0203);
        }
        MeResponse response = new MeResponse();
        response.setProfile(toProfile(user));
        response.setMenus(menuTree(user.getId()));
        response.setPerms(permissionSet(user.getId()));
        return response;
    }

    @Override
    public Set<String> permissionSet(Integer userId) {
        return new HashSet<>(roleMenuMapper.selectPermsByUserId(userId));
    }

    @Override
    public List<MenuTree> menuTree(Integer userId) {
        return menuTreeService.build(menuInfoMapper.selectMenusByUserId(userId));
    }

    private ProfileDto toProfile(UserInfo user) {
        ProfileDto profile = new ProfileDto();
        profile.setUid(user.getId());
        profile.setUsername(user.getUsername());
        profile.setName(user.getName());
        profile.setEnable(user.getEnable());
        profile.setMustChangePassword(user.getMustChangePassword());
        profile.setRoles(roleMapper.selectRoleNamesByUserId(user.getId()));
        return profile;
    }
}
