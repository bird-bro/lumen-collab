package org.birdbro.lumen.setting.dto.examsetting;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Body create request ({@code POST /api/v1/setting/exam-bodies}, exam-setting-api):
 * a body belongs to a group ({@code groupId} NOT NULL) and may nest under a parent
 * of the SAME group ({@code upId}); the modality must match the group's. References
 * are service-enforced (zero physical FKs).
 *
 * @author birdbro
 */
@Data
public class BodyCreateRequest {

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

    @ApiModelProperty("部位编码 (optional)")
    private String code;

    @ApiModelProperty("排序")
    private Integer sort;

    @ApiModelProperty("启用 (dict 1000 codes, required)")
    @NotNull(message = "enable 不能为空")
    private Integer enable;
}
