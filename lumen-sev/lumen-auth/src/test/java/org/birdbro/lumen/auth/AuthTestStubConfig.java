package org.birdbro.lumen.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Test-only wiring stub for the Batch 4 context-load test (init-backend-skeleton task 4.1).
 *
 * <p>The copied framework {@code SecurityConfig} (framework/config/SecurityConfig.java)
 * constructor-injects a {@link UserDetailsService}. In OPS every business service supplies
 * its own implementation (e.g. birdbro-manage's {@code UserDetailsServiceImpl}); in lumen
 * that implementation is deliberately deferred to the auth feature change (design D5,
 * adaptation 2), so this change ships none and the context would fail with
 * {@code required a bean of type 'org.springframework.security.core.userdetails.UserDetailsService'}.
 * That failure was surfaced by the red run of the context-load test.</p>
 *
 * <p>This stub satisfies ONLY that constructor injection so the context can load with zero
 * infrastructure. It lives in {@code src/test/java} (never on the production classpath),
 * is imported explicitly by {@link AuthApplicationTests} via {@code @Import}, and its
 * {@code loadUserByUsername} is never invoked by a context-load test - it throws loudly if
 * anything ever does. No framework/common class is modified (verbatim guarantee intact) and
 * no production security wiring is completed early.</p>
 *
 * @author birdbro
 */
@TestConfiguration
public class AuthTestStubConfig {

    @Bean
    public UserDetailsService testUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("stub - never invoked by the context-load test");
        };
    }
}
