package org.birdbro.lumen.core.service;

import org.birdbro.lumen.core.entity.ExamOrderStatus;

/**
 * The document state machine's single writer (lumen-core-registration-kernel task 3.1;
 * spec exam-workflow-kernel "Document State Machine"). Every status change of an order
 * goes through {@link #transition}: legal edges advance, branches split by stage, and
 * anything else is refused with the stored status UNCHANGED.
 *
 * <p>Legal edges: BOOKED &rarr; REGISTERED (签到), REGISTERED &rarr; IN_PROGRESS,
 * IN_PROGRESS &rarr; COMPLETED, BOOKED &rarr; BOOKING_CANCELLED, and CANCELLED from
 * REGISTERED or IN_PROGRESS. Entry states (BOOKED from booking; REGISTERED from walk-in
 * or HIS confirm) are set by the creation entries through the aggregate service, not by
 * transitions.</p>
 *
 * @author birdbro
 */
public interface ExamOrderStateMachineService {

    /**
     * Fires one transition on a live order.
     *
     * @param orderId the order id
     * @param target  the target state of a LEGAL edge from the order's current state
     * @param reason  the cancel reason (used only by the cancel branches; nullable)
     * @return the persisted status after the transition
     * @throws org.birdbro.common.exception.BusinessException when the edge is illegal,
     *         the order is missing, or a cancel is refused because the report is issued
     *         (A0400) - the stored status stays unchanged in every refusal case
     */
    ExamOrderStatus transition(Integer orderId, ExamOrderStatus target, String reason);
}
