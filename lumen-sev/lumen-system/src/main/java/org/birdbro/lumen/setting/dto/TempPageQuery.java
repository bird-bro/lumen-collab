package org.birdbro.lumen.setting.dto;

import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.birdbro.framework.config.PageSizeConfig;

/**
 * Template file-list query ({@code GET /api/v1/setting/rep-temps/page},
 * exam-setting-api): the templates (flag=1) under ONE parent node of one scope -
 * the console's right-pane table feed.
 *
 * @author birdbro
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class TempPageQuery extends PageSizeConfig {

    @ApiModelProperty("可见范围 (1 公共 / 2 个人, required)")
    @NotNull(message = "type 不能为空")
    private Integer type;

    @ApiModelProperty("检查类型 (dict 1027 value, required)")
    @NotNull(message = "modality 不能为空")
    private String modality;

    @ApiModelProperty("上级节点 (required - the selected folder)")
    @NotNull(message = "upId 不能为空")
    private Integer upId;

    @ApiModelProperty("名称关键字 (optional)")
    private String name;
}
