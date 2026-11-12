package org.birdbro.lumen.setting.entity;

import java.math.BigDecimal;

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
 * ExamItem ({@code exam_item}, exam-setting-api): a 检查项目 - the hospital-wide
 * catalog row 开单 consumes. Carries the billing block (price/rate/tag) AND the
 * performance-score weights ({@code score_rep} 诊断得分 / {@code score_aud} 审核得分 -
 * NULL excludes the item from that weighting; user ruling 2026-11-12: in-hospital
 * performance needs them). Anchors on a body ({@code body_id} NOT NULL); the body's
 * group is DERIVED through the join, never denormalized. Methods bind through
 * {@code exam_item_method}. Code unique within the modality among live rows
 * (service-checked, partial unique index backstop).
 *
 * @author birdbro
 */
@TableName("exam_item")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ExamItem extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 检查类型 (dict 1027 value; must match the bound body's modality). */
    private String modality;

    /** 项目名称. */
    private String exam;

    /** 项目编码, unique within the modality among live rows when present. */
    private String code;

    /** 检查部位 (exam_body.id, NOT NULL, convention reference). */
    private Integer bodyId;

    /** 单价（元）. */
    private BigDecimal price;

    /** 折扣 (0-1). */
    private BigDecimal rate;

    /** 诊断计费数量. */
    private Integer tag;

    /** 诊断得分（绩效权重） - NULL excludes the item from that weighting. */
    private Integer scoreRep;

    /** 审核得分（绩效权重） - NULL excludes the item from that weighting. */
    private Integer scoreAud;

    /** 常用项目 (开单页的常用清单标记). */
    private Boolean isCommon;

    /** 排序. */
    private Integer sort;

    /** 启用 (dict 1000 common_status codes). */
    private Integer enable;

    /** 备注. */
    private String remark;
}
