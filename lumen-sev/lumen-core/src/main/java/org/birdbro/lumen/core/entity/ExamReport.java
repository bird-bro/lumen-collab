package org.birdbro.lumen.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import org.birdbro.common.core.base.EntityBase;

/**
 * The report PLACEHOLDER member of the aggregate (1:1 with the order document): status
 * + the pluggable completion-source seam ONLY (patient-domain decision #9). The report
 * DOMAIN - the writing flow, the configurable 审核链 (user ruling 2026-11-18: 0 级=
 * 提交即签 / 1 级=初审 / 2 级=初审+终审), locking, merging, signatures - is a later
 * proposal; this member carries just what the kernel guards need (ISSUED arms the
 * snapshot-refresh guard and the cancel refusal).
 *
 * @author birdbro
 */
@TableName("exam_report")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExamReport extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** The owning order id (1:1). */
    private Integer orderId;

    /** Report status (DRAFTING default; ISSUED arms the kernel guards). */
    private ExamReportStatus status;

    /** Completion-source seam (LOCAL writing flow / REMOTE return flow). */
    private ReportCompletionSource completionSource;
}
