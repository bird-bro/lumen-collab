package org.birdbro.lumen.core.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.core.entity.ExamExecution;
import org.birdbro.lumen.core.entity.ExamItemRef;
import org.birdbro.lumen.core.entity.ExamOrder;
import org.birdbro.lumen.core.entity.ExamReport;
import org.birdbro.lumen.core.hooks.RegistrationContext;
import org.birdbro.lumen.core.hooks.RegistrationHook;
import org.birdbro.lumen.core.number.NumberIssuerPort;
import org.birdbro.lumen.core.patient.PatientIdentity;
import org.birdbro.lumen.core.patient.PatientRegistryPort;
import org.birdbro.lumen.core.service.ExamOrderAggregateService;
import org.birdbro.lumen.core.service.ExamOrderRegistrationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port-driven registration implementation (lumen-core-registration-kernel task 5.1,
 * design D6). Recorded decisions:
 *
 * <ol>
 *   <li>Identity comes ONLY from the port: a missing or {@code patUid}-less result is
 *       refused BEFORE anything persists (spec: Order creation consumes the port /
 *       Incomplete port result refused). The kernel never resolves patients itself -
 *       consuming services provide the port adapter; the kernel test host stands in a
 *       deterministic fake (test sources only, like TestUserDetailsService).</li>
 *   <li>The patient reference AND the snapshot ride the identity: the reference is
 *       copied to {@code patient_uid}; when the caller brought no snapshot, the
 *       identity's payload becomes it (decision #4 - the exam-time demographics
 *       snapshot is taken at registration).</li>
 *   <li>The accession number (检查号) is issued HERE when the caller did not bring one -
 *       the composition point of the two seams (port + issuer). A caller-provided
 *       accession (e.g. from the HIS confirm path) is kept verbatim.</li>
 *   <li>The whole registration runs in ONE transaction (the aggregate insert inside
 *       it), so a later-batch hook failure still rolls everything back (task 7.1).</li>
 *   <li>Task 7.1 mounted the ordered registration-hook chain here (the EIS five-step
 *       linkage lives OUTSIDE the kernel as consumer beans): hooks fire in
 *       {@code order()} sequence after the aggregate persists, INSIDE the transaction -
 *       any exception propagates and rolls the registration back (spec: Registration
 *       Extension Hooks). Spring injects an empty list when no consumer registered a
 *       hook, so the kernel-only path is unchanged.</li>
 * </ol>
 *
 * @author birdbro
 */
@Service
public class ExamOrderRegistrationServiceImpl implements ExamOrderRegistrationService {

    private final PatientRegistryPort patientRegistryPort;
    private final NumberIssuerPort numberIssuerPort;
    private final ExamOrderAggregateService examOrderAggregateService;
    private final List<RegistrationHook> registrationHooks;

    public ExamOrderRegistrationServiceImpl(PatientRegistryPort patientRegistryPort,
            NumberIssuerPort numberIssuerPort,
            ExamOrderAggregateService examOrderAggregateService,
            List<RegistrationHook> registrationHooks) {
        this.patientRegistryPort = patientRegistryPort;
        this.numberIssuerPort = numberIssuerPort;
        this.examOrderAggregateService = examOrderAggregateService;
        this.registrationHooks = registrationHooks;
    }

    @Override
    @Transactional
    public Integer registerOrder(PatientIdentity identity, ExamOrder order,
            ExamExecution execution, ExamReport report) {
        if (identity == null || identity.getPatUid() == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "检查单必须经患者登记端口取得身份（pat_uid 缺失，拒绝建单）");
        }
        order.setPatientUid(identity.getPatUid());
        if (order.getPatientSnapshot() == null && identity.getSnapshot() != null) {
            order.setPatientSnapshot(identity.getSnapshot());
        }
        if (StrUtil.isBlank(order.getAccessionNo())) {
            // the 登记号 (dict 编号类型 1028 token - the accession family) comes VERBATIM
            // from the number-issuer port (platform-owned rule machinery; fail-fast - a
            // port failure rolls the registration back). The modality scope rides the
            // FIRST exam item's token (number-rule-modality-scope design D5) - null when
            // the order carries no items or a blank token, which resolves the family's
            // GLOBAL pool platform-side (the legacy single-arg behavior preserved).
            order.setAccessionNo(numberIssuerPort.issue("登记号",
                    firstExamItemModality(order.getExamItems())));
        }
        Integer orderId = examOrderAggregateService.insertWithMembers(order, execution, report);

        // the ordered hook chain fires INSIDE the transaction - a hook exception rolls
        // the whole registration back (spec: hook failure rolls back the registration)
        List<RegistrationHook> ordered = new ArrayList<>(registrationHooks);
        ordered.sort(Comparator.comparingInt(RegistrationHook::order));
        RegistrationContext context = RegistrationContext.builder()
                .orderId(orderId).order(order).execution(execution).report(report).build();
        for (RegistrationHook hook : ordered) {
            hook.execute(context);
        }
        return orderId;
    }

    /**
     * The issue modality for the accession: the FIRST exam item's token if non-blank,
     * else {@code null} (the family global fallback). Deliberately first-item ONLY -
     * later items are never scanned (mixed-modality orders are outside v1 scope, design
     * D5; US issues single-项目 orders). The token is client-originated and rides
     * UNVALIDATED - the scope semantics live platform-side. The refs may be the create
     * path's in-memory list OR a mapper-read jsonb list (the HIS confirm feeds the
     * cache target straight in) - both decode TYPED {@code ExamItemRef} elements
     * through {@code ExamItemRefListJsonbTypeHandler}.
     */
    private String firstExamItemModality(List<ExamItemRef> examItems) {
        ExamItemRef first = CollUtil.getFirst(examItems);
        return first == null ? null : StrUtil.trimToNull(first.getModality());
    }
}
