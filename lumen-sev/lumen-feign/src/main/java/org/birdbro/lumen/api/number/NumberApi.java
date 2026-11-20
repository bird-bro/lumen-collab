package org.birdbro.lumen.api.number;

import org.birdbro.common.core.R;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * The platform number-rule Feign contract (number-rule-unification-sev task 1.1,
 * design D5; the FIRST inter-service contract - lumen-api materializes with it): the
 * machine issuance endpoint of the system-owned number-rule service. Consumers
 * (lumen-patient today; the department services adapt the kernel's NumberIssuerPort
 * through this) enable it with `@EnableFeignClients` and configure the DIRECT target
 * URL per environment (`lumen.api.system.url` - no discovery, the 2026-11-03
 * local-config ruling).
 *
 * <p>Fail-fast contract: the fallback THROWS (C0001) — a fallback that fabricates a
 * number is FORBIDDEN, numbers are never reused (the platform burns the serial on any
 * caller-side rollback, so a retry after failure must re-issue, never reuse).</p>
 *
 * <p>The endpoint carries the internal-service key (`X-Internal-Key`) — the consumer's
 * Feign RequestInterceptor adds it from the same gitignored config the system side
 * checks (design D4 of number-rule-unification-sev).</p>
 *
 * @author birdbro
 */
@FeignClient(name = "lumen-system-number", url = "${lumen.api.system.url:}",
        fallback = NumberApiFallback.class)
public interface NumberApi {

    /**
     * Issues one number from the given rule family (the platform burns one serial).
     *
     * @param request the rule family
     * @return the envelope carrying the issued number in {@code data.number}
     */
    @PostMapping("/api/v1/system/number-rules/issue")
    R<NumberIssueResponse> issue(@RequestBody NumberIssueRequest request);
}
