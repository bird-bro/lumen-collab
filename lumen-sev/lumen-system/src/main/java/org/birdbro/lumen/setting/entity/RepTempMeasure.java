package org.birdbro.lumen.setting.entity;

import javax.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One measurement row of {@link RepTempSection} (design D2): {@code label} is
 * the display name (主动脉根部内径, required); {@code code} is OPTIONAL since
 * rep-template-ux-polish (user ruling 2026-11-20 - the printed report's B-form
 * measure table never shows the 项目 code and the UI retired its input; the
 * field rides nullable for legacy rows, API-direct writers and future
 * report-domain statistics anchoring); unit is an optional template phrase.
 * {@code normal} is the v2 tagged union ({@link RepTempMeasureNormal} -
 * range/text/null, rep-temp-normal-range 2026-11-19; the former free-text
 * string shape was retired by the V47 one-shot migration).
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RepTempMeasure {

    /** 测量项可选锚（UI 已下线项目输入,存量保留,API 直写可带,报告域统计备用）. */
    private String code;

    /** 显示名（如 主动脉根部内径,必填）. */
    @NotBlank(message = "测量项 label 不能为空")
    private String label;

    /** 单位（可空）. */
    private String unit;

    /** 正常范围 v2 tagged union（range/text/null,可空）. */
    private RepTempMeasureNormal normal;
}
