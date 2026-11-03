package org.birdbro.framework.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;
import org.birdbro.common.core.base.EntityBase;

/**
 * <p>
 * 字典数据
 * </p>
 *
 * @author birdbro
 * @since 2024-06-04
 */
@TableName("dict_detail")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class DictDetailEntity extends EntityBase {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("PK")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @ApiModelProperty("启用 1(def)  禁用 0 ")
    @TableField("enable")
    private Integer enable;

    @ApiModelProperty("目录ID")
    @TableField("ca_id")
    private Integer caId;

    @TableField("ca_code")
    private String caCode;

    @ApiModelProperty("编码")
    @TableField("code")
    private Integer code;

    @ApiModelProperty("值")
    @TableField("value")
    private String value;

    @ApiModelProperty("标签")
    @TableField("label")
    private String label;

    @ApiModelProperty("备用值1")
    @TableField("back_1")
    private String back1;

    @ApiModelProperty("备用值2")
    @TableField("back_2")
    private String back2;

    @ApiModelProperty("排序")
    @TableField("sort")
    private Integer sort;

    @ApiModelProperty("颜色")
    @TableField("color")
    private String color;

    @ApiModelProperty("样式")
    @TableField("css")
    private String css;

    @ApiModelProperty("备注")
    @TableField("remark")
    private String remark;

    @ApiModelProperty("首字母")
    @TableField("initial")
    private String initial;
}
