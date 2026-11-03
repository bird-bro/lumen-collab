package org.birdbro.lumen.system.dto;

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
public class DetailQuery extends PageSizeConfig {

    @ApiModelProperty("目录ID")
    private Integer caId;

    @ApiModelProperty("值")
    private String value;
}
