package org.birdbro.lumen.system.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Update-department request body ({@code PUT /api/v1/system/org-deps/{id}},
 * auth-rbac-foundation task 4.2): partial update - every field optional, {@code null}
 * skipped by the MyBatis-Plus NOT-NULL update strategy. A new {@code orgId}
 * re-anchors the department (validated to exist); a new {@code code} is duplicate-checked.
 *
 * @author birdbro
 */
@Data
public class UpdateOrgDepRequest {

    @ApiModelProperty("New owning org id (validated to exist)")
    private Integer orgId;

    @ApiModelProperty("New stable code (duplicate-checked among live rows)")
    private String code;

    @ApiModelProperty("New display name")
    private String name;
}
