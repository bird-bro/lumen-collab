package org.birdbro.framework.dict.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author birdbro
 * @date 14:10 2023-03-27
 **/
@Data
@EqualsAndHashCode(callSuper = false)
public class CatalModify extends CatalCreate {

    @ApiModelProperty("主键id")
    private Integer id;

}
