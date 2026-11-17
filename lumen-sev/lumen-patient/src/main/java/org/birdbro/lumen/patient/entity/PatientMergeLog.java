package org.birdbro.lumen.patient.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import org.birdbro.common.core.base.EntityBase;

/**
 * Merge audit trail ({@code patient_merge_log}, patient-domain task 2.1): one row per
 * merge decision - {@code merged_id} absorbed into {@code survivor_id}.
 *
 * @author birdbro
 */
@TableName("patient_merge_log")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PatientMergeLog extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 被吸收主索引 id（patient_master.id）. */
    private Integer mergedId;

    /** 存续主索引 id（patient_master.id）. */
    private Integer survivorId;

    /** 合并原因（审计记录）. */
    private String reason;
}
