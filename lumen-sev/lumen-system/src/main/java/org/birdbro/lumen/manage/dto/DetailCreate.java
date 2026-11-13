package org.birdbro.lumen.manage.dto;

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

    @ApiModelProperty("编码")
    private Integer code;

    @ApiModelProperty("值（显示与存储文本）")
    private String value;

    @ApiModelProperty("标签值1")
    private String tag;

    @ApiModelProperty("标记值2")
    private String flag;

    @ApiModelProperty("排序")
    private Integer sort;

    @ApiModelProperty("颜色")
    private String color;

    @ApiModelProperty("样式")
    private String css;

    @ApiModelProperty("备注")
    private String remark;


}
