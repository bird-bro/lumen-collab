package org.birdbro.lumen.system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * lumen-system service shell (init-backend-skeleton task 3.1).
 *
 * <p>{@code scanBasePackages} covers the whole {@code org.birdbro} root so the copied
 * {@code framework}/{@code common} beans register - the OPS {@code LaunchApplication}
 * habit. {@code @MapperScan} is that same habit (OPS LaunchApplication:
 * {@code @MapperScan(basePackages = "org.birdbro.**.mapper")}): the copied framework
 * log services ({@code LogInfoServiceImpl} etc.) autowire their MyBatis-Plus mappers,
 * which no component scan can satisfy. Added during Batch 4's red-green cycle - the
 * context-load test failed on {@code logInfoServiceImpl} without it. Discovery
 * registration is automatic on this train (no {@code @EnableDiscoveryClient} needed).
 * <p>No registry/config center exists (user decision 2026-11-03, design D8: local
 * configuration, 随访 fu-launch shape) - nothing to enable.</p>
 *
 * <p>Deliberately nothing else: no controllers, no security wiring - token issuance
 * is this service's job - its wiring shipped with auth-rbac-foundation Batch 3: the
 * login loop (AuthController/AuthService + the copied TokenService), the service-owned
 * UserDetailsService the copied SecurityConfig constructor-injects, the framework anonymous-path customizer (security.permit-all config), and
 * the framework UserHeaderBridgeFilter for MetaObjectConfig audit identity.</p>
 *
 * @author birdbro
 */
@SpringBootApplication(scanBasePackages = "org.birdbro")
@MapperScan(basePackages = "org.birdbro.**.mapper")
public class SystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(SystemApplication.class, args);
    }
}
