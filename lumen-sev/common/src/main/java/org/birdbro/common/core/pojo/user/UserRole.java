package org.birdbro.common.core.pojo.user;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户角色
 *
 * @author birdbro
 * @date 10:40 2024-06-19
 **/
@Data
@EqualsAndHashCode(callSuper = false)
public class UserRole {


    private Integer rid;

    private String name;

    @ApiModelProperty("类型 0全局def，1系统")
    private Integer type;

    @ApiModelProperty("标记")
    private Integer tag;

    private String tagValue;


}
