package org.birdbro.lumen.auth.dto;

import javax.validation.constraints.NotBlank;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Create-user request body ({@code POST /api/v1/auth/users}, auth-rbac-foundation task
 * 3.3): the minimal write endpoint that proves the audit identity loop - the JWT
 * principal must reach {@code MetaObjectConfig} through the USER-HEADER bridge so the
 * row's {@code create_by} fills. Full user CRUD (list, duplicate rejection, password
 * change, enable flag) lands in task 4.3.
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
}
