package org.birdbro.lumen.us.adapter;

import cn.hutool.core.bean.BeanUtil;

import org.birdbro.common.core.R;
import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.feign.patient.PatientApi;
import org.birdbro.lumen.feign.patient.PatientIdentityResponse;
import org.birdbro.lumen.feign.patient.PatientLookupResult;
import org.birdbro.lumen.feign.patient.PatientResolveOrRegisterRequest;
import org.birdbro.lumen.feign.patient.PatientResolveOrRegisterResult;
import org.birdbro.lumen.core.entity.PatientSnapshot;
import org.birdbro.lumen.core.patient.PatientIdentity;
import org.birdbro.lumen.core.patient.PatientRegistrationRequest;
import org.birdbro.lumen.core.patient.PatientRegistryPort;
import org.springframework.stereotype.Component;

/**
 * The kernel {@link PatientRegistryPort} adapter (us-station-registration-sev task 3.1,
 * design D3): delegates to the patient domain's INTERNAL {@code PatientApi} Feign
 * contract (internal-key guarded - {@code FeignInternalKeyConfig} carries the header),
 * the {@code NumberApi} contract precedent. Refusal discipline (the spec's "Incomplete
 * port result refused" + the kernel port contract):
 *
 * <ul>
 *   <li>an envelope with NO data, an ambiguous CANDIDATES outcome, or a MISSING identity
 *       refuses A0400 BEFORE any order row is written (the caller never reaches the
 *       kernel's {@code registerOrder});</li>
 *   <li>a {@code patUid}-less identity refuses A0400 (the kernel refuses again
 *       downstream - two independent guards on the same invariant);</li>
 *   <li>transport failures propagate UNTOUCHED (the fail-fast fallback throws C0001 -
 *       patient's {@code PatientNumberServiceImpl} precedent: the adapter never catches
 *       a business exception and never fabricates an outcome).</li>
 * </ul>
 *
 * <p>The LOOKUP-ONLY resolve passthrough (task 3.2, the B2 review gap closure) rides
 * {@link #lookup(PatientResolveOrRegisterRequest)} on this same bean - deliberately
 * OUTSIDE the kernel port interface (the kernel owns that seam and stays untouched):
 * exact | candidates | NONE, never a registration.</p>
 *
 * @author birdbro
 */
@Component
public class PatientRegistryPortAdapter implements PatientRegistryPort {

    private final PatientApi patientApi;

    public PatientRegistryPortAdapter(PatientApi patientApi) {
        this.patientApi = patientApi;
    }

    @Override
    public PatientIdentity resolveOrRegister(PatientRegistrationRequest request) {
        R<PatientResolveOrRegisterResult> response =
                patientApi.resolveOrRegister(toContractRequest(request));
        if (response == null || response.getData() == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "患者服务响应缺失数据，拒绝建单（不完整结果绝不落单）");
        }
        PatientResolveOrRegisterResult result = response.getData();
        if (PatientResolveOrRegisterResult.RESULT_CANDIDATES.equals(result.getResult())
                || result.getIdentity() == null) {
            // an ambiguous soft match is a candidate LIST for the UI to pick from, never
            // an order linkage - the create path refuses (the resolve passthrough is the
            // channel that surfaces candidates)
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "患者解析结果不完整（软匹配候选或无身份），拒绝建单: "
                            + (result.getResult() == null ? "null" : result.getResult()));
        }
        if (result.getIdentity().getPatUid() == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "患者解析结果缺少 pat_uid，拒绝建单（pat_uid 是唯一落单锚点）");
        }
        return PatientIdentity.builder()
                .patUid(result.getIdentity().getPatUid())
                .patId(result.getIdentity().getPatId())
                .resolved(PatientResolveOrRegisterResult.RESULT_MATCHED.equals(result.getResult()))
                .snapshot(toSnapshot(result.getIdentity()))
                .build();
    }

    /**
     * The LOOKUP-ONLY resolve passthrough's backend hop: exact | candidates | NONE from
     * the patient domain, unwrapped from the envelope (a data-less envelope refuses -
     * never a fabricated empty). NO side effect on any outcome (the endpoint's own
     * contract; nothing here writes).
     */
    public PatientLookupResult lookup(PatientResolveOrRegisterRequest query) {
        R<PatientLookupResult> response = patientApi.lookup(query);
        if (response == null || response.getData() == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "患者服务响应缺失数据，解析透传失败");
        }
        return response.getData();
    }

    // ---------------------------------------------------------------- mapping

    /**
     * The kernel port request -&gt; the contract body. {@code BeanUtil.copyProperties}
     * copies the matching resolve keys 1:1 (idType/idNo/cardNo/name/gender/phone); the
     * kernel request's exam-time extras (age/patientSource/...) are deliberately NOT on
     * the contract - they ride the us-side snapshot instead.
     */
    private PatientResolveOrRegisterRequest toContractRequest(PatientRegistrationRequest request) {
        PatientResolveOrRegisterRequest contract = new PatientResolveOrRegisterRequest();
        BeanUtil.copyProperties(request, contract, true);
        return contract;
    }

    /**
     * The delivered archive fields the port result carries - the exam-time snapshot's
     * base (the create service overlays the exam-time demographics on top; the kernel
     * keeps a caller-provided snapshot verbatim).
     */
    private PatientSnapshot toSnapshot(PatientIdentityResponse identity) {
        PatientSnapshot snapshot = new PatientSnapshot();
        snapshot.setPatId(identity.getPatId());
        snapshot.setName(identity.getName());
        snapshot.setGender(identity.getGender());
        snapshot.setPhone(identity.getPhone());
        return snapshot;
    }
}
