package org.birdbro.lumen.manage.dto;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * User list item ({@code GET /api/v1/system/users/list}, auth-rbac-foundation task 4.3).
 * Deliberately NOT the {@code user_info} entity: the entity's {@code password} column
 * holds the BCrypt hash, and a DTO with an explicit field set is the only guarantee it
 * NEVER serializes (task 4.3 binding "no entity leakage", lumen-sev coding standard).
 *
 * @author birdbro
 */
@Data
public class UserItemDto {

    @ApiModelProperty("UID (user_info.id)")
    private Integer uid;

    @ApiModelProperty("Login name")
    private String username;

    @ApiModelProperty("Display name")
    private String name;

    @ApiModelProperty("Phone number (V26 profile column)")
    private String phone;

    @ApiModelProperty("Email (V26 profile column)")
    private String email;

    @ApiModelProperty("Dict 性别 catalog code (V26 profile column)")
    private Integer gender;

    @ApiModelProperty("Dict 职称 catalog code (V26 profile column)")
    private Integer title;

    @ApiModelProperty("Owning department id (V26 profile column)")
    private Integer depId;

    @ApiModelProperty("Department display name, batch-resolved (null when unbound/dead)")
    private String depName;

    @ApiModelProperty("Exam-type visibility, CSV of dict 检查类型 codes (V26)")
    private String modalitys;

    @ApiModelProperty("Report-workflow authority tier, dict 审核权限 code (V26)")
    private Integer repAudit;

    @ApiModelProperty("Report-signature image path (V26)")
    private String signature;

    @ApiModelProperty("The bound role display names, batch-resolved (empty when unbound)")
    private List<String> roleNames;

    @ApiModelProperty("Enable flag: 1 = active, 0 = disabled")
    private Integer enable;

    @ApiModelProperty("1 = the user must change the password at next login")
    private Integer mustChangePassword;

    @ApiModelProperty("Audit creator UID (user_info.create_by)")
    private Integer createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty("Row creation time")
    private Date createTime;
}
