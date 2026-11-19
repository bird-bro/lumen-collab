package org.birdbro.lumen.patient.service.impl;

import org.birdbro.common.core.R;
import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.api.number.NumberApi;
import org.birdbro.lumen.api.number.NumberIssueRequest;
import org.birdbro.lumen.patient.service.PatientNumberService;
import org.springframework.stereotype.Service;

/**
 * Number issuance via PLATFORM delegation (number-rule-unification-sev task 3.1,
 * design D6; the 2026-11-18 unification ruling): the local row-lock implementation
 * RETIRED - the burned-serial counter lives in the SYSTEM schema behind the
 * {@link NumberApi} Feign contract, and this service consumes it.
 *
 * <p>Semantics preserved through the remote hop (the platform commits the increment
 * in its own transaction): an archive creation that fails after issuance still BURNS
 * the number - the caller's rollback cannot revert the platform-side {@code
 * serial_cur}, and the number is never reissued (proven platform-side in
 * NumberIssuerServiceTests.burnedSerialSurvivesCallerRollback). Failure path: a
 * platform unreachability throws the C0001 fail-fast fallback (never a fabricated
 * number) and the archive creation refuses; B0001 (no active rule) and A0400 (invalid
 * stamp pattern) propagate as the platform's business envelope errors decoded by
 * Feign.</p>
 *
 * @author birdbro
 */
@Service
public class PatientNumberServiceImpl implements PatientNumberService {

    private final NumberApi numberApi;

    public PatientNumberServiceImpl(NumberApi numberApi) {
        this.numberApi = numberApi;
    }

    @Override
    public String issue(String ruleType) {
        R<org.birdbro.lumen.api.number.NumberIssueResponse> response =
                numberApi.issue(NumberIssueRequest.builder().ruleType(ruleType).build());
        if (response == null || response.getData() == null
                || response.getData().getNumber() == null) {
            throw new BusinessException(ExceptionEnum.ERROR_B0001,
                    "号码平台响应缺失数据: " + ruleType);
        }
        return response.getData().getNumber();
    }
}
