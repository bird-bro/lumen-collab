package org.birdbro.lumen.setting.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Body full-replace update request ({@code PUT /api/v1/setting/exam-bodies/{id}},
 * exam-setting-api). Same surface as create; the service applies it D9 null-aware -
 * an absent {@code code}/{@code sort} CLEARS the column (never the null-skipping
 * updateById).
 *
 * @author birdbro
 */
@Data
public class BodyUpdateRequest {

    @ApiModelProperty("检查类型 (dict 1027 value, must match the group's)")
    @NotBlank(message = "modality 不能为空")
    private String modality;

    @ApiModelProperty("所属部位分组 (required)")
    @NotNull(message = "groupId 不能为空")
    private Integer groupId;

    @ApiModelProperty("上级部位 (optional, must belong to the same group)")
    private Integer upId;

    @ApiModelProperty("部位名称 (required)")
    @NotBlank(message = "name 不能为空")
    private String name;

    @ApiModelProperty("部位编码 (absent clears)")
    private String code;

    @ApiModelProperty("排序 (absent clears)")
    private Integer sort;

    @ApiModelProperty("启用 (dict 1000 codes, required)")
    @NotNull(message = "enable 不能为空")
    private Integer enable;
}
