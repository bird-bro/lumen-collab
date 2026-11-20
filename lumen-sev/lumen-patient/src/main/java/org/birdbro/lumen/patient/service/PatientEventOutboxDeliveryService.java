package org.birdbro.lumen.patient.service;

import java.util.List;

import org.birdbro.lumen.patient.entity.PatientEventOutbox;

/**
 * Transactional-outbox DELIVERY side (us-station-registration-sev task 2.2, design
 * D4): the consumer-facing half of the outbox that the first consumer change (the US
 * station's snapshot poller) exercises through the patient internal endpoints - pull
 * and ack. Deliberately a SEPARATE top-level service from the append-only
 * {@link PatientEventOutboxService}: that interface stays INSERT-only BY INTERFACE
 * (the append-only reflection pin keeps passing untouched); THIS interface owns the
 * consumer-side status machine the append-only era deliberately deferred ("the first
 * consumer change will own their status machine").
 *
 * <p>Delivery semantics (spec patient-domain "Internal Service Surface"): {@code
 * pullPending} hands out PENDING entries in id order, limit-capped, WITHOUT claiming
 * them - entries remain visible to re-pull until acked (exactly once PER ACK: a failed
 * dispatch that skips the ack leaves the entry PENDING for the next pass, and replay
 * is safe because dispatch is idempotent). {@code ack} flips one row to CONSUMED and
 * KEEPS the row for audit (append-only in the row sense: no delete, no payload
 * change); acking a non-PENDING (or missing) row is an idempotent no-op SUCCESS.</p>
 *
 * @author birdbro
 */
public interface PatientEventOutboxDeliveryService {

    /** The status a row carries until a consumer acks it. */
    String STATUS_PENDING = "PENDING";

    /** The status an acked row keeps for audit (rows are never deleted). */
    String STATUS_CONSUMED = "CONSUMED";

    /** The batch cap used when the consumer does not name one (the D5 poller's size). */
    int DEFAULT_PULL_LIMIT = 50;

    /**
     * Reads the PENDING batch, oldest first, capped - a pure SELECT (no claim, no
     * status change), so pulled entries remain visible to re-pull until acked.
     *
     * @param limit the batch cap; {@code null} or non-positive resolves to
     *        {@link #DEFAULT_PULL_LIMIT}
     * @return the PENDING entries ordered by id ascending
     */
    List<PatientEventOutbox> pullPending(Integer limit);

    /**
     * Flips one row PENDING &rarr; CONSUMED (a guarded single UPDATE:
     * {@code id = ? AND status = PENDING}). Rows are kept for audit; acking an already
     * consumed (or missing) row updates nothing and is an idempotent no-op SUCCESS.
     *
     * @param id the outbox row id
     */
    void ack(Integer id);
}
