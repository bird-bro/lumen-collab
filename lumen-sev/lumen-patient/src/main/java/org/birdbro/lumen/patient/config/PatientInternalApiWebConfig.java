package org.birdbro.lumen.patient.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the patient-side {@link InternalKeyInterceptor} on the internal service
 * surface (us-station-registration-sev task 2.2, design D4) - a PATIENT-DOMAIN
 * configurer, NOT an edit of the copied framework WebMvcConfig (the additive-first
 * adaptation rule). Scoped to exactly {@code /api/v1/patient/internal/**}: the
 * user-facing archive/resolve/merge surface stays behind the JWT chain, the internal
 * resolve-or-register + outbox pull/ack family behind the internal key.
 *
 * <p>NOT behind user perms, ever: the internal family sits on the service's
 * {@code security.permit-all} list (application.yml) precisely so a tokenless service
 * call reaches THIS guard - which is fail-closed on an unconfigured key.</p>
 *
 * @author birdbro
 */
@Configuration
public class PatientInternalApiWebConfig implements WebMvcConfigurer {

    private final String internalKey;

    public PatientInternalApiWebConfig(
            @Value("${lumen.internal-api-key:}") String internalKey) {
        this.internalKey = internalKey;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalKeyInterceptor(internalKey))
                .addPathPatterns("/api/v1/patient/internal/**");
    }
}
