package org.birdbro.lumen.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * lumen-auth service shell (init-backend-skeleton task 3.1).
 *
 * <p>{@code scanBasePackages} covers the whole {@code org.birdbro} root so the copied
 * {@code framework}/{@code common} beans register - the OPS {@code LaunchApplication}
 * habit. {@code @MapperScan} is that same habit (OPS LaunchApplication:
 * {@code @MapperScan(basePackages = "org.birdbro.**.mapper")}): the copied framework
 * log services ({@code LogInfoServiceImpl} etc.) autowire their MyBatis-Plus mappers,
 * which no component scan can satisfy. Added during Batch 4's red-green cycle - the
 * context-load test failed on {@code logInfoServiceImpl} without it. Discovery
 * registration is automatic on this train (no {@code @EnableDiscoveryClient} needed).
 * Deliberately nothing else: no controllers, no security wiring - token issuance
 * is this service's job but its wiring completes in the auth feature change (design D5,
 * adaptation 2).</p>
 *
 * @author birdbro
 */
@SpringBootApplication(scanBasePackages = "org.birdbro")
@MapperScan(basePackages = "org.birdbro.**.mapper")
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
