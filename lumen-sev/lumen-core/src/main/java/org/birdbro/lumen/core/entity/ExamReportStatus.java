package org.birdbro.lumen.core.entity;

/**
 * The report PLACEHOLDER's status (spec exam-workflow-kernel "Exam-Order Aggregate" -
 * the report member carries status + the completion-source seam only). The full report
 * domain (state machine, 审核链 configurable levels, locking, merging, signatures) is a
 * later proposal per user ruling 2026-11-18; DRAFTING/ISSUED is the minimal pair the
 * kernel needs: ISSUED (已出) arms the snapshot-refresh guard and the cancel refusal.
 *
 * @author birdbro
 */
public enum ExamReportStatus {
    /** 书写中 (report not yet issued). */
    DRAFTING,
    /** 已出 (issued - arms the snapshot-refresh guard and the cancel refusal). */
    ISSUED
}
