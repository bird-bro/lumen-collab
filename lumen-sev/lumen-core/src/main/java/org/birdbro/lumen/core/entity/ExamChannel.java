package org.birdbro.lumen.core.entity;

/**
 * 申请渠道 (spec exam-workflow-kernel "Exam-Order Aggregate" - Channel reserves the
 * remote value): LOCAL is the default local-application flow; REMOTE (远程申请) is the
 * RESERVED, NOT-YET-ENABLED value kept in the value set from v1 for the lumen-remote
 * readiness (patient-domain decision notes) - no kernel creation path sets it yet.
 * Stored as the enum name in the varchar column.
 *
 * @author birdbro
 */
public enum ExamChannel {
    /** 本地申请 (default). */
    LOCAL,
    /** 远程申请 - reserved, not yet enabled (patient decision #6/#9 remote readiness). */
    REMOTE
}
