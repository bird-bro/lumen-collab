package org.birdbro.lumen.auth.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Update-role request body ({@code PUT /api/v1/auth/roles/{id}}, auth-rbac-foundation
 * task 4.4): partial update - every field optional, {@code null} skipped by the
 * MyBatis-Plus NOT-NULL update strategy. A new {@code name} is duplicate-checked among
 * live rows (excluding the updated role itself).
 *
 * @author birdbro
 */
@Data
public class UpdateRoleRequest {

    @ApiModelProperty("New role name (duplicate-checked among live rows)")
    private String name;

    @ApiModelProperty("New role type: 10 system / 11 business")
    private Integer type;

    @ApiModelProperty("New free-text remark")
    private String remark;
}
