package org.birdbro.framework.config;

import lombok.RequiredArgsConstructor;
import org.birdbro.framework.security.filter.JwtAuthenticationTokenFilter;
import org.birdbro.framework.security.handle.AuthenticationEntryPointImpl;
import org.birdbro.framework.security.handle.LogoutSuccessHandlerImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.web.filter.CorsFilter;

/**
 * spring security配置
 *
 * @author birdbro
 */
@Configuration
@RequiredArgsConstructor
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final UserDetailsService userDetailsService;


    /**
     * 认证失败处理类
     */

    private final AuthenticationEntryPointImpl unauthorizedHandler;

    /**
     * 退出处理类
     */

    private final LogoutSuccessHandlerImpl logoutSuccessHandler;

    /**
     * token认证过滤器
     */

    private final JwtAuthenticationTokenFilter tokenFilter;

    /**
     * 跨域过滤器
     */

    private final CorsFilter corsFilter;

    /**
     * 解决 无法直接注入 AuthenticationManager
     *
     * @return
     * @throws Exception
     */
    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    /**
     * anyRequest          |   匹配所有请求路径
     * access              |   SpringEl表达式结果为true时可以访问
     * anonymous           |   匿名可以访问
     * denyAll             |   用户不能访问
     * fullyAuthenticated  |   用户完全认证可以访问（非remember-me下自动登录）
     * hasAnyAuthority     |   如果有参数，参数表示权限，则其中任何一个权限可以访问
     * hasAnyRole          |   如果有参数，参数表示角色，则其中任何一个角色可以访问
     * hasAuthority        |   如果有参数，参数表示权限，则其权限可以访问
     * hasIpAddress        |   如果有参数，参数表示IP地址，如果用户IP和参数匹配，则可以访问
     * hasRole             |   如果有参数，参数表示角色，则其角色可以访问
     * permitAll           |   用户可以任意访问
     * rememberMe          |   允许通过remember-me登录的用户访问
     * authenticated       |   用户登录后可访问
     */
    @Override
    protected void configure(HttpSecurity httpSecurity) throws Exception {

        httpSecurity
                // CSRF禁用，因为不使用session
                .csrf().disable()
                // 禁用HTTP响应标头
                .headers().cacheControl().disable().and()
                // 认证失败处理类
                .exceptionHandling().authenticationEntryPoint(unauthorizedHandler).and()
                // 基于token，所以不需要session
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
                // 过滤请求
                .authorizeRequests()
                // 允许匿名访问
                .antMatchers("/api/sign/in").permitAll()
                .antMatchers("/api/sign/keyPair").permitAll()
                .antMatchers("/api/wechat/sign/**").permitAll()
                .antMatchers("/api/viewer/key/**").permitAll()
                .antMatchers("/api/dicom/**").permitAll()
                // 第三方开放接口：不走 JWT，由 AkSkAuthFilter 验签（见 birdbro-ice AkSkFilterConfig）
                .antMatchers("/api/open/**").permitAll()
                // 静态资源，可匿名访问
                .antMatchers(HttpMethod.GET, "/", "/*.html", "/**/*.html", "/**/*.css", "/**/*.js", "/profile/**").permitAll()
                .antMatchers("/swagger-ui/", "/swagger-resources/**", "/webjars/**", "/*/api-docs", "/druid/**").permitAll()
                // 除上面外的所有请求全部需要鉴权认证
                .anyRequest().authenticated()
                .and()
                .headers().frameOptions().disable();

        // 添加一个过滤器验证其他请求的Token是否合法
        httpSecurity.addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class);
        // 添加登出过滤器
        httpSecurity.logout().logoutUrl("/api/sign/out").logoutSuccessHandler(logoutSuccessHandler);
        // 添加CORS filter 允许跨域请求
        httpSecurity.addFilterBefore(corsFilter, JwtAuthenticationTokenFilter.class);
        httpSecurity.addFilterBefore(corsFilter, LogoutFilter.class);
        httpSecurity.headers().cacheControl().disable();

    }


    /**
     * 身份认证接口
     */
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        // 密码 = 库中裸 MD5(32位),校验在 UserDetailsServiceImpl 手动 equals 完成。
        // encoder 必须显式 NoOp(明文比对):缺省 DelegatingPasswordEncoder 要求密码带 {xxx} 前缀,
        // 对裸 MD5 会抛 "There is no PasswordEncoder mapped for the id \"null\""。
        // 零引用≠死代码:encoder 由 Spring Security 按类型消费,勿删(c214bd1 曾误删,2026-10-21 登录全断)。
        auth.userDetailsService(userDetailsService)
                .passwordEncoder(NoOpPasswordEncoder.getInstance());
    }
}
