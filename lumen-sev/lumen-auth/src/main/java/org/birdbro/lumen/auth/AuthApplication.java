package org.birdbro.lumen.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * lumen-auth service shell (init-backend-skeleton task 3.1).
 *
 * <p>{@code scanBasePackages} covers the whole {@code org.birdbro} root so the copied
 * {@code framework}/{@code common} beans register - the OPS {@code LaunchApplication}
 * habit. Discovery registration is automatic on this train (no {@code @EnableDiscoveryClient}
 * needed). Deliberately nothing else: no controllers, no security wiring - token issuance
 * is this service's job but its wiring completes in the auth feature change (design D5,
 * adaptation 2).</p>
 *
 * @author birdbro
 */
@SpringBootApplication(scanBasePackages = "org.birdbro")
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
