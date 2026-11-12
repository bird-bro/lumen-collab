package org.birdbro.lumen.system.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.birdbro.common.Constants.Constants;
import org.birdbro.common.core.pojo.user.MenuTree;
import org.birdbro.common.core.pojo.user.UserLogin;
import org.birdbro.common.core.pojo.user.UserSign;
import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.common.utils.SecurityUtils;
import org.birdbro.framework.manager.AsyncManager;
import org.birdbro.framework.manager.factory.AsyncFactory;
import org.birdbro.framework.security.TokenService;
import org.birdbro.lumen.system.dto.LoginResponse;
import org.birdbro.lumen.system.dto.MeResponse;
import org.birdbro.lumen.system.dto.ProfileDto;
import org.birdbro.lumen.system.entity.UserInfo;
import org.birdbro.lumen.system.mapper.MenuInfoMapper;
import org.birdbro.lumen.system.mapper.RoleInfoMapper;
import org.birdbro.lumen.system.mapper.RoleMenuMapper;
import org.birdbro.lumen.system.mapper.UserInfoMapper;
import org.birdbro.lumen.system.service.AuthService;
import org.birdbro.lumen.system.service.support.MenuTreeService;
import org.birdbro.lumen.system.service.support.LoginLockoutService;
import org.birdbro.lumen.system.service.support.PasswordCipherHelper;
import org.springframework.beans.factory.annotation.Autowired;
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
 * <p>Sign events (system-data task 3.4): BOTH outcomes append a {@code public.log_sign}
 * row through {@code AsyncFactory.recordForSign} - success with the session identity,
 * failure recorded then RETHROWN so the envelope behavior is unchanged - symmetric to
 * the copied {@code LogoutSuccessHandlerImpl}'s wired sign-out event.</p>
 *
 * <p>The {@link UserLogin} stored in Redis carries identity only: {@link UserSign} is
 * built WITHOUT the password hash, so no secret ever reaches Redis or the responses
 * (Batch-2 review handoff). Profile/menu/perms data is always re-read from the database -
 * the Redis session copy is identity, not truth. The {@link BCryptPasswordEncoder} is
 * the SHARED lumen bean (Batch-3 review polish, applied here in task 4.3).</p>
 *
 * @author birdbro
 */
@Service
public class AuthServiceImpl implements AuthService {


    private final UserInfoMapper userInfoMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuInfoMapper menuInfoMapper;
    private final RoleInfoMapper roleInfoMapper;
    private final MenuTreeService menuTreeService;
    private final TokenService tokenService;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthServiceImpl(UserInfoMapper userInfoMapper, RoleMenuMapper roleMenuMapper,
            MenuInfoMapper menuInfoMapper, RoleInfoMapper roleInfoMapper,
            MenuTreeService menuTreeService, TokenService tokenService,
            BCryptPasswordEncoder passwordEncoder) {
        this.userInfoMapper = userInfoMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.menuInfoMapper = menuInfoMapper;
        this.roleInfoMapper = roleInfoMapper;
        this.menuTreeService = menuTreeService;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Additive DI (never-modify rule; documented precedent {@code LogController}): the
     * existing constructor stays byte-identical, the password-cipher helper joins as a
     * field (password-transport-encryption-api task 2.4) - its {@code decrypt} turns the
     * request's SM2 ciphertext into the raw value the existing BCrypt line consumes.
     */
    @Autowired
    private PasswordCipherHelper passwordCipherHelper;

    /**
     * Additive DI (never-modify rule; documented precedent {@code LogController}): the
     * per-account brute-force lockout gate joins as a field (login-lockout task 2.2) —
     * the copied A0211 error code is thrown HERE, the Redis key model lives in the
     * support service.
     */
    @Autowired
    private LoginLockoutService loginLockoutService;

    @Override
    public LoginResponse login(String username, String rawPassword) {
        // Transport decryption FIRST (password-transport-encryption-api task 2.4,
        // coordination D4/D5): the request's password field arrives as SM2 ciphertext;
        // a format failure throws A0400 HERE - before the account query, so plaintext
        // probing cannot enumerate accounts and wrong-password counters stay clean.
        // {@code rawPassword} now names the CIPHERTEXT (the parameter signature is
        // unchanged; javadoc carries the semantic). Additive field DI below
        // (never-modify rule); the BCrypt match line is untouched except for feeding it
        // the DECRYPTED value.
        String raw = passwordCipherHelper.decrypt(rawPassword);
        // Resolved BEFORE the try: the failure branches know the account row (when it
        // exists) for the sign-event uid. TableLogic appends deleted = 0, so a logically
        // deleted account is "unknown".
        UserInfo user = userInfoMapper.selectOne(
                new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUsername, username));
        try {
            if (user == null) {
                throw new BusinessException(ExceptionEnum.ERROR_A0201);
            }
            if (!Integer.valueOf(1).equals(user.getEnable())) {
                throw new BusinessException(ExceptionEnum.ERROR_A0202);
            }
            // Login lockout gate (login-lockout task 2.2, design D4): AFTER the account
            // lookup (an unknown username can never be locked) and BEFORE the BCrypt
            // round (a locked account costs one Redis GET, not one slow hash). The
            // copied A0211 constant is REUSED verbatim - a locked account answers A0211
            // regardless of the submitted password until the lock expires.
            if (loginLockoutService.isLocked(username)) {
                throw new BusinessException(ExceptionEnum.ERROR_A0211);
            }
            if (!passwordEncoder.matches(raw, user.getPassword())) {
                // Only the wrong-password path counts toward lockout (user ruling):
                // record BEFORE the throw; the threshold attempt itself still answers
                // A0210 - the lock starts biting from the NEXT attempt (spec).
                loginLockoutService.recordFailure(username);
                throw new BusinessException(ExceptionEnum.ERROR_A0210);
            }
            // Success clears the counter (spec: earlier failures never accumulate).
            loginLockoutService.clear(username);

            UserSign sign = new UserSign();
            sign.setUid(user.getId());
            sign.setAccount(user.getUsername());
            sign.setName(user.getName());
            sign.setEnable(user.getEnable());
            UserLogin loginUser = new UserLogin(user.getId(), sign, permissionSet(user.getId()));
            // createToken RETURNS the signed JWT; the UUID it stashes on loginUser is the
            // Redis session key - only the returned string may leave as data.token.
            String jwt = tokenService.createToken(loginUser);

            // Sign-event wiring (system-data task 3.4): the success twin of the copied
            // LogoutSuccessHandlerImpl's recordForSign call. Event constant: the copied
            // Constants carry no SIGN_IN - the generic SUCCESS(10)/FAIL(11) pair is the
            // recorded selection (SIGN_OUT=12 stays the logout event); the UserLogin arg
            // supplies uid + the session token anchor (UUID, not the JWT).
            AsyncManager.me().execute(AsyncFactory.recordForSign(
                    user.getUsername(),
                    Constants.SUCCESS,
                    "登录",
                    "登录成功，用户" + user.getUsername(),
                    loginUser));

            LoginResponse response = new LoginResponse();
            response.setToken(jwt);
            response.setProfile(toProfile(user));
            response.setMenus(menuTree(user.getId()));
            response.setPerms(permissionSet(user.getId()));
            return response;
        } catch (BusinessException e) {
            // Sign-event wiring (system-data task 3.4): record then RETHROW - the A-class
            // envelope behavior is unchanged.
            recordFailedSign(username, user, e);
            throw e;
        }
    }

    /**
     * Records the failed sign-in (the {@code Constants.FAIL} event) and rethrows nothing -
     * the caller rethrows the ORIGINAL exception so the envelope stays byte-identical.
     * The login loop has no UserLogin session yet: a minimal attempt identity (uid only)
     * is built when the account row exists, so the failure row still carries the uid;
     * the UNKNOWN-account branch passes NO UserLogin at all - {@code recordForSign}
     * tolerates the absent varargs ({@code new UserLogin()} - uid/token land null).
     */
    private void recordFailedSign(String username, UserInfo user, BusinessException e) {
        String descr = "登录失败：" + e.getMessage();
        if (user == null) {
            AsyncManager.me().execute(AsyncFactory.recordForSign(
                    username, Constants.FAIL, "登录", descr));
            return;
        }
        UserLogin attempt = new UserLogin();
        attempt.setUserId(user.getId());
        AsyncManager.me().execute(AsyncFactory.recordForSign(
                username, Constants.FAIL, "登录", descr, attempt));
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
        BeanUtil.copyProperties(user, profile, true);
        profile.setUid(user.getId()); // renamed: entity id -> profile uid
        profile.setRoles(roleInfoMapper.selectRoleNamesByUserId(user.getId()));
        return profile;
    }
}
