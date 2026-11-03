package org.birdbro.lumen.system.service.impl;

import java.util.HashSet;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.birdbro.common.core.pojo.user.UserLogin;
import org.birdbro.common.core.pojo.user.UserSign;
import org.birdbro.lumen.system.entity.UserInfo;
import org.birdbro.lumen.system.mapper.RoleMenuMapper;
import org.birdbro.lumen.system.mapper.UserInfoMapper;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * The service-owned {@link UserDetailsService} the copied framework {@code SecurityConfig}
 * constructor-injects (auth-rbac-foundation task 3.2; replaces the Batch 1/2
 * {@code AuthTestStubConfig} deferral documented in that stub's javadoc).
 *
 * <p>OPS semantics (birdbro-manage's {@code UserDetailsServiceImpl} lineage): load the
 * account by username among live rows ({@code deleted = 0} via the copied EntityBase
 * {@code @TableLogic}), map it onto the copied {@link UserLogin} identity
 * ({@code userId}, {@code UserSign{uid, account, name, enable}}, permission set) WITHOUT
 * the password hash. The copied {@code SecurityConfig} pairs this loader with
 * {@code NoOpPasswordEncoder} inside its {@code AuthenticationManagerBuilder} - that
 * path stays untouched and UNUSED: lumen's login service verifies the BCrypt hash itself
 * (task 3.1), and this loader exists to satisfy the copied chain's injection contract
 * and any future Spring Security authentication flows.</p>
 *
 * @author birdbro
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserInfoMapper userInfoMapper;
    private final RoleMenuMapper roleMenuMapper;

    public UserDetailsServiceImpl(UserInfoMapper userInfoMapper, RoleMenuMapper roleMenuMapper) {
        this.userInfoMapper = userInfoMapper;
        this.roleMenuMapper = roleMenuMapper;
    }

    @Override
    public UserLogin loadUserByUsername(String username) throws UsernameNotFoundException {
        UserInfo user = userInfoMapper.selectOne(
                new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("account not found: " + username);
        }

        UserSign sign = new UserSign();
        sign.setUid(user.getId());
        sign.setAccount(user.getUsername());
        sign.setName(user.getName());
        sign.setEnable(user.getEnable());
        Set<String> perms = new HashSet<>(roleMenuMapper.selectPermsByUserId(user.getId()));
        return new UserLogin(user.getId(), sign, perms);
    }
}
