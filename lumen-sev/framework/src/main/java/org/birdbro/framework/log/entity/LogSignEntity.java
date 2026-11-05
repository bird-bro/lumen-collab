package org.birdbro.framework.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
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
 * @since 2024-11-01
 */
@TableName("log_sign")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class LogSignEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @ApiModelProperty("日期")
    @TableField("date_ym")
    private Integer dateYm;

    @TableField("msg")
    private String msg;

    @TableField("descr")
    private String descr;

    @TableField("type")
    private Integer type;

    @TableField("uid")
    private Integer uid;

    @TableField("account")
    private String account;

    @TableField("action_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date actionTime;

    @TableField("mobile")
    private Boolean mobile;

    @TableField("ip")
    private String ip;

    @TableField("os")
    private String os;

    @TableField("browser")
    private String browser;

    @TableField("engine")
    private String engine;

    @TableField("session")
    private String session;

    /**
     * 会话凭证锚点（拷贝件沿袭）。sign-log-query 设计 D7：登录日志查询路径不 select 此列，
     * 未选列的 null 由 {@code NON_NULL} 从响应 JSON 中整体略去（凭证材料零出现在管理端
     * 响应里）；拷贝 XML 路径的非空 token 值照常序列化，行为不变。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @TableField("token")
    private String token;

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
