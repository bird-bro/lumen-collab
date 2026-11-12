package org.birdbro.lumen.setting.entity;

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
 * ExamBodyGroup ({@code exam_body_group}, exam-setting-api): a 部位分组 - the FLAT
 * business classification of exam body parts within one 检查类型 (modality). The
 * 2026-11-12 Option-A split ruling keeps groups in their own table (the business
 * classification) and anatomy in {@code exam_body} (the part, nested by {@code up_id}
 * within a group) - dissolving RCP's upId=100000 synthetic root where one node played
 * both roles. No code column: the name is the operational key, unique within the
 * modality among live rows (service-checked first, partial unique index backstop).
 *
 * @author birdbro
 */
@TableName("exam_body_group")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ExamBodyGroup extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 检查类型 (dict 1027 value - the single platform modality vocabulary). */
    private String modality;

    /** 分组名称, unique within the modality among live rows (service-enforced). */
    private String name;

    /** 排序. */
    private Integer sort;

    /** 启用 (dict 1000 common_status codes). */
    private Integer enable;
}
