package org.birdbro.lumen.patient.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Number-rule update request ({@code PUT /api/v1/patient/patient-number-rules/{id}},
 * patient-domain task 4.1): the same surface as create (the web form is full-field;
 * the service copies non-null fields, so the caller may omit optionals to keep them).
 * {@code serial_cur}/{@code stamp_value} are RUNTIME STATE - no request field exists,
 * the CRUD path can never move them.
 *
 * @author birdbro
 */
@Data
public class PatientNumberRuleUpdateRequest {

    @ApiModelProperty("规则类型（'pat_id' 今天）")
    @NotBlank(message = "ruleType 不能为空")
    private String ruleType;

    @ApiModelProperty("号码前缀（可选，≤8 位字母数字）")
    @Pattern(regexp = "[A-Za-z0-9]{0,8}", message = "prefix 须为不超过 8 位的字母数字")
    private String prefix;

    @ApiModelProperty("号码后缀（可选，≤8 位字母数字）")
    @Pattern(regexp = "[A-Za-z0-9]{0,8}", message = "suffix 须为不超过 8 位的字母数字")
    private String suffix;

    @ApiModelProperty("流水位数（1-10，左补零）")
    @NotNull(message = "serialLength 不能为空")
    @Min(value = 1, message = "serialLength 不能小于 1")
    @Max(value = 10, message = "serialLength 不能大于 10")
    private Integer serialLength;

    @ApiModelProperty("是否按日期戳周期重置 0 否 / 1 是")
    @NotNull(message = "isStamp 不能为空")
    private Integer isStamp;

    @ApiModelProperty("日期戳格式（Java 日期模式，可选；isStamp=1 且缺省时默认 yyMMdd）")
    private String stamp;

    @ApiModelProperty("启用 1 / 停用 0（同类型唯一启用行，冲突拒绝 A0714）")
    @NotNull(message = "active 不能为空")
    private Integer active;
}
