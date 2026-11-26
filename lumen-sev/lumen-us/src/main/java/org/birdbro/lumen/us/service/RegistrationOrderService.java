package org.birdbro.lumen.us.service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import org.birdbro.lumen.feign.patient.PatientLookupResult;
import org.birdbro.lumen.feign.patient.PatientResolveOrRegisterRequest;
import org.birdbro.lumen.core.entity.ExamOrderStatus;
import org.birdbro.lumen.us.dto.RegistrationCreateRequest;
import org.birdbro.lumen.us.dto.RegistrationCreateResult;
import org.birdbro.lumen.us.dto.RegistrationOrderPageQuery;
import org.birdbro.lumen.us.dto.RegistrationOrderView;
import org.birdbro.lumen.us.dto.SlotOptionsView;

/**
 * The US registration business service (us-station-registration-sev task 3.2, design
 * D6/D7): the thin HTTP face's backend - create rides the kernel registration entry
 * (identity from the patient-registry port, accession issued INSIDE the creation path
 * through the number-issuer port, the ext block riding the kernel {@code
 * RegistrationHook} seam), query/state transitions stay kernel-owned (the state-machine
 * transition service is the status column's single writer - its refusals surface
 * verbatim).
 *
 * <p>Layering (naming.md): business service = interface + {@code impl/} implementation;
 * the controller stays thin.</p>
 *
 * @author birdbro
 */
public interface RegistrationOrderService {

    /**
     * The LOOKUP-ONLY resolve passthrough (task 3.2; specs/us-station "Patient Registry
     * Adapter / US-side resolve passthrough"): proxies the demographics to the patient
     * domain's lookup endpoint through the adapter - exact identity | candidate list |
     * empty, NO side effect anywhere. Deliberately NOT the kernel port (that seam is
     * resolve-or-register by kernel contract; the kernel stays untouched).
     *
     * @param query the resolve keys (the contract lookup form; the provisional flag is
     *        ignored - no fallback archive exists on this path)
     * @return the lookup outcome (MATCHED / CANDIDATES / NONE)
     */
    PatientLookupResult resolveLookup(PatientResolveOrRegisterRequest query);

    /**
     * Registers one BOOKED order (task 3.2; specs/us-station "Create a booked order"):
     * resolves-or-registers the patient through the adapter (an incomplete port result -
     * ambiguous candidates, missing identity, missing patUid - refuses BEFORE any write),
     * builds the exam-time snapshot (the delivered archive fields + the body's exam-time
     * demographics), and persists the aggregate through the kernel registration entry -
     * which issues the accession through the number-issuer port inside the creation
     * transaction and fires the ext hook. The ext block stashes thread-locally for the
     * hook and is cleared in a finally (a refused registration never leaks it).
     *
     * @param request the create body (identity-or-demographics + exam items + optional
     *        ext block + optional 急诊 flag)
     * @return the persisted order id + issued accession + entry status
     */
    RegistrationCreateResult create(RegistrationCreateRequest request);

    /**
     * The booking slot-options read (us-booking-slot-picker; specs/us-station
     * "Pre-registration and Slot Capacity"): renders the modality's live slot config
     * as the DATE's granularity grid with LIVE occupancy (the create path's count
     * predicate) so the workbench's 预约时段 select offers the configured slots and
     * grays the full ones (the prototype's 满员标灰). Read-only - no config-row lock
     * (the create path's FOR UPDATE serialization stays create-side); an unconfigured
     * modality answers configured=false (fail-closed at create, the picker disables).
     *
     * @param date     the booking date (yyyy-MM-dd, required)
     * @param modality the dict-1024 modality token (required)
     * @return the configured flag + the day's slot grid with booked/capacity
     */
    SlotOptionsView slotOptions(String date, String modality);

    /**
     * Pages the orders (task 3.2; specs/us-station "Order List") with the 1:1 US
     * extension fields flattened in - the query path carries the extension without
     * kernel changes (the ext rows batch-load against the page's order ids).
     *
     * @param query the pagination + filters
     * @return the paged view rows (id desc - today's work first)
     */
    IPage<RegistrationOrderView> page(RegistrationOrderPageQuery query);

    /**
     * The per-status counts for the workbench's status chips (us-registration-redesign-api
     * design D5; EIS ReportStatusCheckbox semantics): the SAME filter set as
     * {@link #page} MINUS the status condition, counted per document state machine
     * status and ZERO-FILLED across all six states — the chips always render the full
     * desk meter.
     *
     * @param query the wide filter set (the status field is deliberately ignored here)
     * @return six entries keyed by state-machine status name, zero-filled
     */
    java.util.Map<String, Long> statusCounts(RegistrationOrderPageQuery query);

    /**
     * Cancels a live order with a reason (specs/us-station "Cancel carries a reason"):
     * the six-state machine has ONE cancel landing — 已取消 — reachable from
     * 已登记/检查中/诊断中 (user ruling 2026-11-26; the former BOOKED &rarr;
     * BOOKING_CANCELLED branch is removed), and the kernel state machine refuses the
     * illegal shapes verbatim (a cancelled order re-cancelling, a report-issued cancel).
     *
     * @param orderId the order id
     * @param reason the cancel reason (stored on the cancel branch)
     * @return the persisted status after the transition
     */
    ExamOrderStatus cancel(Integer orderId, String reason);
}
