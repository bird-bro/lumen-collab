package org.birdbro.lumen.system.dto;

import java.util.List;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * User profile detail ({@code GET /api/v1/system/users/{id}}, user-edit-delete-profile-api
 * task 2.2) - the edit dialog's echo payload. Same no-leak guarantee as
 * {@link UserItemDto}: the entity's {@code password} hash has no property to bind to.
 * {@code roleIds} (not roleNames): the edit dialog submits ids back through the
 * update's full-replace.
 *
 * @author birdbro
 */
@Data
public class UserDetailDto {

    @ApiModelProperty("UID (user_info.id)")
    private Integer uid;

    @ApiModelProperty("Login name (immutable through the update)")
    private String username;

    @ApiModelProperty("Display name")
    private String name;

    @ApiModelProperty("Phone number")
    private String phone;

    @ApiModelProperty("Email")
    private String email;

    @ApiModelProperty("Dict 性别 catalog code")
    private Integer gender;

    @ApiModelProperty("Dict 职称 catalog code")
    private Integer title;

    @ApiModelProperty("Owning department (org_dep.id)")
    private Integer depId;

    @ApiModelProperty("Exam-type visibility, CSV of dict 检查类型 codes")
    private String modalitys;

    @ApiModelProperty("Report-workflow authority tier (dict 审核权限 code)")
    private Integer repAudit;

    @ApiModelProperty("Report-signature image path")
    private String signature;

    @ApiModelProperty("The bound role ids (the edit dialog's full-replace source)")
    private List<Integer> roleIds;
}
