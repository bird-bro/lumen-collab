package org.birdbro.lumen.us.poller;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.birdbro.common.core.R;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.api.patient.PatientApi;
import org.birdbro.lumen.api.patient.PatientOutboxEntry;
import org.birdbro.lumen.core.entity.ExamOrder;
import org.birdbro.lumen.core.entity.ExamOrderStatus;
import org.birdbro.lumen.core.entity.PatientSnapshot;
import org.birdbro.lumen.core.mapper.ExamOrderMapper;
import org.birdbro.lumen.core.service.ExamOrderAggregateService;

/**
 * The patient-snapshot poller (us-station-registration-sev task 4.1, design D5; specs/
 * us-station "Snapshot Refresh Wiring" - the two WHEN/THEN scenarios ARE the acceptance
 * gate). Batch-pulls PENDING entries from the patient domain's internal outbox over the
 * {@link PatientApi} Feign contract (the internal key rides {@code FeignInternalKeyConfig}
 * automatically) and dispatches each entry: {@code PATIENT_CHANGED} refreshes the jsonb
 * snapshot of every NON-CANCELLED live order of the payload patient through the kernel
 * {@code refreshPatientSnapshot}; {@code PATIENT_MERGED} repoints
 * {@code exam_order.patient_uid} to the surviving archive for the same live set (us-side
 * mapper update - design D5's minimal-merge hygiene: historical cancelled orders keep the
 * old uid) before the same refresh. Rows are acked ONLY after their dispatch succeeded.
 *
 * <p>Failure discipline (design D5's replay-safe contract - refresh is an overwrite,
 * repoint is idempotent, so replay is always safe):</p>
 *
 * <ul>
 *   <li><b>Pull/ack transport failures</b> (the fail-fast C0001 fallback or any
 *       transport throw): the whole batch skips on a failed pull - no acks, the next
 *       pass retries; a failed ack leaves that row PENDING for replay. Transport doubt
 *       never marks a row consumed.</li>
 *   <li><b>Per-entry dispatch failures</b> (unparseable payload, missing linkage uid,
 *       unknown event type): caught PER ENTRY - the batch continues, the failed row gets
 *       NO ack and stays PENDING for replay (one poisoned entry never blocks the batch).</li>
 *   <li><b>Kernel guard refusals</b> (the report is ISSUED - 医疗文书不静默改): DETERMINISTIC
 *       and permanent, so a refused order is DELIVERED-BUT-NOT-APPLICABLE, not a dispatch
 *       failure - the entry still ACKS. Re-pulling forever cannot change the report state;
 *       the kernel javadoc delegates the follow-up to the consumer (e.g. a manual
 *       re-issue prompt). DOCUMENTED CHOICE per design D5's ack semantics.</li>
 * </ul>
 *
 * <p>Snapshot overlay discipline (patient decision #4 - the exam-time snapshot is taken
 * at registration): the refresh target is the order's CURRENT snapshot overlaid with the
 * event's DELIVERED archive fields ({@code patId}/{@code name}/{@code gender}/{@code
 * phone} - exactly the archive fields the registration adapter maps). The exam-time
 * demographics (age/patientSource/admissionNumber/bedNumber/height/weight) are NEVER
 * touched by the poller, and a null archive field never erases a snapshot value the
 * archive does not authoritatively own (the create path may have gap-filled it from the
 * registration request). An order with no stored snapshot and an event that delivers no
 * archive field takes NO kernel write.</p>
 *
 * <p>PATIENT_MERGED payload note (recorded gap): the patient outbox writer emits only
 * {@code {"survivorId":..,"mergedId":..}} - no archive fields - so today the merged
 * entries' refresh overlay is empty and the kernel write is content-neutral (the repoint
 * is the delivery). The dispatch path is UNIFORM with PATIENT_CHANGED: the day the
 * payload carries the survivor's archive, the same code applies it with zero us-side
 * change. The patient-side widening is pending the patient module's own decision - never
 * widened silently from here.</p>
 *
 * <p>Scheduling: {@code @EnableScheduling} on the service shell; the fixed delay is
 * configurable ({@code lumen.us.snapshot-poll-delay-ms}, default 10s) and the batch cap
 * clamps non-positive values back to the D5 default (50). Tests drive {@link #pollOnce()}
 * directly - the suite base pins the delay to one hour so the scheduler never races a
 * suite's stubbed batch.</p>
 *
 * @author birdbro
 */
@Component
public class PatientSnapshotPoller {

    private static final Logger log = LoggerFactory.getLogger(PatientSnapshotPoller.class);

    /** The D5 batch cap - also the clamp target for a non-positive configured limit. */
    static final int DEFAULT_PULL_LIMIT = 50;

    /**
     * The event families the patient outbox writer emits (the patient module's
     * {@code PatientEventOutboxService} constants, mirrored as literals - lumen-us never
     * imports lumen-patient; the raw-jsonb payload is the deliberate decoupling seam).
     */
    static final String EVENT_PATIENT_CHANGED = "PATIENT_CHANGED";
    static final String EVENT_PATIENT_MERGED = "PATIENT_MERGED";

    private final PatientApi patientApi;
    private final ExamOrderMapper examOrderMapper;
    private final ExamOrderAggregateService examOrderAggregateService;
    private final int pollLimit;

    public PatientSnapshotPoller(PatientApi patientApi, ExamOrderMapper examOrderMapper,
            ExamOrderAggregateService examOrderAggregateService,
            @Value("${lumen.us.snapshot-poll-limit:50}") int pollLimit) {
        this.patientApi = patientApi;
        this.examOrderMapper = examOrderMapper;
        this.examOrderAggregateService = examOrderAggregateService;
        this.pollLimit = pollLimit;
    }

    /** The scheduled entry point (fixed delay - no overlap of this poller with itself). */
    @Scheduled(fixedDelayString = "${lumen.us.snapshot-poll-delay-ms:10000}")
    public void scheduledPoll() {
        pollOnce();
    }

    /**
     * One poll pass: pull a PENDING batch, dispatch each entry, ack the dispatched ones.
     * Public so the suites drive it deterministically without waiting on the clock.
     */
    public void pollOnce() {
        List<PatientOutboxEntry> batch;
        try {
            R<List<PatientOutboxEntry>> envelope = patientApi.pullOutbox(effectiveLimit());
            batch = envelope == null ? null : envelope.getData();
        } catch (Exception | AssertionError transport) {
            // transport doubt (fail-fast C0001 fallback included): the whole batch
            // skips - no acks anywhere, the next pass retries the same PENDING rows.
            // AssertionError is in the net deliberately: the Sentinel feign handler
            // (spring-cloud-alibaba 2021.1) wraps the fallback's BusinessException in
            // an AssertionError - an Error, not an Exception (live finding
            // 2026-11-20; the regression test pins the wrapper shape).
            log.warn("[us-snapshot-poller] outbox pull failed - batch skipped, no acks "
                    + "(replay next pass): {}", transport.getMessage());
            return;
        }
        if (batch == null || batch.isEmpty()) {
            return;
        }
        for (PatientOutboxEntry entry : batch) {
            dispatchSafely(entry);
        }
    }

    // ---------------------------------------------------------------- dispatch

    /**
     * One entry, one try/catch: the dispatch and its ack succeed together or the row
     * stays PENDING (NO ack on failure - the replay contract).
     */
    private void dispatchSafely(PatientOutboxEntry entry) {
        if (entry == null || entry.getId() == null) {
            log.warn("[us-snapshot-poller] a pulled entry carries no id - skipped");
            return;
        }
        try {
            dispatch(entry);
            patientApi.ackOutbox(entry.getId());
        } catch (Exception | AssertionError failed) {
            // AssertionError included: Sentinel wraps fallback business exceptions in
            // it (see the pull-site comment) - the replay contract must hold for the
            // wrapper too.
            log.warn("[us-snapshot-poller] outbox entry {} dispatch failed - NO ack, the "
                    + "row stays PENDING for replay: {}", entry.getId(), failed.getMessage());
        }
    }

    /** Routes one entry by its event family; an unknown family is a poisoned entry. */
    private void dispatch(PatientOutboxEntry entry) {
        JSONObject payload = parsePayload(entry);
        if (EVENT_PATIENT_CHANGED.equals(entry.getEventType())) {
            dispatchPatientChanged(payload);
        } else if (EVENT_PATIENT_MERGED.equals(entry.getEventType())) {
            dispatchPatientMerged(payload);
        } else {
            throw new IllegalStateException("outbox entry " + entry.getId()
                    + " carries unknown event type '" + entry.getEventType()
                    + "' - cannot dispatch");
        }
    }

    /** PATIENT_CHANGED: refresh the patient's live orders with the delivered archive. */
    private void dispatchPatientChanged(JSONObject payload) {
        Integer patientUid = requiredUid(payload, "id");
        PatientSnapshot delivery = archiveDelivery(payload);
        for (ExamOrder order : liveOrders(patientUid)) {
            refreshOrder(order, delivery);
        }
    }

    /**
     * PATIENT_MERGED: repoint the merged-away uid's live set to the survivor, then
     * refresh the SAME live set (the repoint is the merge's delivery; the refresh rides
     * the uniform overlay path - see the class javadoc's payload note).
     */
    private void dispatchPatientMerged(JSONObject payload) {
        Integer survivorUid = requiredUid(payload, "survivorId");
        Integer mergedUid = requiredUid(payload, "mergedId");
        List<ExamOrder> live = liveOrders(mergedUid);
        repointLiveOrders(mergedUid, survivorUid);
        PatientSnapshot delivery = archiveDelivery(payload);
        for (ExamOrder order : live) {
            refreshOrder(order, delivery);
        }
    }

    // ---------------------------------------------------------------- steps

    /**
     * The live set (design D5): deleted = 0 rides the entity's {@code @TableLogic}
     * automatically; the two cancel states are excluded - a cancelled order's snapshot
     * is history, never refreshed, and its patient_uid is never repointed.
     */
    private List<ExamOrder> liveOrders(Integer patientUid) {
        return examOrderMapper.selectList(new LambdaQueryWrapper<ExamOrder>()
                .eq(ExamOrder::getPatientUid, patientUid)
                .notIn(ExamOrder::getStatus, ExamOrderStatus.BOOKING_CANCELLED,
                        ExamOrderStatus.CANCELLED));
    }

    /**
     * The us-side mapper update (design D5's documented v1 hygiene): patient_uid old to
     * new over the live-set predicate, re-evaluated ATOMICALLY in the UPDATE's WHERE -
     * historical cancelled orders keep the old uid; deleted = 0 rides {@code @TableLogic}.
     */
    private void repointLiveOrders(Integer mergedUid, Integer survivorUid) {
        examOrderMapper.update(null, new LambdaUpdateWrapper<ExamOrder>()
                .set(ExamOrder::getPatientUid, survivorUid)
                .eq(ExamOrder::getPatientUid, mergedUid)
                .notIn(ExamOrder::getStatus, ExamOrderStatus.BOOKING_CANCELLED,
                        ExamOrderStatus.CANCELLED));
    }

    /**
     * One order's snapshot refresh through the kernel aggregate service. The kernel's
     * ISSUED-report guard is a DETERMINISTIC refusal (delivered but not applicable) -
     * caught here so the entry still acks; ANY other failure propagates to the entry
     * handler and keeps the row PENDING.
     */
    private void refreshOrder(ExamOrder order, PatientSnapshot delivery) {
        PatientSnapshot snapshot = resolveRefreshSnapshot(order.getPatientSnapshot(), delivery);
        if (snapshot == null) {
            return; // nothing stored and nothing delivered - no kernel write
        }
        try {
            examOrderAggregateService.refreshPatientSnapshot(order.getId(), snapshot);
        } catch (BusinessException refused) {
            log.info("[us-snapshot-poller] order {} snapshot refresh refused by the kernel "
                    + "guard - delivered but not applicable, the entry still acks: {}",
                    order.getId(), refused.getMsg());
        }
    }

    /**
     * The refresh target: the current snapshot overlaid with the delivered archive
     * fields (non-null overlay - a null archive field never erases a snapshot value the
     * archive does not authoritatively own). {@code null} = no stored snapshot AND no
     * delivered field - nothing to write.
     */
    private PatientSnapshot resolveRefreshSnapshot(PatientSnapshot current,
            PatientSnapshot delivery) {
        if (current == null && !hasArchiveField(delivery)) {
            return null;
        }
        PatientSnapshot snapshot = current != null ? current : new PatientSnapshot();
        if (delivery.getPatId() != null) {
            snapshot.setPatId(delivery.getPatId());
        }
        if (delivery.getName() != null) {
            snapshot.setName(delivery.getName());
        }
        if (delivery.getGender() != null) {
            snapshot.setGender(delivery.getGender());
        }
        if (delivery.getPhone() != null) {
            snapshot.setPhone(delivery.getPhone());
        }
        return snapshot;
    }

    // ---------------------------------------------------------------- payload

    /**
     * The delivered archive fields of one payload - exactly the four archive fields the
     * registration adapter maps into the snapshot shape (the payload is the archive
     * row's full bean map; every other key is deliberately ignored).
     */
    private PatientSnapshot archiveDelivery(JSONObject payload) {
        PatientSnapshot delivery = new PatientSnapshot();
        delivery.setPatId(payload.getStr("patId"));
        delivery.setName(payload.getStr("name"));
        delivery.setGender(payload.getInt("gender"));
        delivery.setPhone(payload.getStr("phone"));
        return delivery;
    }

    private boolean hasArchiveField(PatientSnapshot delivery) {
        return delivery.getPatId() != null || delivery.getName() != null
                || delivery.getGender() != null || delivery.getPhone() != null;
    }

    /** The raw jsonb payload document - malformed payloads poison their entry. */
    private JSONObject parsePayload(PatientOutboxEntry entry) {
        if (StrUtil.isBlank(entry.getPayload())) {
            throw new IllegalStateException("outbox entry " + entry.getId()
                    + " carries an empty payload");
        }
        try {
            return JSONUtil.parseObj(entry.getPayload());
        } catch (Exception malformed) {
            throw new IllegalStateException("outbox entry " + entry.getId()
                    + " carries an unparseable payload", malformed);
        }
    }

    /** The linkage uid every dispatch needs - its absence poisons the entry. */
    private Integer requiredUid(JSONObject payload, String key) {
        Integer uid = payload.getInt(key);
        if (uid == null) {
            throw new IllegalStateException("outbox payload lacks '" + key
                    + "' - the entry cannot be dispatched");
        }
        return uid;
    }

    /** The batch cap: the configured limit, clamped back to 50 when non-positive. */
    private int effectiveLimit() {
        return pollLimit <= 0 ? DEFAULT_PULL_LIMIT : pollLimit;
    }
}
