package org.birdbro.lumen.setting.dto.examsetting;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Method create request ({@code POST /api/v1/setting/exam-methods},
 * exam-setting-api). The code is optional; when present it is unique within the
 * modality among live rows (service-checked, partial unique index backstop).
 *
 * @author birdbro
 */
@Data
public class MethodCreateRequest {

    @ApiModelProperty("检查类型 (dict 1027 value, required)")
    @NotBlank(message = "modality 不能为空")
    private String modality;

    @ApiModelProperty("方法名称 (required)")
    @NotBlank(message = "name 不能为空")
    private String name;

    @ApiModelProperty("方法编码 (optional)")
    private String code;

    @ApiModelProperty("排序")
    private Integer sort;

    @ApiModelProperty("启用 (dict 1000 codes, required)")
    @NotNull(message = "enable 不能为空")
    private Integer enable;
}
