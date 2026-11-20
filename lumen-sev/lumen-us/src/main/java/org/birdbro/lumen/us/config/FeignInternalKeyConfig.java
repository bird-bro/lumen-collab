package org.birdbro.lumen.us.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;

import cn.hutool.core.util.StrUtil;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Feign internal-key carrier (us-station-registration-sev task 3.1, design D3 - the
 * patient {@code FeignInternalKeyConfig} precedent COPIED per-service, additive-first;
 * framework promotion is a future proposal): every outgoing Feign call from this service
 * carries the {@code X-Internal-Key} header the counterparty checks (the patient internal
 * endpoints for {@code PatientApi}; the system number-rule endpoint for {@code
 * NumberApi}) - the SAME gitignored-config value ({@code lumen.internal-api-key},
 * env-var overridable), never committed. Both us Feign clients share one auth scheme, so
 * a context-wide interceptor is precise enough; a client with different auth would scope
 * this per-client (the patient javadoc's own carve-out).
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
