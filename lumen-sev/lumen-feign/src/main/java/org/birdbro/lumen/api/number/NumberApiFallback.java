package org.birdbro.lumen.api.number;

import org.birdbro.common.core.R;
import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;

import org.springframework.stereotype.Component;

/**
 * The FAIL-FAST fallback of the platform number contract (number-rule-unification-sev
 * task 1.1, design D5): a Sentinel fallback that fabricates a number is FORBIDDEN —
 * numbers are never reused and a client-side substitute would break identity, so the
 * fallback simply THROWS the C-class service-unreachable business exception and the
 * caller's operation refuses (the kernel's NumberIssuerPort contract mirrors this:
 * issuer failure refuses the registration).
 *
 * @author birdbro
 */
@Component
public class NumberApiFallback implements NumberApi {

    @Override
    public R<NumberIssueResponse> issue(NumberIssueRequest request) {
        throw new BusinessException(ExceptionEnum.ERROR_C0001,
                "号码规则服务不可达，发号失败（fail-fast，绝不造号）: "
                + (request != null ? request.getRuleType() : "?"));
    }
}
