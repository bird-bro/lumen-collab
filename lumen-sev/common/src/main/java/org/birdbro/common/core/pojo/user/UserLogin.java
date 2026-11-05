package org.birdbro.common.core.pojo.user;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 登录用户身份权限
 *
 * @author birdbro
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class UserLogin implements UserDetails {

    /**
     * 用户唯一标识
     */
    private String token;

    /**
     * 用户ID
     */
    private Integer userId;


    /**
     * 登录时间
     */
    private Long loginTime;

    /**
     * 过期时间
     */
    private Long expireTime;


    /**
     * 权限列表
     */
    private Set<String> permissions;


    /**
     * 登录信息
     */
    private AgentInfo agent;

    /**
     * 用户信息
     */
    private UserSign user;

    /**
     * 用户AI显示与否
     */
    private Integer aiShow;

    /**
     * 筛查复核的分数阈值
     */
    private Integer recheckScore;

    /**
     * 报告是否支持上云上江西系统  0-不支持  1-支持
     */
    private Integer sendCloudSys;


    public UserLogin() {
    }

    public UserLogin(Integer userId, UserSign user) {
        this.userId = userId;
        this.user = user;
    }

    public UserLogin(Integer userId, UserSign user, Set<String> permissions) {
        this.userId = userId;
        this.user = user;
        this.permissions = permissions;
    }


    @JSONField(serialize = false)
    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getAccount();
    }

    /**
     * 账户是否未过期,过期无法验证
     */
    @JSONField(serialize = false)
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 指定用户是否解锁,锁定的用户无法进行身份验证
     *
     * @return
     */
    @JSONField(serialize = false)
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 指示是否已过期的用户的凭据(密码),过期的凭据防止认证
     *
     * @return
     */
    @JSONField(serialize = false)
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 是否可用 ,禁用的用户不能身份验证
     *
     * @return
     */
    @JSONField(serialize = false)
    @Override
    public boolean isEnabled() {
        return true;
    }


    /**
     * 会话权限集 → Spring Security authorities（server-side-perm-enforcement D1）。
     * 空集/缺失一律返回空列表而非 null——过滤器把本值直接放进
     * {@code UsernamePasswordAuthenticationToken}，null 会在 Spring 内部引发 NPE；
     * 缺 permissions 字段的旧会话因此自然降级为"无任何权限"（默认拒绝）。
     *
     * @author birdbro
     */
    @JSONField(serialize = false)
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (permissions == null || permissions.isEmpty()) {
            return Collections.emptyList();
        }
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }


}
