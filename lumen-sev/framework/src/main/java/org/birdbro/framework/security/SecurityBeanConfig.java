package org.birdbro.framework.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * The shared stateless {@link BCryptPasswordEncoder} for every lumen service (lumen
 * adaptation (6), user ruling 2026-11-03) - one bean instead of per-service {@code new}
 * fields. Note: the copied {@code SecurityConfig} pins its OWN {@code NoOpPasswordEncoder}
 * on the {@code AuthenticationManagerBuilder} (OPS MD5 lineage, manual compare in the
 * {@code UserDetailsService}) - this bean is independent of that wiring and is consumed
 * by-type by services that hash passwords (lumen-system login + user management).
 *
 * @author birdbro
 */
@Configuration
public class SecurityBeanConfig {

    /** Stateless, thread-safe: one instance serves the whole application. */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
