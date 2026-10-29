package org.birdbro.common.core.base;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author birdbro
 * @date 13:09 2023-2-15
 **/
@Data
public abstract class EntityBase implements Serializable {

    @JsonIgnore
    @TableLogic
    @ApiModelProperty("删除：否0(def) 是1")
    @TableField(value = "deleted", updateStrategy = FieldStrategy.NOT_NULL)
    private Integer deleted;


    @ApiModelProperty("创建人")
    @TableField(value = "create_by", updateStrategy = FieldStrategy.NOT_NULL, fill = FieldFill.INSERT)
    private Integer createBy;


    @ApiModelProperty("创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField(value = "create_time", updateStrategy = FieldStrategy.NOT_NULL)
    private Date createTime;


    @ApiModelProperty("修改人")
    @TableField(value = "update_by", updateStrategy = FieldStrategy.NOT_NULL, fill = FieldFill.INSERT_UPDATE)
    private Integer updateBy;


    @ApiModelProperty("修改时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField(value = "update_time", updateStrategy = FieldStrategy.NOT_NULL)
    private Date updateTime;
}
