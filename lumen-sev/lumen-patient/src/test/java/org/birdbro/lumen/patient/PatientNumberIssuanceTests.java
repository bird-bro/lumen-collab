package org.birdbro.lumen.patient;

import org.birdbro.common.core.R;
import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.api.number.NumberApi;
import org.birdbro.lumen.api.number.NumberIssueRequest;
import org.birdbro.lumen.api.number.NumberIssueResponse;
import org.birdbro.lumen.patient.service.PatientNumberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Patient-side number issuance proof AFTER the platform unification (number-rule-
 * unification-sev task 3.1; spec patient-domain "Image Number Issuance" MODIFIED):
 * the patient service DELEGATES to the platform's NumberApi Feign contract — the
 * row-lock mechanism proofs (concurrency / day reset / format) relocated to the
 * lumen-system suite with `number-rule-unification-sev`. What stays patient-side is
 * the DELEGATION contract:
 *
 * <ol>
 *   <li>the platform number lands on the archive row VERBATIM (never re-formatted);</li>
 *   <li>a platform failure refuses the archive creation (fail-fast — the C0001
 *       fallback throws, no fabricated number, no archive row survives).</li>
 * </ol>
 *
 * <p>The burned-serial platform semantics (a caller rollback burns the number) are
 * proven platform-side (NumberIssuerServiceTests.burnedSerialSurvivesCallerRollback);
 * this suite pins the consumer side of that contract.</p>
 *
 * @author birdbro
 */
class PatientNumberIssuanceTests extends PatientIntegrationTestBase {

    @Autowired
    private PatientNumberService patientNumberService;

    // numberApi arrives from PatientIntegrationTestBase (the suite-wide seam stub);
    // the scenarios below RE-STUB it for their own expectations

    @Test
    void delegationCarriesThePlatformNumberVerbatim() {
        when(numberApi.issue(any(NumberIssueRequest.class)))
                .thenReturn(R.ofSuccess(NumberIssueResponse.builder().number("P2611190001").build()));

        String patId = patientNumberService.issue("影像号");
        assertEquals("P2611190001", patId,
                "the platform number lands VERBATIM - never re-formatted client-side");
    }

    @Test
    void platformFailureRefusesArchiveCreation() {
        when(numberApi.issue(any(NumberIssueRequest.class)))
                .thenThrow(new BusinessException(ExceptionEnum.ERROR_C0001,
                        "号码规则服务不可达，发号失败（fail-fast，绝不造号）: 影像号"));

        BusinessException refused = assertThrows(BusinessException.class,
                () -> patientNumberService.issue("影像号"),
                "a platform failure propagates - the caller's operation refuses");
        assertTrue(String.valueOf(refused.getCode()).startsWith("C"),
                "the platform unreachability is a C-class error: " + refused.getCode());
    }

}
