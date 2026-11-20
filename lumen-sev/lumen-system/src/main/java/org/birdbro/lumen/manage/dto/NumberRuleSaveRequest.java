package org.birdbro.lumen.manage.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The number-rule create/update request body (number-rule-unification-sev task 2.2):
 * the rule family + format parameters. Validation mirrors the console page's
 * field-level rules (serial length bounds, prefix charset) — violations surface on
 * the offending field.
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NumberRuleSaveRequest {

    /** 规则类型（dict 编号类型 1028 的条目值，即号码族机器 token；创建后不可改）. */
    @NotBlank
    @Size(max = 32, message = "规则类型最长 32 个字符")
    private String ruleType;

    /** 适用范围模态（dict 模态 1024 的条目值 token，可空=全局；创建后不可改，V49）. */
    @Size(max = 16, message = "适用范围模态最长 16 个字符")
    private String modality;

    /** 号码前缀（字母/数字/短横线，可空）. */
    @Size(max = 32)
    @Pattern(regexp = "[A-Za-z0-9_-]*", message = "前缀仅限字母/数字/短横线/下划线")
    private String prefix;

    /** 号码后缀（字母/数字/短横线，可空）. */
    @Size(max = 32)
    @Pattern(regexp = "[A-Za-z0-9_-]*", message = "后缀仅限字母/数字/短横线/下划线")
    private String suffix;

    /** 流水位数长（1-12，左补零）. */
    @NotNull
    @Min(1)
    @Max(12)
    private Integer serialLength;

    /** 是否按日期戳日重置 0 否 / 1 是. */
    @NotNull
    @Min(0)
    @Max(1)
    private Integer isStamp;

    /** 日期戳格式（Java 日期模式；is_stamp=1 时必填）. */
    @Size(max = 16)
    private String stamp;

    /** 启用 1 / 停用 0（同类型唯一启用行）. */
    @NotNull
    @Min(0)
    @Max(1)
    private Integer active;

    /** 备注（可选运维备注，V46）. */
    @Size(max = 255, message = "备注最长 255 个字符")
    private String remark;
}
