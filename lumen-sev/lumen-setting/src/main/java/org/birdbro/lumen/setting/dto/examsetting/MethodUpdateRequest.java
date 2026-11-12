package org.birdbro.lumen.setting.dto.examsetting;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Method full-replace update request ({@code PUT /api/v1/setting/exam-methods/{id}},
 * exam-setting-api). Same surface as create; D9 null-aware - absent optional columns
 * CLEAR.
 *
 * @author birdbro
 */
@Data
public class MethodUpdateRequest {

    @ApiModelProperty("检查类型 (dict 1027 value, required)")
    @NotBlank(message = "modality 不能为空")
    private String modality;

    @ApiModelProperty("方法名称 (required)")
    @NotBlank(message = "name 不能为空")
    private String name;

    @ApiModelProperty("方法编码 (absent clears)")
    private String code;

    @ApiModelProperty("排序 (absent clears)")
    private Integer sort;

    @ApiModelProperty("启用 (dict 1000 codes, required)")
    @NotNull(message = "enable 不能为空")
    private Integer enable;
}
