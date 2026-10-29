package org.birdbro.common.core.pojo.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.List;

/**
 * @author birdbro
 * @date 16:05 2024-05-24
 **/
@Data
@EqualsAndHashCode(callSuper = false)
public class UserSign {

    @ApiModelProperty("UID")
    private Integer uid;

    @ApiModelProperty("启用 1(def) 禁用 0 ")
    private Integer enable;

    @ApiModelProperty("账号")
    private String account;

    @ApiModelProperty("密码")
    private String password;

    @ApiModelProperty("首字母")
    private String initial;

    @ApiModelProperty("姓名")
    private String name;

    @ApiModelProperty("邮箱")
    private String email;

    @ApiModelProperty("手机号码")
    private String phone;

    @ApiModelProperty("头像")
    private String icon;

    @ApiModelProperty("性别 0女 1男")
    private Integer gender;

    @ApiModelProperty("生日")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date birthday;

    @ApiModelProperty("签名")
    private String signature;

    @ApiModelProperty("职称")
    private Integer title;

    private String titleValue;

    @ApiModelProperty("职务")
    private Integer job;

    @ApiModelProperty("参加工作时间")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date joinWork;

    @ApiModelProperty("擅长")
    private String speciality;

    @ApiModelProperty("个人简介")
    private String intro;


    /**
     * 用户角色
     **/
    private List<UserRole> roles;


    /**
     * 入职信息
     */
    private UserEntry entry;


    /**
     * 用户菜单
     */
    private List<MenuTree> menus;
}
