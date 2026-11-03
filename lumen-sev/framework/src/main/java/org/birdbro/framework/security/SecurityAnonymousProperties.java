package org.birdbro.framework.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Service-declared anonymous (permit-all) paths (lumen adaptation (6), user ruling
 * 2026-11-03: framework/common may evolve on the OPS lineage). The copied
 * {@code SecurityConfig} keeps the OPS hardcoded {@code permitAll} list verbatim; each
 * lumen service declares the anonymous paths it OWNS through this property instead of
 * a per-service security config class - e.g. lumen-system:
 *
 * <pre>
 * security:
 *   permit-all:
 *     - /api/v1/system/login
 * </pre>
 *
 * <p>Bound by {@link AnonymousPathCustomizer} via {@code @EnableConfigurationProperties}.
 * Additive-only evolution: no line of the copied SecurityConfig is modified, so a future
 * OPS upstream sync cannot conflict.</p>
 *
 * @author birdbro
 */
@Data
@ConfigurationProperties(prefix = "security")
public class SecurityAnonymousProperties {

    /** Anonymous paths the service owns (ant patterns); empty = nothing extra. */
    private List<String> permitAll = new ArrayList<>();
}
