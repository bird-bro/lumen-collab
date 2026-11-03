package org.birdbro.lumen.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;
import org.birdbro.common.core.base.EntityBase;

/**
 * <p>
 *
 * </p>
 *
 * @author birdbro
 * @since 2024-05-07
 */

@TableName("dict_catal")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class DictCatalEntity extends EntityBase {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @ApiModelProperty("启用 1(def)  禁用 0 ")
    @TableField("enable")
    private Integer enable;

    @ApiModelProperty("编码")
    @TableField("code")
    private String code;

    @ApiModelProperty("名称")
    @TableField("name")
    private String name;

    @ApiModelProperty("排序")
    @TableField("sort")
    private Integer sort;

    @TableField("remark")
    private String remark;

    @ApiModelProperty("分类")
    @TableField("cls")
    private Integer cls;

    @ApiModelProperty("首字母")
    @TableField("initial")
    private String initial;
}
