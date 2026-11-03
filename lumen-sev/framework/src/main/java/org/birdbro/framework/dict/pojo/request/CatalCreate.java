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
public class CatalCreate {

    @ApiModelProperty("启用 1(def)  禁用 0 ")
    private Byte enable;

    @ApiModelProperty("编码")
    private String code;

    @ApiModelProperty("索引名称")
    private String name;

    @ApiModelProperty("排序")
    private Integer sort;

    @ApiModelProperty("备注")
    private String remark;

    @ApiModelProperty("分类")
    private Integer cls;

    @ApiModelProperty("首字母")
    private String initial;

}
