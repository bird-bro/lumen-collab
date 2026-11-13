package org.birdbro.lumen.setting.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Template-node create request ({@code POST /api/v1/setting/rep-temps},
 * exam-setting-api): one tree node - directory (flag=0, content-free) or template
 * (flag=1, finding/impression). The scope rides {@code type}; for type=2 the OWNER
 * BINDING comes from the session, NEVER the request body (the
 * server-side-binding ruling).
 *
 * @author birdbro
 */
@Data
public class TempCreateRequest {

    @ApiModelProperty("可见范围 (1 公共 / 2 个人, required)")
    @NotNull(message = "type 不能为空")
    private Integer type;

    @ApiModelProperty("检查类型 (dict 1027 value, required)")
    @NotBlank(message = "modality 不能为空")
    private String modality;

    @ApiModelProperty("节点类型 (0 目录 / 1 模板, required)")
    @NotNull(message = "flag 不能为空")
    private Integer flag;

    @ApiModelProperty("上级节点 (optional - null roots the tree)")
    private Integer upId;

    @ApiModelProperty("名称 (required)")
    @NotBlank(message = "name 不能为空")
    private String name;

    @ApiModelProperty("影像学表现 (templates only)")
    private String finding;

    @ApiModelProperty("影像学意见 (templates only)")
    private String impression;
}
