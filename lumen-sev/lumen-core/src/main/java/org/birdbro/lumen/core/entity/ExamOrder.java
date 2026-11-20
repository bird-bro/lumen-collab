package org.birdbro.lumen.core.entity;

import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import org.birdbro.common.core.base.EntityBase;
import org.birdbro.lumen.core.mapper.ExamItemRefListJsonbTypeHandler;
import org.birdbro.lumen.core.mapper.KernelJsonbTypeHandler;

/**
 * The order DOCUMENT - the aggregate root (spec exam-workflow-kernel "Exam-Order
 * Aggregate"; the anti-god-table split of EIS's 78-field order_info: 编号段/状态段/
 * 申请段/取消段 live here, exam execution and the report are separate 1:1 members, and
 * the patient reference rides {@code patient_uid} NOT NULL + the jsonb snapshot).
 * Department-specific fields are deliberately absent (they belong to department
 * services or their extension columns).
 *
 * @author birdbro
 */
@TableName(value = "exam_order", autoResultMap = true)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExamOrder extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 检查号 accession (kernel-issued through the exam-side numbering issuer). */
    private String accessionNo;

    /** 预约号 (walk-in orders carry none). */
    private String bookingNo;

    /** 申请单号 (HIS or manual). */
    private String applyNo;

    /** HIS 侧单据标识. */
    private String hisId;

    /** 申请渠道 (LOCAL default; REMOTE reserved, not yet enabled). */
    private ExamChannel channel;

    /** 文档状态机状态 (written ONLY through the state-machine transition service). */
    private ExamOrderStatus status;

    /** Patient reference - the patient domain's pat_uid, NOT NULL (decision #3). */
    private Integer patientUid;

    /** jsonb 患者快照 (decision #4; refreshed only while the report is not issued). */
    @TableField(typeHandler = KernelJsonbTypeHandler.class)
    private PatientSnapshot patientSnapshot;

    /**
     * 检查项目引用+名称快照 (kernel holds references + snapshots, not the catalog).
     * Reads go through the TYPED list handler (the raw {@code List.class} parse loses
     * the element type and hands back maps - the modality-scope derivation is the
     * kernel's first typed element accessor).
     */
    @TableField(typeHandler = ExamItemRefListJsonbTypeHandler.class)
    private List<ExamItemRef> examItems;

    /** 急诊标志 (display/notification only, the EIS semantics). */
    private Integer emgFlag;

    /** 取消原因 (cancel branches). */
    private String cancelReason;
}
