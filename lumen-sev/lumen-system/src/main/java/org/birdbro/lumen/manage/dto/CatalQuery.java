package org.birdbro.lumen.manage.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.birdbro.framework.config.PageSizeConfig;

/**
 * @author birdbro
 * @date 8:56 2024-08-12
 **/
@Data
@EqualsAndHashCode(callSuper = false)
public class CatalQuery extends PageSizeConfig {

    @ApiModelProperty("索引名称")
    private String name;

    @ApiModelProperty("分类")
    private Integer cls;
}
