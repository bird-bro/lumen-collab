package org.birdbro.lumen.setting.entity;

import javax.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One measurement row of {@link RepTempSection} (design D2): {@code code} is the
 * STABLE anchor the report domain renders/statistics key on (e.g. AO/EF),
 * {@code label} is the display name (主动脉根部内径) - both required; unit/normal
 * are optional template phrases.
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

    /** 正常范围（可空）. */
    private String normal;
}
