package org.birdbro.lumen.manage.dto;

import javax.validation.constraints.NotBlank;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Login request body ({@code POST /api/v1/system/login}, auth-rbac-foundation task 3.1).
 * Carries the raw credentials from the client; the password stays raw inside the request
 * scope only - the BCrypt hash is what the service compares against and what the
 * database stores.
 *
 * @author birdbro
 */
@Data
public class LoginRequest {

    @NotBlank(message = "username must not be blank")
    @ApiModelProperty("Login name")
    private String username;

    @NotBlank(message = "password must not be blank")
    @ApiModelProperty("Raw password")
    private String password;
}
