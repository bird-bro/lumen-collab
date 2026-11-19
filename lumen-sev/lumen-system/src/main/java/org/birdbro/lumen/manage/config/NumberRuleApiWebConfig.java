package org.birdbro.lumen.manage.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link InternalKeyInterceptor} on the machine issuance endpoint
 * (number-rule-unification-sev task 2.2, design D4) - a MANAGE-DOMAIN configurer, NOT
 * an edit of the copied framework WebMvcConfig (the additive-first adaptation rule).
 * Scoped to exactly {@code /api/v1/system/number-rules/issue}: the rule CRUD stays
 * behind user perms, the machine endpoint behind the internal key.
 *
 * @author birdbro
 */
@Configuration
public class NumberRuleApiWebConfig implements WebMvcConfigurer {

    private final String internalKey;

    public NumberRuleApiWebConfig(
            @Value("${lumen.internal-api-key:}") String internalKey) {
        this.internalKey = internalKey;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalKeyInterceptor(internalKey))
                .addPathPatterns("/api/v1/system/number-rules/issue");
    }
}
