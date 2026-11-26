package org.birdbro.lumen.manage.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link InternalKeyInterceptor} on the INTERNAL exam-room reads
 * (us-order-exam-room-sev task 2.2) - a MANAGE-DOMAIN configurer, NOT an edit of the
 * copied framework WebMvcConfig nor of {@code NumberRuleApiWebConfig} (the additive-first
 * adaptation rule; the number-rules family configurer stays byte-identical). Scoped to
 * exactly the two internal rooms paths: the console room registry stays behind user
 * perms, the machine reads behind the internal key.
 *
 * @author birdbro
 */
@Configuration
public class InternalRoomApiWebConfig implements WebMvcConfigurer {

    private final String internalKey;

    public InternalRoomApiWebConfig(
            @Value("${lumen.internal-api-key:}") String internalKey) {
        this.internalKey = internalKey;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalKeyInterceptor(internalKey))
                .addPathPatterns("/api/v1/system/internal/org-rooms/list",
                        "/api/v1/system/internal/org-rooms/*");
    }
}
