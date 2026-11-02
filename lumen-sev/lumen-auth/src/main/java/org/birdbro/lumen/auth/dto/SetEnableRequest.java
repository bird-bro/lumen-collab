package org.birdbro.lumen.auth.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Enable-flag request body ({@code PUT /api/v1/auth/users/{id}/enable},
 * auth-rbac-foundation task 4.3). The 0/1 range is bean-validated here, so an
 * out-of-range value never reaches the service (the copied handler renders it as the
 * {@code A0400} envelope).
 *
 * @author birdbro
 */
@Data
public class SetEnableRequest {

    @NotNull(message = "enable must not be null")
    @Min(value = 0, message = "enable must be 0 or 1")
    @Max(value = 1, message = "enable must be 0 or 1")
    @ApiModelProperty("1 = enable the user, 0 = disable")
    private Integer enable;
}
