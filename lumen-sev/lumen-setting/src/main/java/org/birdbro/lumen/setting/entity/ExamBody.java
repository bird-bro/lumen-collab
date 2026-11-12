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
 * ExamBody ({@code exam_body}, exam-setting-api): a 检查部位 - the anatomy concept,
 * anchored to a body group ({@code group_id}, NOT NULL) and optionally nested within
 * that group by {@code up_id} (胸部 → 肺/纵隔). Convention references only - zero
 * physical FKs, both service-enforced (the group and the parent must exist, stay live,
 * and belong to the same modality/group).
 *
 * @author birdbro
 */
@TableName("exam_body")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ExamBody extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 检查类型 (dict 1027 value; must match the group's modality). */
    private String modality;

    /** 所属部位分组 (exam_body_group.id, NOT NULL, convention reference). */
    private Integer groupId;

    /** 上级部位 (exam_body.id, nullable - nesting inside the group is optional). */
    private Integer upId;

    /** 部位名称. */
    private String name;

    /** 部位编码 (optional label). */
    private String code;

    /** 排序. */
    private Integer sort;

    /** 启用 (dict 1000 common_status codes). */
    private Integer enable;
}
