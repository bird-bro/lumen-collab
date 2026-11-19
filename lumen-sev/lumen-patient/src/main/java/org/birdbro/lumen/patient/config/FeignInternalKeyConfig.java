package org.birdbro.lumen.patient.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;

import cn.hutool.core.util.StrUtil;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Feign internal-key carrier (number-rule-unification-sev task 3.1, design D4):
 * every outgoing Feign call from this service carries the {@code X-Internal-Key}
 * header the system side checks - the SAME gitignored-config value
 * ({@code lumen.internal-api-key}, env-var overridable), never committed. Patient has
 * exactly ONE Feign client today (NumberApi), so a context-wide interceptor is
 * precise enough; a second client with different auth would scope this per-client.
 *
 * @author birdbro
 */
@Configuration
public class FeignInternalKeyConfig {

    @Bean
    public RequestInterceptor internalKeyRequestInterceptor(
            @Value("${lumen.internal-api-key:}") String internalKey) {
        return (RequestTemplate template) -> {
            if (StrUtil.isNotBlank(internalKey)) {
                template.header("X-Internal-Key", internalKey);
            }
        };
    }
}
