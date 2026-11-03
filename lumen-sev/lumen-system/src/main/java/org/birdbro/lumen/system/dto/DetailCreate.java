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
public class DetailCreate {


    @ApiModelProperty("启用 1(def)  禁用 0 ")
    private Integer enable;

    @ApiModelProperty("目录ID")
    private Integer caId;

    private String caCode;

    @ApiModelProperty("编码")
    private Integer code;

    @ApiModelProperty("值")
    private String value;

    @ApiModelProperty("标签")
    private String label;

    @ApiModelProperty("备用值1")
    private String back1;

    @ApiModelProperty("备用值2")
    private String back2;

    @ApiModelProperty("排序")
    private Integer sort;

    @ApiModelProperty("颜色")
    private String color;

    @ApiModelProperty("样式")
    private String css;

    @ApiModelProperty("备注")
    private String remark;


}
