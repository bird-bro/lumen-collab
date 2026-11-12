package org.birdbro.lumen.setting.dto.examsetting;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Body-group create request ({@code POST /api/v1/setting/exam-body-groups},
 * exam-setting-api). The group table carries no code column - the NAME is the
 * operational key, unique within the modality among live rows (service-checked).
 *
 * @author birdbro
 */
@Data
public class BodyGroupCreateRequest {

    @ApiModelProperty("检查类型 (dict 1027 value, required)")
    @NotBlank(message = "modality 不能为空")
    private String modality;

    @ApiModelProperty("分组名称 (required, unique within the modality)")
    @NotBlank(message = "name 不能为空")
    private String name;

    @ApiModelProperty("排序")
    private Integer sort;

    @ApiModelProperty("启用 (dict 1000 codes, required)")
    @NotNull(message = "enable 不能为空")
    private Integer enable;
}
