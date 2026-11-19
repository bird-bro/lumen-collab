package org.birdbro.lumen.core;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * TEST-SOURCES-ONLY placeholder {@link UserDetailsService} satisfying the copied
 * framework {@code SecurityConfig} constructor injection inside the kernel's test
 * context (lumen-core-registration-kernel task 2.1 - the PatientUserDetailsService
 * precedent). REAL services each provide their own adapter in main sources
 * (lumen-system's UserDetailsServiceImpl is the platform's credential source);
 * lumen-core MAIN sources deliberately carry NO security beans - a shared library
 * never owns identity. This bean exists only so the test host boots; it always
 * throws, exactly like the patient placeholder.
 *
 * @author birdbro
 */
@Service
public class TestUserDetailsService implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        throw new UsernameNotFoundException(
                "lumen-core issues no logins and owns no identity - this test-only "
                + "UserDetailsService satisfies the framework SecurityConfig constructor "
                + "in the test host, never a credential source: " + username);
    }
}
