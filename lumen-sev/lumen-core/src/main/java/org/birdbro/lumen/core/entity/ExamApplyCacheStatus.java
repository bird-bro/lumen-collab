package org.birdbro.lumen.core.entity;

/**
 * The HIS intake cache row's lifecycle status (spec exam-workflow-kernel "HIS Two-Phase
 * Intake"): CACHED until the MANUAL confirm commits it to a formal order - there is NO
 * server-side auto-commit. COMMITTED rows reject further target corrections.
 *
 * @author birdbro
 */
public enum ExamApplyCacheStatus {
    /** 已缓存，待人工确认转单 (the first phase). */
    CACHED,
    /** 已确认转单 (the terminal cache state - the formal order exists). */
    COMMITTED
}
