package org.birdbro.lumen.setting.entity;

import javax.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One measurement row of {@link RepTempSection} (design D2): {@code code} is the
 * STABLE anchor the report domain renders/statistics key on (e.g. AO/EF),
 * {@code label} is the display name (主动脉根部内径) - both required; unit is an
 * optional template phrase. {@code normal} is the v2 tagged union
 * ({@link RepTempMeasureNormal} - range/text/null, rep-temp-normal-range 2026-11-19;
 * the former free-text string shape was retired by the V47 one-shot migration).
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RepTempMeasure {

    /** 测量项稳定锚（报告域渲染/统计键,如 AO/EF,必填）. */
    @NotBlank(message = "测量项 code 不能为空")
    private String code;

    /** 显示名（如 主动脉根部内径,必填）. */
    @NotBlank(message = "测量项 label 不能为空")
    private String label;

    /** 单位（可空）. */
    private String unit;

    /** 正常范围 v2 tagged union（range/text/null,可空）. */
    private RepTempMeasureNormal normal;
}
