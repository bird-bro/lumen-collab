package org.birdbro.lumen.us.adapter;

import org.birdbro.common.core.R;
import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.api.number.NumberApi;
import org.birdbro.lumen.api.number.NumberIssueRequest;
import org.birdbro.lumen.api.number.NumberIssueResponse;
import org.birdbro.lumen.core.number.NumberIssuerPort;
import org.springframework.stereotype.Component;

/**
 * The kernel {@link NumberIssuerPort} adapter (us-station-registration-sev task 3.1,
 * design D3 - MIRRORS the patient {@code PatientNumberServiceImpl} precedent verbatim):
 * delegates to the platform number-rule {@code NumberApi} Feign contract (internal-key
 * guarded - {@code FeignInternalKeyConfig} carries the header; direct url {@code
 * lumen.api.system.url}). The kernel hands the rule family token (the accession family
 * is issued inside {@code ExamOrderRegistrationService.registerOrder} when the caller
 * did not bring one) - this adapter never picks or re-formats a rule.
 *
 * <p>Fail-fast contract (the port's own): a missing envelope/data refuses B0001, and a
 * transport failure propagates UNTOUCHED (the fail-fast fallback throws C0001) - a
 * fallback that fabricates a number is FORBIDDEN, numbers are never reused (the platform
 * burns the serial on any caller-side rollback). The us create path issues the accession
 * INSIDE the kernel's registration transaction through this port - exactly the
 * composition point the kernel contract dictates.</p>
 *
 * @author birdbro
 */
@Component
public class NumberIssuerPortAdapter implements NumberIssuerPort {

    private final NumberApi numberApi;

    public NumberIssuerPortAdapter(NumberApi numberApi) {
        this.numberApi = numberApi;
    }

    @Override
    public String issue(String ruleType) {
        R<NumberIssueResponse> response =
                numberApi.issue(NumberIssueRequest.builder().ruleType(ruleType).build());
        if (response == null || response.getData() == null
                || response.getData().getNumber() == null) {
            throw new BusinessException(ExceptionEnum.ERROR_B0001,
                    "号码平台响应缺失数据: " + ruleType);
        }
        return response.getData().getNumber();
    }
}
