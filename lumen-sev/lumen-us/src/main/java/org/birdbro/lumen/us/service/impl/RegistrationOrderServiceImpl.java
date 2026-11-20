package org.birdbro.lumen.us.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.hutool.core.util.StrUtil;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.api.patient.PatientLookupResult;
import org.birdbro.lumen.api.patient.PatientResolveOrRegisterRequest;
import org.birdbro.lumen.core.entity.ExamExecution;
import org.birdbro.lumen.core.entity.ExamOrder;
import org.birdbro.lumen.core.entity.ExamOrderStatus;
import org.birdbro.lumen.core.entity.ExamReport;
import org.birdbro.lumen.core.entity.PatientSnapshot;
import org.birdbro.lumen.core.mapper.ExamOrderMapper;
import org.birdbro.lumen.core.patient.PatientIdentity;
import org.birdbro.lumen.core.patient.PatientRegistrationRequest;
import org.birdbro.lumen.core.service.ExamOrderRegistrationService;
import org.birdbro.lumen.core.service.ExamOrderStateMachineService;
import org.birdbro.lumen.us.adapter.PatientRegistryPortAdapter;
import org.birdbro.lumen.us.dto.RegistrationCreateRequest;
import org.birdbro.lumen.us.dto.RegistrationCreateResult;
import org.birdbro.lumen.us.dto.RegistrationOrderPageQuery;
import org.birdbro.lumen.us.dto.RegistrationOrderView;
import org.birdbro.lumen.us.entity.UsRegistrationExt;
import org.birdbro.lumen.us.hook.UsRegistrationExtHolder;
import org.birdbro.lumen.us.mapper.UsRegistrationExtMapper;
import org.birdbro.lumen.us.service.RegistrationOrderService;
import org.springframework.stereotype.Service;

/**
 * The US registration business service implementation (us-station-registration-sev task
 * 3.2, design D6/D7). Recorded decisions:
 *
 * <ol>
 *   <li>Create is NOT transactional here on purpose: the patient-registry port resolves
 *       over HTTP BEFORE any transaction opens (a remote call must never hold a local
 *       transaction open - the kernel hook contract's warning, design D8); the kernel
 *       {@code registerOrder} then runs the ONE registration transaction (aggregate +
 *       accession + the ext hook inside it).</li>
 *   <li>The identity comes ONLY from the port adapter (the kernel contract): ambiguous
 *       candidates / missing patUid refuse in the adapter BEFORE any write; the kernel
 *       re-guards downstream (two independent guards on the same invariant).</li>
 *   <li>The snapshot combines the DELIVERED archive fields (the adapter's port payload)
 *       with the body's exam-time demographics (patient decision #4 - the exam-time
 *       snapshot is taken at registration); the kernel keeps a caller-provided snapshot
 *       verbatim.</li>
 *   <li>The ext block rides the kernel {@code RegistrationHook} seam: stashed
 *       thread-locally before {@code registerOrder}, drained by {@code UsRegistrationHook}
 *       INSIDE the registration transaction, cleared in a finally so a refused
 *       registration never leaks the block onto a pooled servlet thread.</li>
 *   <li>State transitions go through the kernel state machine ONLY (the status column's
 *       single writer): 登记 = BOOKED -&gt; REGISTERED; the cancel branch splits by
 *       stage (BOOKED -&gt; BOOKING_CANCELLED, otherwise -&gt; CANCELLED) and every
 *       illegal jump surfaces the kernel refusal VERBATIM with the stored status
 *       unchanged. A missing order reads the kernel's own A0400 message shape.</li>
 *   <li>The page query rides the kernel {@code ExamOrderMapper} (the raw persistence
 *       surface; the status column stays untouched by reads) + one batched ext load -
 *       the extension rides the view without kernel changes.</li>
 * </ol>
 *
 * @author birdbro
 */
@Service
public class RegistrationOrderServiceImpl implements RegistrationOrderService {

    private final PatientRegistryPortAdapter patientRegistryPortAdapter;
    private final ExamOrderRegistrationService examOrderRegistrationService;
    private final ExamOrderStateMachineService examOrderStateMachineService;
    private final ExamOrderMapper examOrderMapper;
    private final UsRegistrationExtMapper usRegistrationExtMapper;

    public RegistrationOrderServiceImpl(
            PatientRegistryPortAdapter patientRegistryPortAdapter,
            ExamOrderRegistrationService examOrderRegistrationService,
            ExamOrderStateMachineService examOrderStateMachineService,
            ExamOrderMapper examOrderMapper,
            UsRegistrationExtMapper usRegistrationExtMapper) {
        this.patientRegistryPortAdapter = patientRegistryPortAdapter;
        this.examOrderRegistrationService = examOrderRegistrationService;
        this.examOrderStateMachineService = examOrderStateMachineService;
        this.examOrderMapper = examOrderMapper;
        this.usRegistrationExtMapper = usRegistrationExtMapper;
    }

    @Override
    public PatientLookupResult resolveLookup(PatientResolveOrRegisterRequest query) {
        return patientRegistryPortAdapter.lookup(query);
    }

    @Override
    public RegistrationCreateResult create(RegistrationCreateRequest request) {
        // (1) the port resolve-or-register - the ONLY identity source; the adapter
        // refuses incomplete results before any order row is written
        PatientIdentity identity = patientRegistryPortAdapter.resolveOrRegister(
                toPortRequest(request));

        // (2) the order document - entry state BOOKED (the booking surface)
        ExamOrder order = new ExamOrder();
        order.setStatus(ExamOrderStatus.BOOKED);
        order.setExamItems(request.getExamItems());
        order.setEmgFlag(request.getEmgFlag());
        order.setPatientSnapshot(buildSnapshot(request, identity));

        // (3) the ext block rides the kernel hook seam (inside the registration
        // transaction - a hook failure rolls the whole registration back)
        UsRegistrationExtHolder.set(request.getExt());
        try {
            Integer orderId = examOrderRegistrationService.registerOrder(
                    identity, order, new ExamExecution(), new ExamReport());
            return RegistrationCreateResult.builder()
                    .orderId(orderId)
                    .accessionNo(order.getAccessionNo())
                    .status(order.getStatus())
                    .build();
        } finally {
            UsRegistrationExtHolder.clear();
        }
    }

    @Override
    public IPage<RegistrationOrderView> page(RegistrationOrderPageQuery query) {
        LambdaQueryWrapper<ExamOrder> wrapper = new LambdaQueryWrapper<ExamOrder>()
                .eq(query.getStatus() != null, ExamOrder::getStatus, query.getStatus())
                .like(StrUtil.isNotBlank(query.getAccessionNo()),
                        ExamOrder::getAccessionNo, query.getAccessionNo())
                .apply(StrUtil.isNotBlank(query.getPatientName()),
                        "patient_snapshot->>'name' LIKE {0}",
                        "%" + query.getPatientName() + "%")
                .orderByDesc(ExamOrder::getId);
        IPage<ExamOrder> orders = examOrderMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);

        // one batched ext load against the page's order ids (the 1:1 extension rides
        // the view; orders without a row carry null ext fields)
        List<Integer> orderIds = orders.getRecords().stream()
                .map(ExamOrder::getId)
                .collect(Collectors.toList());
        Map<Integer, UsRegistrationExt> extByOrderId = orderIds.isEmpty()
                ? Collections.emptyMap()
                : usRegistrationExtMapper.selectList(new LambdaQueryWrapper<UsRegistrationExt>()
                                .in(UsRegistrationExt::getOrderId, orderIds))
                        .stream()
                        .collect(Collectors.toMap(UsRegistrationExt::getOrderId,
                                Function.identity()));
        return orders.convert(order -> toView(order, extByOrderId.get(order.getId())));
    }

    @Override
    public ExamOrderStatus register(Integer orderId) {
        // the kernel state machine is the status column's SINGLE writer - the refusal
        // message surfaces verbatim, the stored status stays unchanged
        return examOrderStateMachineService.transition(orderId,
                ExamOrderStatus.REGISTERED, null);
    }

    @Override
    public ExamOrderStatus cancel(Integer orderId, String reason) {
        // the branch splits by stage - the kernel refuses the illegal shapes verbatim
        ExamOrder order = examOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "检查单不存在: " + orderId);
        }
        ExamOrderStatus target = order.getStatus() == ExamOrderStatus.BOOKED
                ? ExamOrderStatus.BOOKING_CANCELLED
                : ExamOrderStatus.CANCELLED;
        return examOrderStateMachineService.transition(orderId, target, reason);
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The create body -&gt; the kernel port request: the resolution chain's keys copy
     * 1:1 (idType/idNo/cardNo/name/gender/phone). The exam-time extras do NOT ride the
     * port (they are not resolution keys) - they join the snapshot instead.
     */
    private PatientRegistrationRequest toPortRequest(RegistrationCreateRequest request) {
        return PatientRegistrationRequest.builder()
                .idType(request.getIdType())
                .idNo(request.getIdNo())
                .cardNo(request.getCardNo())
                .name(request.getName())
                .gender(request.getGender())
                .phone(request.getPhone())
                .build();
    }

    /**
     * The exam-time snapshot (patient decision #4): the delivered archive fields are the
     * base (the adapter's port payload), the body's exam-time demographics overlay, and
     * the body's demographics fill any gap the archive payload leaves.
     */
    private PatientSnapshot buildSnapshot(RegistrationCreateRequest request,
            PatientIdentity identity) {
        PatientSnapshot snapshot = identity.getSnapshot() != null
                ? identity.getSnapshot() : new PatientSnapshot();
        snapshot.setAge(request.getAge());
        snapshot.setPatientSource(request.getPatientSource());
        snapshot.setAdmissionNumber(request.getAdmissionNumber());
        snapshot.setBedNumber(request.getBedNumber());
        snapshot.setHeight(request.getHeight());
        snapshot.setWeight(request.getWeight());
        if (snapshot.getName() == null) {
            snapshot.setName(request.getName());
        }
        if (snapshot.getGender() == null) {
            snapshot.setGender(request.getGender());
        }
        if (snapshot.getPhone() == null) {
            snapshot.setPhone(request.getPhone());
        }
        return snapshot;
    }

    /** The order document + its ext row -&gt; the flattened view row. */
    private RegistrationOrderView toView(ExamOrder order, UsRegistrationExt ext) {
        RegistrationOrderView view = RegistrationOrderView.builder()
                .orderId(order.getId())
                .accessionNo(order.getAccessionNo())
                .status(order.getStatus())
                .patientUid(order.getPatientUid())
                .patientSnapshot(order.getPatientSnapshot())
                .examItems(order.getExamItems())
                .emgFlag(order.getEmgFlag())
                .cancelReason(order.getCancelReason())
                .createTime(order.getCreateTime())
                .build();
        if (ext != null) {
            view.setPregnancyStatus(ext.getPregnancyStatus());
            view.setGestationalWeeks(ext.getGestationalWeeks());
            view.setNote(ext.getNote());
        }
        return view;
    }
}
