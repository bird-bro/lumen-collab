package org.birdbro.lumen.patient;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * lumen-patient service shell (patient-domain task 1.1, design D6: module shape mirrors
 * lumen-system conventions).
 *
 * <p>{@code scanBasePackages} covers the whole {@code org.birdbro} root so the copied
 * {@code framework}/{@code common} beans register - the OPS {@code LaunchApplication}
 * habit. {@code @MapperScan} is that same habit (OPS LaunchApplication:
 * {@code @MapperScan(basePackages = "org.birdbro.**.mapper")}): the copied framework
 * log services ({@code LogInfoServiceImpl} etc.) autowire their MyBatis-Plus mappers,
 * which no component scan can satisfy.</p>
 *
 * <p>Single-domain service (naming.md): the app class lives at the SERVICE root package
 * {@code org.birdbro.lumen.patient} - the root package mirrors the SERVICE identity
 * (module name, {@code spring.application.name}, nginx {@code /api/v1/patient/**}
 * prefix). No registry/config center exists (user decision 2026-11-03, design D8: local
 * configuration, 随访 fu-launch shape) - nothing to enable; discovery registration is
 * automatic on this train (no {@code @EnableDiscoveryClient} needed).</p>
 *
 * <p>Batch 1 ships the scaffold only: no controllers, no mappers, no entities yet -
 * tasks 2.x/3.x/4.x build the EMPI domain on this shell. The copied framework
 * {@code SecurityConfig} constructor-injects a {@code UserDetailsService}; that slot
 * is satisfied by {@link org.birdbro.lumen.patient.security.PatientUserDetailsService}
 * (patient has no login - the JWT issuer stays lumen-system).</p>
 *
 * @author birdbro
 */
@SpringBootApplication(scanBasePackages = "org.birdbro")
@MapperScan(basePackages = "org.birdbro.**.mapper")
public class PatientApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatientApplication.class, args);
    }
}
