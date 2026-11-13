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
 * ExamMethod ({@code exam_method}, exam-setting-api): a 检查方法 - one modality's
 * examination technique vocabulary (e.g. 平扫/增强 for CT). Items bind methods through
 * the {@code exam_item_method} join table - RCP's comma-joined id string plus its
 * denormalized methodValue name copy are NOT ported (coordination exam-setting.md).
 *
 * @author birdbro
 */
@TableName("exam_method")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ExamMethod extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 检查类型 (dict 1027 value). */
    private String modality;

    /** 方法名称. */
    private String name;

    /** 方法编码, unique within the modality among live rows when present. */
    private String code;

    /** 排序. */
    private Integer sort;

    /** 启用 (dict 1000 common_status codes). */
    private Integer enable;
}
