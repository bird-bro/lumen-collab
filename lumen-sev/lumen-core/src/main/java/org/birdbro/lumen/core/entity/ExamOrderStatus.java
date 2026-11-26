package org.birdbro.lumen.core.entity;

/**
 * The document state machine's states (spec exam-workflow-kernel "Document State
 * Machine"; the six registration statuses, user ruling 2026-11-26 - the former
 * BOOKED / BOOKING_CANCELLED arrival vocabulary is removed, and the single 处理中
 * state is split into 检查中 / 诊断中). Transitions live in the transition table of
 * the state-machine service; the status column is written ONLY through that service.
 *
 * <p>Legal edges: REGISTERED &rarr; IN_EXAM (检查开始 = MPPS IN PROGRESS) &rarr;
 * IN_DIAGNOSIS (进报告页抢占) &rarr; COMPLETED (完成报告) &rarr; PRINTED (打印);
 * CANCELLED from REGISTERED / IN_EXAM / IN_DIAGNOSIS, refused once the report is 已出.
 * Forward edges stay permissive (every later stage fires from each earlier live
 * state); backward moves and terminal exits do not exist. Entry: booking (预约=预登记,
 * user ruling 2026-11-24), walk-in registration, and HIS two-phase confirm all enter
 * REGISTERED directly.</p>
 *
 * @author birdbro
 */
public enum ExamOrderStatus {
    /** 已登记 (booking/walk-in/HIS-confirm entry state). */
    REGISTERED,
    /** 检查中 (exam being performed; the MPPS IN PROGRESS event advances it). */
    IN_EXAM,
    /** 诊断中 (the report-page preemption lock holds the order). */
    IN_DIAGNOSIS,
    /** 已完成 (report issued). */
    COMPLETED,
    /** 已打印 (an independent machine state - the print action advances it). */
    PRINTED,
    /** 已取消 (the single cancel landing from the three live states). */
    CANCELLED
}
