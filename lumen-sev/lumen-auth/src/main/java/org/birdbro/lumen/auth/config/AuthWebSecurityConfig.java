package org.birdbro.lumen.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

/**
 * lumen-side anonymous-access rule for the login path (auth-rbac-foundation task 3.2,
 * design D6 "apply 时核对" resolution). The copied framework {@code SecurityConfig} keeps
 * its hardcoded OPS {@code permitAll} list (sign/wechat/viewer/dicom/open paths) - it is
 * verbatim machinery and CANNOT be extended without editing it, so {@code /api/v1/auth/
 * login} is exempted through Spring Security's {@link WebSecurityCustomizer} instead:
 * the minimal lumen-owned equivalent of the copied mechanism's anonymous list.
 *
 * <p>{@code web.ignoring()} short-circuits the copied security chain for the login URL
 * only (no token exists at login time anyway); every other request - including
 * {@code GET /api/v1/auth/me} - stays filter-guarded by the copied chain and is rejected
 * with the copied entry point's "203" envelope when tokenless (proven by
 * {@code AuthLoginTests#meWithoutTokenRejectedWith203Envelope}).</p>
 *
 * @author birdbro
 */
@Configuration
public class AuthWebSecurityConfig {

    /** Exempts only the login path from the security chain (see class javadoc). */
    @Bean
    public WebSecurityCustomizer authWebSecurityCustomizer() {
        return (WebSecurity web) -> web.ignoring().antMatchers("/api/v1/auth/login");
    }
}
