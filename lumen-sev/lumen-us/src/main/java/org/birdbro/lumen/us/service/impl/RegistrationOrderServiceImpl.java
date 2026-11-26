package org.birdbro.lumen.us.service.impl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.feign.patient.PatientLookupResult;
import org.birdbro.lumen.feign.patient.PatientResolveOrRegisterRequest;
import org.birdbro.lumen.core.entity.ExamExecution;
import org.birdbro.lumen.core.entity.ExamOrder;
import org.birdbro.lumen.core.entity.ExamOrderStatus;
import org.birdbro.lumen.core.entity.ExamRegistrationType;
import org.birdbro.lumen.core.entity.ExamReport;
import org.birdbro.lumen.core.entity.ExamSlotConfig;
import org.birdbro.lumen.core.entity.PatientSnapshot;
import org.birdbro.lumen.core.entity.SlotWindow;
import org.birdbro.lumen.core.entity.SlotWindowBounds;
import org.birdbro.lumen.core.mapper.ExamOrderMapper;
import org.birdbro.lumen.core.mapper.ExamSlotConfigMapper;
import org.birdbro.lumen.core.patient.PatientIdentity;
import org.birdbro.lumen.core.patient.PatientRegistrationRequest;
import org.birdbro.lumen.core.service.ExamOrderRegistrationService;
import org.birdbro.lumen.core.service.ExamOrderStateMachineService;
import org.birdbro.lumen.core.service.ExamSlotCapacityService;
import org.birdbro.lumen.core.service.support.PreRegistrationPolicy;
import org.birdbro.lumen.us.adapter.PatientRegistryPortAdapter;
import org.birdbro.lumen.us.dto.RegistrationCreateRequest;
import org.birdbro.lumen.us.dto.RegistrationCreateResult;
import org.birdbro.lumen.us.dto.RegistrationOrderPageQuery;
import org.birdbro.lumen.us.dto.RegistrationOrderView;
import org.birdbro.lumen.us.dto.SlotOptionsView;
import org.birdbro.lumen.us.dto.SlotOptionsView.SlotOption;
import org.birdbro.lumen.us.entity.UsRegistrationExt;
import org.birdbro.lumen.us.hook.UsRegistrationExtHolder;
import org.birdbro.lumen.us.mapper.UsRegistrationExtMapper;
import org.birdbro.lumen.us.service.RegistrationOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final PreRegistrationPolicy preRegistrationPolicy;
    private final ExamSlotCapacityService examSlotCapacityService;
    private final ExamSlotConfigMapper examSlotConfigMapper;
    private final TransactionTemplate transactionTemplate;

    public RegistrationOrderServiceImpl(
            PatientRegistryPortAdapter patientRegistryPortAdapter,
            ExamOrderRegistrationService examOrderRegistrationService,
            ExamOrderStateMachineService examOrderStateMachineService,
            ExamOrderMapper examOrderMapper,
            UsRegistrationExtMapper usRegistrationExtMapper,
            PreRegistrationPolicy preRegistrationPolicy,
            ExamSlotCapacityService examSlotCapacityService,
            ExamSlotConfigMapper examSlotConfigMapper,
            PlatformTransactionManager transactionManager) {
        this.patientRegistryPortAdapter = patientRegistryPortAdapter;
        this.examOrderRegistrationService = examOrderRegistrationService;
        this.examOrderStateMachineService = examOrderStateMachineService;
        this.examOrderMapper = examOrderMapper;
        this.usRegistrationExtMapper = usRegistrationExtMapper;
        this.preRegistrationPolicy = preRegistrationPolicy;
        this.examSlotCapacityService = examSlotCapacityService;
        this.examSlotConfigMapper = examSlotConfigMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public PatientLookupResult resolveLookup(PatientResolveOrRegisterRequest query) {
        return patientRegistryPortAdapter.lookup(query);
    }

    @Override
    public RegistrationCreateResult create(RegistrationCreateRequest request) {
        // (1) the port resolve-or-register - the ONLY identity source; the adapter
        // refuses incomplete results before any order row is written. It stays OUTSIDE
        // any transaction (kernel design D8 - a remote call never holds a local tx).
        PatientIdentity identity = patientRegistryPortAdapter.resolveOrRegister(
                toPortRequest(request));

        // (2) the ext block rides the kernel hook seam (inside the registration
        // transaction - a hook failure rolls the whole registration back). The entity
        // binds the client body 1:1 (kept by design), but the AUDIT columns are
        // SERVER-OWNED (B3 review Minor-1): a client-posted deleted=1 must never
        // persist (the row would arrive soft-deleted), and create/update stamps belong
        // to the meta-fill security header + the DB defaults, never the body - so the
        // stashed ext is stripped of them right here, before the hook persists it.
        UsRegistrationExt ext = request.getExt();
        if (ext != null) {
            ext.setDeleted(null);
            ext.setCreateBy(null);
            ext.setUpdateBy(null);
            ext.setCreateTime(null);
            ext.setUpdateTime(null);
        }
        UsRegistrationExtHolder.set(ext);
        try {
            if (request.getBookingTime() == null) {
                // (3a) the WALK-IN path: 提交即登记 (拍板 02-B) - REGISTERED directly
                // (us-prereg-as-registered: deriveEntryStatus retires from the us flow,
                // the kernel support method stays untouched); no slot-config machinery
                // is touched (an unconfigured modality never blocks a walk-in). The
                // category stamp is SERVER-OWNED - the request DTO carries no
                // registrationType property, so the body can never set it.
                ExamOrder order = newOrder(request, identity);
                order.setStatus(ExamOrderStatus.REGISTERED);
                order.setRegistrationType(ExamRegistrationType.WALK_IN);
                Integer orderId = examOrderRegistrationService.registerOrder(
                        identity, order, new ExamExecution(), new ExamReport());
                return toResult(order, orderId);
            }
            // (3b) the PRE-REGISTRATION path (us-registration-redesign-api design
            // D1): the capacity dance + the aggregate registration in ONE caller
            // transaction - the kernel validate's FOR UPDATE config-row lock (D7)
            // serializes same-slot creates and any refusal rolls everything back
            return transactionTemplate.execute(tx ->
                    registerPreRegistration(request, identity));
        } finally {
            UsRegistrationExtHolder.clear();
        }
    }

    /** The create result from the just-registered document (the derived state echo). */
    private RegistrationCreateResult toResult(ExamOrder order, Integer orderId) {
        return RegistrationCreateResult.builder()
                .orderId(orderId)
                .accessionNo(order.getAccessionNo())
                .status(order.getStatus())
                .registrationType(order.getRegistrationType())
                .bookingTime(order.getBookingTime())
                .build();
    }

    /** Builds the order document from the body (the entry state set by the caller path). */
    private ExamOrder newOrder(RegistrationCreateRequest request, PatientIdentity identity) {
        ExamOrder order = new ExamOrder();
        order.setExamItems(request.getExamItems());
        order.setEmgFlag(request.getEmgFlag());
        order.setPatientSnapshot(buildSnapshot(request, identity));
        order.setBookingTime(request.getBookingTime());
        return order;
    }

    /**
     * The composed pre-registration create (design D1 - the kernel flow-test reference
     * composition): guard the past BEFORE the count (D4), lock the config row FOR
     * UPDATE, resolve the slot window, count the live BOOKED orders scoped by the first
     * item's modality, validate, then derive BOOKED and register INSIDE the same
     * transaction.
     */
    private RegistrationCreateResult registerPreRegistration(RegistrationCreateRequest request,
            PatientIdentity identity) {
        ExamOrder order = newOrder(request, identity);
        Date dbNow = examSlotConfigMapper.selectDbNow();
        preRegistrationPolicy.requireFutureBooking(request.getBookingTime(), dbNow);
        String modality = firstExamItemModality(request.getExamItems());
        ExamSlotConfig config = examSlotConfigMapper.selectOne(
                new LambdaQueryWrapper<ExamSlotConfig>()
                        .eq(ExamSlotConfig::getModality, modality)
                        .last("FOR UPDATE"));
        SlotWindowBounds bounds = examSlotCapacityService.resolveSlotWindow(config,
                request.getBookingTime());
        // 未取消即占位 (us-prereg-as-registered): the occupancy counts live
        // not-cancelled orders in the window - the reservation survives every live
        // stage (the retired predicate counted BOOKED rows only)
        int occupancy = examOrderMapper.selectCount(new LambdaQueryWrapper<ExamOrder>()
                .notIn(ExamOrder::getStatus, ExamOrderStatus.BOOKING_CANCELLED,
                        ExamOrderStatus.CANCELLED)
                .ge(ExamOrder::getBookingTime, bounds.getWindowStart())
                .lt(ExamOrder::getBookingTime, bounds.getWindowEnd())
                .apply("exam_items->0->>'modality' = {0}", modality))
                .intValue();
        examSlotCapacityService.validatePreregistration(modality, request.getBookingTime(),
                occupancy);
        // 预登记=已登记 (us-prereg-as-registered, user ruling 2026-11-24): a future slot
        // enters REGISTERED too - the slot anchor records the appointment, not a pending
        // state; the category stamp marks the pre-registration branch.
        order.setStatus(ExamOrderStatus.REGISTERED);
        order.setRegistrationType(ExamRegistrationType.PRE_REGISTERED);
        Integer orderId = examOrderRegistrationService.registerOrder(
                identity, order, new ExamExecution(), new ExamReport());
        return toResult(order, orderId);
    }

    @Override
    public SlotOptionsView slotOptions(String date, String modality) {
        // the strict date anchor (A0400 on garbage - the endpoint is a booking aid,
        // not a general calendar)
        LocalDate day;
        try {
            day = LocalDate.parse(StrUtil.trimToEmpty(date));
        } catch (RuntimeException parseFailure) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "预约日期格式无效: " + date + "（应为 yyyy-MM-dd）");
        }
        // the modality's LIVE config, read-only (no FOR UPDATE - the serialization
        // point stays create-side; a stale read only costs a gray/white tint)
        ExamSlotConfig config = examSlotConfigMapper.selectOne(
                new LambdaQueryWrapper<ExamSlotConfig>()
                        .eq(ExamSlotConfig::getModality, modality));
        if (config == null || CollUtil.isEmpty(config.getOpenWindows())
                || config.getGranularityMinutes() == null || config.getGranularityMinutes() <= 0) {
            return SlotOptionsView.builder().configured(false).slots(Collections.emptyList()).build();
        }
        int granularity = config.getGranularityMinutes();
        List<SlotOption> slots = new ArrayList<>();
        for (SlotWindow window : config.getOpenWindows()) {
            final LocalTime windowOpen;
            final LocalTime windowClose;
            try {
                windowOpen = LocalTime.parse(window.getStart());
                windowClose = LocalTime.parse(window.getEnd());
            } catch (RuntimeException badWindow) {
                // the create path fails closed on unparseable windows - the read
                // mirrors it by skipping the broken window (the picker shows the
                // rest; the create still refuses the same broken booking)
                continue;
            }
            LocalTime cursor = windowOpen;
            while (!cursor.plusMinutes(granularity).isAfter(windowClose)) {
                LocalTime slotEnd = cursor.plusMinutes(granularity);
                Date slotStartTs = java.sql.Timestamp.valueOf(day.atTime(cursor));
                Date slotEndTs = java.sql.Timestamp.valueOf(day.atTime(slotEnd));
                int booked = examOrderMapper.selectCount(new LambdaQueryWrapper<ExamOrder>()
                        .notIn(ExamOrder::getStatus, ExamOrderStatus.BOOKING_CANCELLED,
                                ExamOrderStatus.CANCELLED)
                        .ge(ExamOrder::getBookingTime, slotStartTs)
                        .lt(ExamOrder::getBookingTime, slotEndTs)
                        .apply("exam_items->0->>'modality' = {0}", modality))
                        .intValue();
                slots.add(SlotOption.builder()
                        .start(cursor.toString())
                        .end(slotEnd.toString())
                        .booked(booked)
                        .capacity(config.getSlotCapacity() == null ? 0 : config.getSlotCapacity())
                        .build());
                cursor = slotEnd;
            }
        }
        return SlotOptionsView.builder().configured(true).slots(slots).build();
    }

    @Override
    public IPage<RegistrationOrderView> page(RegistrationOrderPageQuery query) {
        IPage<ExamOrder> orders = examOrderMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                wideQueryWrapper(query));

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
    public Map<String, Long> statusCounts(RegistrationOrderPageQuery query) {
        // the SAME wrapper MINUS the status condition (design D5); one group-by count,
        // zero-filled across all six states so the chips render the full meter
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ExamOrder> counts =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ExamOrder>()
                        .select("status", "COUNT(*) AS cnt");
        appendWideConditions(counts, query, false);
        counts.groupBy("status");
        Map<String, Long> meter = new java.util.LinkedHashMap<>();
        for (ExamOrderStatus status : ExamOrderStatus.values()) {
            meter.put(status.name(), 0L);
        }
        examOrderMapper.selectMaps(counts).forEach(row -> {
            Object status = row.get("status");
            Object cnt = row.get("cnt");
            if (status != null && cnt != null) {
                meter.put(String.valueOf(status),
                        ((Number) cnt).longValue());
            }
        });
        return meter;
    }

    /**
     * The wide-table predicate builder (design D4 - the 拍板 03-B RCP∪EIS union): status
     * multi-select IN, accession/name LIKE, the three EXACT jsonb snapshot conditions,
     * the per-element exam-item name fuzzy, and the apply-time (= create_time) range.
     * Shared by the page and the status meter so the chips always match the list.
     */
    private com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ExamOrder> appendWideConditions(
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ExamOrder> wrapper,
            RegistrationOrderPageQuery query, boolean withStatus) {
        if (withStatus && CollUtil.isNotEmpty(query.getStatus())) {
            wrapper.in("status", query.getStatus());
        }
        wrapper.like(StrUtil.isNotBlank(query.getAccessionNo()), "accession_no",
                        query.getAccessionNo())
                .apply(StrUtil.isNotBlank(query.getPatientName()),
                        "patient_snapshot->>'name' LIKE {0}",
                        "%" + query.getPatientName() + "%")
                .apply(StrUtil.isNotBlank(query.getPatientId()),
                        "patient_snapshot->>'patId' = {0}", query.getPatientId())
                .apply(StrUtil.isNotBlank(query.getPatientSource()),
                        "patient_snapshot->>'patientSource' = {0}", query.getPatientSource())
                .apply(StrUtil.isNotBlank(query.getCardNo()),
                        "patient_snapshot->>'cardNo' = {0}", query.getCardNo())
                .apply(StrUtil.isNotBlank(query.getExamItemName()),
                        "EXISTS (SELECT 1 FROM jsonb_array_elements(exam_items) e"
                        + " WHERE e->>'itemName' LIKE {0})",
                        "%" + query.getExamItemName() + "%")
                .ge(query.getApplyTimeStart() != null, "create_time", query.getApplyTimeStart())
                .le(query.getApplyTimeEnd() != null, "create_time", query.getApplyTimeEnd());
        return wrapper;
    }

    /** The page's wrapper: the wide conditions + the status multi-select, id desc.
     *  String-column route throughout (the enum values bind through the house
     *  enum-name handler exactly like the typed lambda route). */
    private com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ExamOrder> wideQueryWrapper(
            RegistrationOrderPageQuery query) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ExamOrder> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ExamOrder>()
                        .orderByDesc("id");
        appendWideConditions(wrapper, query, true);
        return wrapper;
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
     * 1:1 (idType/idNo/cardNo/name/gender/birthDate/phone) - birthDate joins as a
     * soft-match key (us-registration-birthdate-sev, specs/us-station "Birth Date
     * Capture"). The exam-time extras (age/patientSource/...) do NOT ride the port -
     * they join the snapshot instead.
     */
    private PatientRegistrationRequest toPortRequest(RegistrationCreateRequest request) {
        return PatientRegistrationRequest.builder()
                .idType(request.getIdType())
                .idNo(request.getIdNo())
                .cardNo(request.getCardNo())
                .name(request.getName())
                .gender(request.getGender())
                .birthDate(request.getBirthDate())
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
        // the soft key stored into the snapshot so the card-no workbench query reads
        // exam_order directly (us-registration-redesign-api design D7 - the additive
        // jsonb key; pre-widening rows read null)
        snapshot.setCardNo(request.getCardNo());
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
                .createBy(order.getCreateBy())
                .registrationType(order.getRegistrationType())
                .bookingTime(order.getBookingTime())
                .build();
        if (ext != null) {
            view.setPregnancyStatus(ext.getPregnancyStatus());
            view.setGestationalWeeks(ext.getGestationalWeeks());
            view.setNote(ext.getNote());
            view.setApplyDept(ext.getApplyDept());
            view.setApplyDoctor(ext.getApplyDoctor());
            view.setApplyTime(ext.getApplyTime());
            view.setApplyDeptId(ext.getApplyDeptId());
            view.setApplyDoctorId(ext.getApplyDoctorId());
        }
        return view;
    }

    /**
     * The validation modality for the pre-registration dance (design D1): the FIRST
     * exam item's token (the issuer-scope derivation precedent, kernel D5) -
     * blank/absent yields {@code null} and the kernel config lookup refuses
     * fail-closed (该检查类型未开通预登记).
     */
    private String firstExamItemModality(List<org.birdbro.lumen.core.entity.ExamItemRef> examItems) {
        org.birdbro.lumen.core.entity.ExamItemRef first = CollUtil.getFirst(examItems);
        return first == null ? null : StrUtil.trimToNull(first.getModality());
    }
}
