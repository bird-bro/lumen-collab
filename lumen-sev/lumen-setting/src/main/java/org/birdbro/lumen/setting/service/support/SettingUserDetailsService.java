package org.birdbro.lumen.setting.service.support;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * The service-owned {@link UserDetailsService} the copied framework
 * {@code SecurityConfig} constructor-injects (exam-setting-api task 1.2 - surfaced by
 * the schema-seed RED run: without a bean of this type the copied chain fails the whole
 * context, exactly as the auth-rbac-foundation Batch 1/2 stub documented for
 * lumen-system).
 *
 * <p>The setting service has NO account store and NO login endpoint: tokens are ISSUED
 * by lumen-system, land in the shared Redis, and the copied
 * {@code JwtAuthenticationTokenFilter} rebuilds the {@code UserLogin} principal from
 * that Redis session - {@code loadUserByUsername} is never on that path (the copied
 * {@code SecurityConfig} pairs this loader with {@code NoOpPasswordEncoder} inside its
 * {@code AuthenticationManagerBuilder}, a path the verify-only service never
 * invokes). The implementation therefore refuses every lookup explicitly instead of
 * pretending to own accounts: a future authentication flow that reaches it must fail
 * loudly, not silently authenticate against an empty store. Lives in
 * {@code service/support/} per the service-package-layout ruling (a stateless
 * collaborator with no HTTP surface of its own).</p>
 *
 * @author birdbro
 */
@Service
public class SettingUserDetailsService implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        throw new UsernameNotFoundException("lumen-setting owns no accounts; tokens are "
                + "issued by lumen-system (lookup was: " + username + ")");
    }
}
