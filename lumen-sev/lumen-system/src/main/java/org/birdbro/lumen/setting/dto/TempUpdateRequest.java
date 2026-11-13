package org.birdbro.lumen.setting.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Template-node full-replace update request ({@code PUT
 * /api/v1/setting/rep-temps/{id}}, exam-setting-api). Scope/ownership are NOT editable
 * through this surface - the update keeps the stored type/ownerUid (moving a private
 * row between users would defeat the enforcement; re-parenting via upId is allowed).
 * D9 null-aware on the content fields.
 *
 * @author birdbro
 */
@Data
public class TempUpdateRequest {

    @ApiModelProperty("检查类型 (dict 1027 value, required)")
    @NotBlank(message = "modality 不能为空")
    private String modality;

    @ApiModelProperty("节点类型 (0 目录 / 1 模板, required)")
    @NotNull(message = "flag 不能为空")
    private Integer flag;

    @ApiModelProperty("上级节点 (optional - re-parenting is allowed)")
    private Integer upId;

    @ApiModelProperty("名称 (required)")
    @NotBlank(message = "name 不能为空")
    private String name;

    @ApiModelProperty("影像学表现 (absent clears)")
    private String finding;

    @ApiModelProperty("影像学意见 (absent clears)")
    private String impression;
}
