package org.birdbro.lumen.us;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * lumen-us service shell (us-station-registration-sev task 1.1, design D1: the FIRST
 * department-service host of the archived exam-workflow kernel registration domain -
 * module shape mirrors the lumen-patient conventions).
 *
 * <p>{@code scanBasePackages} covers the whole {@code org.birdbro} root so the copied
 * {@code framework}/{@code common} beans register - the OPS {@code LaunchApplication}
 * habit. {@code @MapperScan} is that same habit (OPS LaunchApplication:
 * {@code @MapperScan(basePackages = "org.birdbro.**.mapper")}): the copied framework
 * log services ({@code LogInfoServiceImpl} etc.) autowire their MyBatis-Plus mappers,
 * which no component scan can satisfy. The wildcard ALSO covers the kernel mappers
 * ({@code org.birdbro.lumen.core.mapper}) for the day lumen-core lands on this
 * classpath (task 3.x) - they resolve against the {@code us} schema then.</p>
 *
 * <p>Single-domain service (naming.md): the app class lives at the SERVICE root package
 * {@code org.birdbro.lumen.us} - the root package mirrors the SERVICE identity (module
 * name, {@code spring.application.name}, nginx {@code /api/v1/us/**} prefix). No
 * registry/config center exists (user decision 2026-11-03: local configuration, 随访
 * fu-launch shape) - nothing to enable; discovery registration is automatic on this
 * train (no {@code @EnableDiscoveryClient} needed). No {@code @EnableFeignClients}
 * yet either: the PatientApi/NumberApi clients ride tasks 2.x/3.x (lumen-api is not on
 * this classpath at batch 1).</p>
 *
 * <p>Batch 1 ships the scaffold + the Flyway ledger only: no controllers, no entities
 * yet - the kernel registration surface builds on this shell in later batches. The
 * copied framework {@code SecurityConfig} constructor-injects a {@code
 * UserDetailsService}; that slot is satisfied by {@link
 * org.birdbro.lumen.us.security.UsUserDetailsService} (the US workstation has no login
 * - the JWT issuer stays lumen-system).</p>
 *
 * @author birdbro
 */
@SpringBootApplication(scanBasePackages = "org.birdbro")
@MapperScan(basePackages = "org.birdbro.**.mapper")
public class UsApplication {

    public static void main(String[] args) {
        SpringApplication.run(UsApplication.class, args);
    }
}
