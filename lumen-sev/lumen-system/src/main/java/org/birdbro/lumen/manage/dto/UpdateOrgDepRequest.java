package org.birdbro.lumen.manage.dto;

import javax.validation.constraints.Size;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Update-department request body ({@code PUT /api/v1/system/org-deps/{id}},
 * org-management-redesign): a partial update with every legacy field optional. A present
 * {@code code} must equal the stored code; the department code is immutable. A new
 * {@code orgId} re-anchors the department to another live, enabled campus.
 *
 * @author birdbro
 */
@Data
public class UpdateOrgDepRequest {

    @ApiModelProperty("New owning campus org id")
    private Integer orgId;

    @Size(max = 32, message = "code must not exceed 32 characters")
    @ApiModelProperty("Immutable department code; must match the stored value when present")
    private String code;

    @Size(max = 128, message = "name must not exceed 128 characters")
    @ApiModelProperty("Display name")
    private String name;

    @ApiModelProperty("Lifecycle status: 1 = enabled, 0 = disabled")
    private Integer status;
}
