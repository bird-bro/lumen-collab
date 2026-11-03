package org.birdbro.framework.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

/**
 * Exempts the service-declared anonymous paths ({@code security.permit-all}, see
 * {@link SecurityAnonymousProperties}) from the security chain - lumen adaptation (6),
 * user ruling 2026-11-03 (framework/common may evolve on the OPS lineage). The minimal
 * lumen equivalent of the copied {@code SecurityConfig} anonymous list: the OPS
 * hardcoded {@code permitAll} entries stay verbatim, each service ADDS the paths it
 * owns. Every path NOT listed stays token-guarded by the copied chain (rejected by the
 * copied {@code AuthenticationEntryPointImpl} with the "203" envelope when tokenless).
 *
 * <p>{@code web.ignoring()} short-circuits the security chain for the listed URLs only -
 * declare ONLY paths that need no authentication at all (e.g. the JWT issuer's login
 * endpoint; no token exists at login time anyway).</p>
 *
 * @author birdbro
 */
@Configuration
@EnableConfigurationProperties(SecurityAnonymousProperties.class)
public class AnonymousPathCustomizer {

    private final SecurityAnonymousProperties properties;

    public AnonymousPathCustomizer(SecurityAnonymousProperties properties) {
        this.properties = properties;
    }

    /** No-op when the service declares no anonymous paths. */
    @Bean
    public WebSecurityCustomizer anonymousPathWebSecurityCustomizer() {
        return (WebSecurity web) -> {
            if (!properties.getPermitAll().isEmpty()) {
                web.ignoring()
                        .antMatchers(properties.getPermitAll().toArray(new String[0]));
            }
        };
    }
}
