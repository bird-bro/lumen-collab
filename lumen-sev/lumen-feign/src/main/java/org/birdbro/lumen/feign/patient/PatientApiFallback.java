package org.birdbro.lumen.feign.patient;

import java.util.List;

import org.birdbro.common.core.R;
import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;

import org.springframework.stereotype.Component;

/**
 * The FAIL-FAST fallback of the patient internal contract (us-station-registration-sev
 * task 2.1, design D3/D4): a Sentinel fallback that fabricates a patient identity is
 * FORBIDDEN - an order linked to an invented pat_uid would misattribute the whole exam
 * workflow, and a fabricated ack confirmation would silently drop a patient event - so
 * the fallback simply THROWS the C-class service-unreachable business exception and the
 * caller's operation refuses (the kernel's PatientRegistryPort contract mirrors this:
 * registration refuses without a port result).
 *
 * @author birdbro
 */
@Component
public class PatientApiFallback implements PatientApi {

    @Override
    public R<PatientResolveOrRegisterResult> resolveOrRegister(
            PatientResolveOrRegisterRequest request) {
        throw new BusinessException(ExceptionEnum.ERROR_C0001,
                "患者服务不可达，解析/建档失败（fail-fast，绝不造身份）: "
                + (request != null ? request.getIdNo() : "?"));
    }

    @Override
    public R<PatientLookupResult> lookup(PatientResolveOrRegisterRequest request) {
        throw new BusinessException(ExceptionEnum.ERROR_C0001,
                "患者服务不可达，仅查询解析失败（fail-fast，绝不造身份）: "
                + (request != null ? request.getIdNo() : "?"));
    }

    @Override
    public R<List<PatientOutboxEntry>> pullOutbox(Integer limit) {
        throw new BusinessException(ExceptionEnum.ERROR_C0001,
                "患者服务不可达，outbox 拉取失败（fail-fast，绝不伪造投递）");
    }

    @Override
    public R<Boolean> ackOutbox(Integer id) {
        throw new BusinessException(ExceptionEnum.ERROR_C0001,
                "患者服务不可达，outbox ack 失败（fail-fast，绝不伪造消费确认）: " + id);
    }
}
