package org.birdbro.lumen.system.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author birdbro
 * @date 14:11 2023-03-27
 **/
@Data
@EqualsAndHashCode(callSuper = false)
public class DetailModify extends DetailCreate {

    @ApiModelProperty("PK")
    private Integer id;
}
