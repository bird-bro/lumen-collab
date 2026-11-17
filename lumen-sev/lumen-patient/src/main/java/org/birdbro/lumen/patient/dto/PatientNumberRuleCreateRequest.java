package org.birdbro.lumen.patient.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Number-rule create request ({@code POST /api/v1/patient/patient-number-rules},
 * patient-domain task 4.1). The rule's FORMAT parameters are plain data (changing the
 * number format never requires a code change); {@code serial_cur} is RUNTIME STATE and
 * deliberately has no request field anywhere in the CRUD family. Validation violations
 * reject A0400 with the field message surfaced (the web form shows them on the field,
 * task 5); the active-rule uniqueness conflict is a service-level A0714.
 *
 * @author birdbro
 */
@Data
public class PatientNumberRuleCreateRequest {

    @ApiModelProperty("规则类型（'pat_id' 今天；未来号码家族同模式复用）")
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
