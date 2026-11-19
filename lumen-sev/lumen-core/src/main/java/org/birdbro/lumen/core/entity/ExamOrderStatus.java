package org.birdbro.lumen.core.entity;

/**
 * The document state machine's states (spec exam-workflow-kernel "Document State
 * Machine"; EIS OrderStatusEnum semantics, fresh lumen names - the EIS numeric dict
 * codes 104201/101301... are EIS's own and are NOT copied). Transitions live in the
 * transition table of the state-machine service (task 3.1); the status column is
 * written ONLY through that service.
 *
 * <p>Legal edges: BOOKED &rarr; REGISTERED (签到) &rarr; IN_PROGRESS &rarr; COMPLETED;
 * branches BOOKED &rarr; BOOKING_CANCELLED and REGISTERED/IN_PROGRESS &rarr; CANCELLED.
 * Entry: booking creates BOOKED; walk-in registration and HIS two-phase confirm enter
 * REGISTERED directly.</p>
 *
 * @author birdbro
 */
public enum ExamOrderStatus {
    /** 已预约 (booking entry state). */
    BOOKED,
    /** 已登记 (签到/walk-in/HIS-confirm entry state). */
    REGISTERED,
    /** 处理中 (exam in progress). */
    IN_PROGRESS,
    /** 已完成. */
    COMPLETED,
    /** 取消预约 (cancel branch from BOOKED). */
    BOOKING_CANCELLED,
    /** 已取消 (cancel branch from REGISTERED or IN_PROGRESS). */
    CANCELLED
}
