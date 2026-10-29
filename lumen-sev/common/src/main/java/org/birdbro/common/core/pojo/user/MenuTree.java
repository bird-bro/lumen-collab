package org.birdbro.common.core.pojo.user;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @author birdbro
 * @date 10:29 2024-07-31
 **/
@Data
@EqualsAndHashCode(callSuper = false)
public class MenuTree {

    @ApiModelProperty("主键:1001")
    private Integer mid;

    @ApiModelProperty("标题")
    private String title;

    @ApiModelProperty("父节点")
    private Integer upId;

    @ApiModelProperty("图标")
    private String icon;

    @ApiModelProperty("路径")
    private String path;

    @ApiModelProperty("路由参数")
    private String query;

    @ApiModelProperty("组件")
    private String component;

    @ApiModelProperty("类型: M目录 C菜单 F按钮 I 接口")
    private String type;

    @ApiModelProperty("排序")
    private Integer sort;

    @ApiModelProperty("显示 1 不显示 0(def)")
    private Boolean visible;

    @ApiModelProperty("重定向")
    private String redirect;

    @ApiModelProperty("子系统")
    private Integer app;

    @ApiModelProperty("面包屑")
    private String breadcrumb;

    @ApiModelProperty("动作")
    private String action;

    @ApiModelProperty("缓存 （0否 1是） def0")
    private Boolean isCache;

    @ApiModelProperty("外连接 （0否 1是）def0")
    private Boolean isFrame;

    @ApiModelProperty("权限标识")
    private String perms;

    @ApiModelProperty("备注")
    private String remark;


    private List<MenuTree> children;
}
