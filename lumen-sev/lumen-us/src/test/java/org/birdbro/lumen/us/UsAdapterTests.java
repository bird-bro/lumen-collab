package org.birdbro.lumen.us;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.birdbro.common.core.R;
import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.feign.patient.PatientIdentityResponse;
import org.birdbro.lumen.feign.patient.PatientLookupResult;
import org.birdbro.lumen.feign.patient.PatientResolveOrRegisterRequest;
import org.birdbro.lumen.feign.patient.PatientResolveOrRegisterResult;
import org.birdbro.lumen.feign.number.NumberIssueRequest;
import org.birdbro.lumen.feign.number.NumberIssueResponse;
import org.birdbro.lumen.core.number.NumberIssuerPort;
import org.birdbro.lumen.core.patient.PatientIdentity;
import org.birdbro.lumen.core.patient.PatientRegistrationRequest;
import org.birdbro.lumen.core.patient.PatientRegistryPort;
import org.birdbro.lumen.us.adapter.PatientRegistryPortAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The port adapters' proof (us-station-registration-sev task 3.1, design D3/D8; specs/us-station
 * "Patient Registry Adapter" - the incomplete-result scenarios ARE the acceptance gate):
 * both adapters are stubbed at the FEIGN SEAM ({@code @MockBean} on the lumen-feign
 * contracts - the shared-test-server feign-consumer-test-seam-stub precedent; no
 * counterpart service runs in this JVM). The adapter refuses an incomplete port result
 * (ambiguous candidates / missing identity / missing patUid / empty envelope) with the
 * house A0400 BEFORE any order row could be written, maps MATCHED/CREATED faithfully to
 * the kernel identity, and propagates a transport failure UNTOUCHED (the fail-fast
 * fallback's C0001 - the patient {@code PatientNumberServiceImpl} precedent: never a
 * fabricated outcome, never a masked code).
 *
 * @author birdbro
 */
class UsAdapterTests extends UsIntegrationTestBase {

    @Autowired
    private PatientRegistryPort patientRegistryPort;

    @Autowired
    private PatientRegistryPortAdapter patientRegistryPortAdapter;

    @Autowired
    private NumberIssuerPort numberIssuerPort;

    // ------------------------------------------- PatientRegistryPort mapping

    @Test
    void matchedPortResultMapsToACompleteKernelIdentity() {
        // a FIXED stubbed outcome (not the shared-band default) so every field assertion
        // is deterministic
        when(patientApi.resolveOrRegister(any())).thenReturn(R.ofSuccess(
                PatientResolveOrRegisterResult.builder()
                        .result(PatientResolveOrRegisterResult.RESULT_MATCHED)
                        .identity(PatientIdentityResponse.builder()
                                .patUid(STUB_UID_BASE + 11)
                                .patId("US3-MATCH")
                                .name("us3匹配档案")
                                .gender(2)
                                .state("RESOLVED")
                                .build())
                        .build()));
        PatientIdentity identity = patientRegistryPort.resolveOrRegister(request());
        assertEquals(Integer.valueOf(STUB_UID_BASE + 11), identity.getPatUid(),
                "the linked patUid is the port contract's NOT NULL key");
        assertEquals("US3-MATCH", identity.getPatId(),
                "the delivered pat_id rides the identity for display");
        assertTrue(identity.isResolved(), "MATCHED means an EXISTING archive was resolved");
        assertNotNull(identity.getSnapshot(), "the delivered archive fields feed the snapshot");
        assertEquals("US3-MATCH", identity.getSnapshot().getPatId(),
                "the snapshot carries the archive's pat_id");
        assertEquals("us3匹配档案", identity.getSnapshot().getName(),
                "the snapshot carries the archive's name");
        assertEquals(Integer.valueOf(2), identity.getSnapshot().getGender(),
                "the snapshot carries the archive's gender");
    }

    @Test
    void createdPortResultMapsToAResolvedFalseIdentity() {
        when(patientApi.resolveOrRegister(any())).thenReturn(R.ofSuccess(
                PatientResolveOrRegisterResult.builder()
                        .result(PatientResolveOrRegisterResult.RESULT_CREATED)
                        .identity(PatientIdentityResponse.builder()
                                .patUid(STUB_UID_BASE + 21)
                                .patId("US3-NEW")
                                .state("PROVISIONAL")
                                .build())
                        .build()));
        PatientIdentity identity = patientRegistryPort.resolveOrRegister(request());
        assertFalse(identity.isResolved(),
                "CREATED means a new/placeholder archive - resolved=false");
        assertEquals(Integer.valueOf(STUB_UID_BASE + 21), identity.getPatUid());
        assertEquals("US3-NEW", identity.getPatId());
    }

    // --------------------------- PatientRegistryPort refusal discipline

    @Test
    void ambiguousCandidatesRefuseBeforeAnyWrite() {
        when(patientApi.resolveOrRegister(any())).thenReturn(R.ofSuccess(
                PatientResolveOrRegisterResult.builder()
                        .result(PatientResolveOrRegisterResult.RESULT_CANDIDATES)
                        .candidates(Collections.singletonList(
                                PatientIdentityResponse.builder().patUid(1).build()))
                        .build()));
        BusinessException refused = assertThrows(BusinessException.class,
                () -> patientRegistryPort.resolveOrRegister(request()),
                "an ambiguous candidate list is never an order linkage - the adapter refuses");
        assertEquals(ExceptionEnum.ERROR_A0400.getCode(), refused.getCode(),
                "the refusal rides the house A0400 envelope code");
        assertTrue(refused.getMsg().contains("候选"),
                "the refusal names the ambiguous shape");
    }

    @Test
    void missingIdentityRefuses() {
        when(patientApi.resolveOrRegister(any())).thenReturn(R.ofSuccess(
                PatientResolveOrRegisterResult.builder()
                        .result(PatientResolveOrRegisterResult.RESULT_MATCHED)
                        .identity(null)
                        .build()));
        BusinessException refused = assertThrows(BusinessException.class,
                () -> patientRegistryPort.resolveOrRegister(request()));
        assertEquals(ExceptionEnum.ERROR_A0400.getCode(), refused.getCode());
    }

    @Test
    void patUidLessIdentityRefusesBeforeAnyWrite() {
        when(patientApi.resolveOrRegister(any())).thenReturn(R.ofSuccess(
                PatientResolveOrRegisterResult.builder()
                        .result(PatientResolveOrRegisterResult.RESULT_MATCHED)
                        .identity(PatientIdentityResponse.builder()
                                .patId("US3-NOUNID")
                                .build())
                        .build()));
        BusinessException refused = assertThrows(BusinessException.class,
                () -> patientRegistryPort.resolveOrRegister(request()),
                "a patUid-less result is the spec's Incomplete port result refused shape");
        assertEquals(ExceptionEnum.ERROR_A0400.getCode(), refused.getCode());
        assertTrue(refused.getMsg().contains("pat_uid"),
                "the refusal names the missing linkage key");
    }

    @Test
    void dataLessEnvelopeRefuses() {
        when(patientApi.resolveOrRegister(any())).thenReturn(R.ofSuccess(null));
        BusinessException refused = assertThrows(BusinessException.class,
                () -> patientRegistryPort.resolveOrRegister(request()));
        assertEquals(ExceptionEnum.ERROR_A0400.getCode(), refused.getCode());
    }

    @Test
    void transportFailurePropagatesFailFastUntouched() {
        when(patientApi.resolveOrRegister(any())).thenThrow(new BusinessException(
                ExceptionEnum.ERROR_C0001, "患者服务不可达，解析/建档失败（fail-fast，绝不造身份）"));
        BusinessException thrown = assertThrows(BusinessException.class,
                () -> patientRegistryPort.resolveOrRegister(request()),
                "the adapter never catches a transport failure - the fail-fast C0001 surfaces");
        assertEquals(ExceptionEnum.ERROR_C0001.getCode(), thrown.getCode(),
                "the C0001 code propagates UNMASKED (the house ApiError style)");
        assertTrue(thrown.getMsg().contains("患者服务不可达，解析/建档失败"),
                "the fallback's detail propagates inside the house message shape");
    }

    // ------------------------------------ the lookup-only passthrough hop

    @Test
    void lookupPassthroughUnwrapsTheEnvelope() {
        when(patientApi.lookup(any())).thenReturn(R.ofSuccess(
                PatientLookupResult.builder()
                        .result(PatientLookupResult.RESULT_MATCHED)
                        .identity(PatientIdentityResponse.builder()
                                .patUid(STUB_UID_BASE + 31)
                                .patId("US3-LOOK")
                                .build())
                        .build()));
        PatientLookupResult result = patientRegistryPortAdapter.lookup(
                new PatientResolveOrRegisterRequest());
        assertEquals(PatientLookupResult.RESULT_MATCHED, result.getResult());
        assertEquals(Integer.valueOf(STUB_UID_BASE + 31), result.getIdentity().getPatUid());
    }

    @Test
    void lookupDataLessEnvelopeRefuses() {
        when(patientApi.lookup(any())).thenReturn(R.ofSuccess(null));
        BusinessException refused = assertThrows(BusinessException.class,
                () -> patientRegistryPortAdapter.lookup(new PatientResolveOrRegisterRequest()),
                "a data-less lookup envelope refuses - never a fabricated empty");
        assertEquals(ExceptionEnum.ERROR_A0400.getCode(), refused.getCode());
    }

    // ------------------------------------------- NumberIssuerPortAdapter

    @Test
    void numberIssuerIssuesThePlatformNumber() {
        String number = numberIssuerPort.issue("登记号");
        assertNotNull(number, "the platform number rides the envelope's data.number");
        assertTrue(number.startsWith(STUB_ACCESSION_PREFIX),
                "the stubbed accession rides the distinctive prefix");
    }

    @Test
    void numberIssuerRefusesMissingData() {
        when(numberApi.issue(any())).thenReturn(R.ofSuccess(
                NumberIssueResponse.builder().number(null).build()));
        BusinessException refused = assertThrows(BusinessException.class,
                () -> numberIssuerPort.issue("登记号"),
                "a number-less envelope refuses (the PatientNumberServiceImpl mirror)");
        assertEquals(ExceptionEnum.ERROR_B0001.getCode(), refused.getCode());
    }

    @Test
    void numberIssuerTransportFailurePropagatesC0001() {
        when(numberApi.issue(any())).thenThrow(new BusinessException(
                ExceptionEnum.ERROR_C0001, "系统服务不可达（fail-fast，绝不造号）"));
        BusinessException thrown = assertThrows(BusinessException.class,
                () -> numberIssuerPort.issue("登记号"));
        assertEquals(ExceptionEnum.ERROR_C0001.getCode(), thrown.getCode(),
                "the C0001 code propagates unmasked - numbers are never fabricated");
    }

    // -------------------- modality-scoped issuance (number-rule-modality-scope task 2.3)

    @Test
    void twoArgIssueForwardsTheModalityTokenOnTheWire() {
        AtomicReference<NumberIssueRequest> captured = captureIssue();
        String number = numberIssuerPort.issue("登记号", "US");
        assertTrue(number.startsWith(STUB_ACCESSION_PREFIX),
                "the overload path still issues the platform number");
        NumberIssueRequest request = captured.get();
        assertNotNull(request, "the two-arg issue reached the number contract");
        assertEquals("登记号", request.getRuleType(),
                "the family rides the request exactly as before");
        assertEquals("US", request.getModality(),
                "the modality token rides the additive contract field verbatim - the "
                        + "adapter never picks, validates, or defaults a rule/scope");
    }

    @Test
    void singleArgIssueKeepsNullModalityOnTheWire() {
        AtomicReference<NumberIssueRequest> captured = captureIssue();
        numberIssuerPort.issue("登记号");
        assertNotNull(captured.get(), "the single-arg issue reached the number contract");
        assertNull(captured.get().getModality(),
                "the legacy single-arg signature stays the null-modality GLOBAL contract");
    }

    @Test
    void twoArgIssueWithNullModalityStaysNullOnTheWire() {
        AtomicReference<NumberIssueRequest> captured = captureIssue();
        numberIssuerPort.issue("登记号", null);
        assertNotNull(captured.get(), "the null-token overload still reaches the contract");
        assertNull(captured.get().getModality(),
                "a null token stays null on the wire - never defaulted to a modality");
    }

    @Test
    void twoArgIssueRefusesMissingData() {
        when(numberApi.issue(any())).thenReturn(R.ofSuccess(
                NumberIssueResponse.builder().number(null).build()));
        BusinessException refused = assertThrows(BusinessException.class,
                () -> numberIssuerPort.issue("登记号", "US"),
                "the overload path keeps the fail-fast contract: a number-less envelope "
                        + "refuses B0001, never a fabricated number");
        assertEquals(ExceptionEnum.ERROR_B0001.getCode(), refused.getCode());
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Points the number seam at an Answer that CAPTURES the outgoing request body -
     * the wire-shape assertions of the modality-scope change read it.
     */
    private AtomicReference<NumberIssueRequest> captureIssue() {
        AtomicReference<NumberIssueRequest> captured = new AtomicReference<>();
        when(numberApi.issue(any())).thenAnswer(inv -> {
            captured.set(inv.getArgument(0));
            return R.ofSuccess(NumberIssueResponse.builder()
                    .number(STUB_ACCESSION_PREFIX + "MOD" + System.nanoTime()).build());
        });
        return captured;
    }

    /** The demographics the create path hands the port. */
    private PatientRegistrationRequest request() {
        return PatientRegistrationRequest.builder()
                .idType("01")
                .idNo("US3ADP-" + System.nanoTime())
                .name("us3适配器档案")
                .gender(1)
                .build();
    }
}
