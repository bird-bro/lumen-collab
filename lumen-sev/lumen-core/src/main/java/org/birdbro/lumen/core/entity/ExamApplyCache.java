package org.birdbro.lumen.core.entity;

import java.util.List;
import java.util.Map;

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
import org.birdbro.lumen.core.mapper.KernelJsonbTypeHandler;

/**
 * The HIS two-phase intake cache row ({@code exam_apply_cache}, lumen-core-registration-
 * kernel task 6.1, design D7; EIS 1.4 semantics): phase one stores the RAW inbound
 * payload ({@code source}) beside the dictionary-mapped exam items ({@code target}) and
 * the patient hint fields; phase two (the manual confirm) turns it into a formal order.
 * The HL7v2 protocol layer itself lives in lumen-integration (future change) - this row
 * is the protocol-neutral two-phase contract.
 *
 * @author birdbro
 */
@TableName(value = "exam_apply_cache", autoResultMap = true)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExamApplyCache extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** HIS 侧单据标识. */
    private String hisId;

    /** 申请单号. */
    private String applyNo;

    /** 缓存行状态 (CACHED until the manual confirm commits it). */
    private ExamApplyCacheStatus status;

    /** 原始报文 (raw inbound payload, jsonb). */
    @TableField(typeHandler = KernelJsonbTypeHandler.class)
    private Map<String, Object> source;

    /**
     * 映射后项目集 (jsonb): the exam-setting catalog references + name snapshots. The
     * adapter does the catalog LOOKUP; the intake service structurally DROPS entries
     * without a catalog reference at cache time (the unmatched-item drop rule).
     */
    @TableField(typeHandler = KernelJsonbTypeHandler.class)
    private List<ExamItemRef> target;

    /** 患者提示信息 (confirm 前无身份 - the raw HIS patient fields, jsonb). */
    @TableField(typeHandler = KernelJsonbTypeHandler.class)
    private Map<String, Object> patientHint;
}
