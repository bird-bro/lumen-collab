package org.birdbro.lumen.setting;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * lumen-setting service shell (exam-setting-api task 1.1): the 检查设置 domain -
 * hospital-wide exam catalog (部位分组/检查部位/检查方法/检查项目) + report-template
 * knowledge base, in the {@code setting} schema under {@code /api/v1/setting/**}.
 *
 * <p>{@code scanBasePackages} covers the whole {@code org.birdbro} root so the copied
 * {@code framework}/{@code common} beans register - the OPS {@code LaunchApplication}
 * habit, identical to {@code SystemApplication}. {@code @MapperScan} follows the same
 * OPS habit (the framework log services autowire MyBatis-Plus mappers no component
 * scan can satisfy). No registry/config center (user decision 2026-11-03, local
 * configuration). No anonymous paths - every endpoint stays token-guarded (the JWT
 * issuer lives in lumen-system; this service only verifies).</p>
 *
 * @author birdbro
 */
@SpringBootApplication(scanBasePackages = "org.birdbro")
@MapperScan(basePackages = "org.birdbro.**.mapper")
public class SettingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettingApplication.class, args);
    }
}
