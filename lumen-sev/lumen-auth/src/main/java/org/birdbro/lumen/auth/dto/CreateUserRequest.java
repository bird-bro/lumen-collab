package org.birdbro.lumen.auth.dto;

import java.util.List;

import javax.validation.constraints.NotBlank;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Create-user request body ({@code POST /api/v1/auth/users}, auth-rbac-foundation
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
}
