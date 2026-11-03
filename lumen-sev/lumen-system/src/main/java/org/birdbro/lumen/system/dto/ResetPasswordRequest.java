package org.birdbro.lumen.system.dto;

import javax.validation.constraints.NotBlank;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Admin password-reset request body ({@code PUT /api/v1/system/users/{id}/password},
 * auth-rbac-foundation task 4.3): the raw replacement password - the service stores its
 * BCrypt hash and sets {@code must_change_password = 1} (the user must change it at the
 * next login).
 *
 * @author birdbro
 */
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "newPassword must not be blank")
    @ApiModelProperty("Raw replacement password (stored as the BCrypt hash)")
    private String newPassword;
}
