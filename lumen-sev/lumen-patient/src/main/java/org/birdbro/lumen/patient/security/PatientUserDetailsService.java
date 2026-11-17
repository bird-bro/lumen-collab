package org.birdbro.lumen.patient.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Placeholder {@link UserDetailsService} satisfying the copied framework
 * {@code SecurityConfig} constructor injection (framework/config/SecurityConfig
 * constructor-injects a {@code UserDetailsService}, and {@code
 * scanBasePackages="org.birdbro"} pulls that config into every service).
 *
 * <p>The JWT request path NEVER loads a {@code UserDetails}: {@code
 * JwtAuthenticationTokenFilter} builds the {@code Authentication} directly from the
 * token claims, so {@link #loadUserByUsername(String)} is unreachable in normal
 * operation - it always throws {@link UsernameNotFoundException}. This bean exists ONLY
 * to satisfy the framework constructor until a future login-bearing change replaces it.
 * Patient has no login: every path stays token-guarded, and lumen-system remains the
 * sole JWT issuer of the platform.</p>
 *
 * @author birdbro
 */
@Service
public class PatientUserDetailsService implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        throw new UsernameNotFoundException(
                "lumen-patient issues no logins - this UserDetailsService is a framework "
                + "SecurityConfig constructor placeholder, never a credential source: "
                + username);
    }
}
