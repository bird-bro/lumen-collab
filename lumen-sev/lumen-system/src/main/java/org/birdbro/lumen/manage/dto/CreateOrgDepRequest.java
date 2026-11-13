package org.birdbro.lumen.manage.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Create-department request body ({@code POST /api/v1/system/org-deps},
 * org-management-redesign). {@code orgId} must reference a live, enabled campus;
 * {@code code} is the immutable integration identifier unique within that campus.
 *
 * @author birdbro
 */
@Data
public class CreateOrgDepRequest {

    @NotNull(message = "orgId must not be null")
    @ApiModelProperty("Owning campus org id")
    private Integer orgId;

    @NotBlank(message = "code must not be blank")
    @Size(max = 32, message = "code must not exceed 32 characters")
    @ApiModelProperty("Immutable department code, unique within the owning campus")
    private String code;

    @NotBlank(message = "name must not be blank")
    @Size(max = 128, message = "name must not exceed 128 characters")
    @ApiModelProperty("Display name")
    private String name;

    @ApiModelProperty("Lifecycle status: 1 = enabled, 0 = disabled; defaults to enabled")
    private Integer status;
}
