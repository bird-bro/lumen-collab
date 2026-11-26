package org.birdbro.lumen.core.service;

import org.birdbro.lumen.core.entity.ExamOrderStatus;

/**
 * The document state machine's single writer (lumen-core-registration-kernel task 3.1;
 * spec exam-workflow-kernel "Document State Machine"). Every status change of an order
 * goes through {@link #transition}: legal edges advance, branches split by stage, and
 * anything else is refused with the stored status UNCHANGED.
 *
 * <p>Legal edges (the six-state machine, user ruling 2026-11-26): REGISTERED &rarr;
 * IN_EXAM (检查开始 = MPPS IN PROGRESS) &rarr; IN_DIAGNOSIS (进报告页抢占) &rarr;
 * COMPLETED (完成报告) &rarr; PRINTED (打印), each later stage also firing from each
 * earlier live state (forward-permissive), and CANCELLED from REGISTERED / IN_EXAM /
 * IN_DIAGNOSIS. Entry states (REGISTERED from booking 预约=预登记, walk-in, or HIS
 * confirm) are set by the creation entries through the aggregate service, not by
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
