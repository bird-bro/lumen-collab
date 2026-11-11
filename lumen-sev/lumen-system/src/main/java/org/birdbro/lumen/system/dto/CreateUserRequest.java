package org.birdbro.lumen.system.dto;

import java.util.List;

import javax.validation.constraints.NotBlank;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Create-user request body ({@code POST /api/v1/system/users}, auth-rbac-foundation
 * tasks 3.3 / 4.3 / 4.4). Tasks 4.3 extended the Batch 3 minimal create with the
 * service-level duplicate rejection; task 4.4 added the optional {@code roleIds} - the
 * RECORDED user-role creation path (no separate user-role assignment endpoint exists in
 * this change): every id must reference a live role or the create is rejected with
 * {@code A0400}, and the bindings insert in the same transaction as the user row.
 *
 * @author birdbro
 */
@Data
public class CreateUserRequest {

    @NotBlank(message = "username must not be blank")
    @ApiModelProperty("Login name, unique among live rows")
    private String username;

    @NotBlank(message = "password must not be blank")
    @ApiModelProperty("Raw password (stored as the BCrypt hash)")
    private String password;

    @NotBlank(message = "name must not be blank")
    @ApiModelProperty("Display name")
    private String name;

    @ApiModelProperty("Optional roles to bind at creation (each must exist; inserted as user_role rows)")
    private List<Integer> roleIds;

    // ===================== the V26 profile fields (user-edit-delete-profile-api) =====================
    // The console's one-drawer create submits the full profile form in one call -
    // BeanUtil.copyProperties(request, user, true) binds these 1:1 onto the row; a
    // dead depId rejects A0400 service-side (the same rule the update path enforces).

    @ApiModelProperty("Phone number (V26; absent = NULL)")
    private String phone;

    @ApiModelProperty("Email (V26; absent = NULL)")
    private String email;

    @ApiModelProperty("Dict 性别 catalog code (V26; absent = NULL)")
    private Integer gender;

    @ApiModelProperty("Dict 职称 catalog code (V26; absent = NULL)")
    private Integer title;

    @ApiModelProperty("Owning department org_dep.id (V26; must be live - A0400 on a miss; absent = NULL)")
    private Integer depId;

    @ApiModelProperty("Exam-type visibility, CSV of dict 检查类型 codes (V26; absent = NULL)")
    private String modalitys;

    @ApiModelProperty("Report-workflow authority tier, dict 审核权限 code (V26; absent = NULL)")
    private Integer repAudit;

    @ApiModelProperty("Report-signature image path (V26; absent = NULL)")
    private String signature;
}
