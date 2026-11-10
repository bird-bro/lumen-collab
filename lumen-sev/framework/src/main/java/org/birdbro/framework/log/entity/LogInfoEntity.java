package org.birdbro.framework.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 *
 * </p>
 *
 * @author birdbro
 * @since 2024-05-07
 */
@TableName("log_info")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class LogInfoEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @ApiModelProperty("日期")
    @TableField("date_ym")
    private Integer dateYm;

    @ApiModelProperty("标题")
    @TableField("title")
    private String title;

    @ApiModelProperty("描述")
    @TableField("descr")
    private String descr;

    @ApiModelProperty("类型")
    @TableField("type")
    private Integer type;

    @TableField("uid")
    private Integer uid;

    @ApiModelProperty("账号")
    @TableField("account")
    private String account;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty("操作时间")
    @TableField("action_time")
    private Date actionTime;


    @ApiModelProperty("执行时间")
    @TableField("spent")
    private Integer spent;

    @TableField("method")
    private String method;

    @ApiModelProperty("方法名")
    @TableField("name")
    private String name;

    @ApiModelProperty("包")
    @TableField("pack")
    private String pack;

    @ApiModelProperty("路径")
    @TableField("url")
    private String url;

    @TableField("session")
    private String session;

    @ApiModelProperty("移动端")
    @TableField("mobile")
    private Boolean mobile;

    @TableField("ip")
    private String ip;

    @TableField("os")
    private String os;

    @ApiModelProperty("浏览器")
    @TableField("browser")
    private String browser;

    @ApiModelProperty("引擎")
    @TableField("engine")
    private String engine;

    @ApiModelProperty("0INFO 1ERROR")
    @TableField("record")
    private Integer record;

    @ApiModelProperty("入参")
    @TableField("param")
    private String param;

    @ApiModelProperty("响应")
    @TableField("result")
    private String result;

    @ApiModelProperty("异常")
    @TableField("exception")
    private String exception;

    @TableField("app")
    private String app;

    @JsonIgnore
    @ApiModelProperty("删除：否0(def) 是1")
    @TableField(value = "deleted")
    private Boolean deleted;

    @ApiModelProperty("创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField(value = "create_time")
    private Date createTime;
}
