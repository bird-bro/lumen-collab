package org.birdbro.lumen.auth.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Update-org request body ({@code PUT /api/v1/auth/orgs/{id}}, auth-rbac-foundation
 * task 4.1): partial update - every field is optional and a {@code null} is skipped by
 * the MyBatis-Plus NOT-NULL update strategy, so {@code {"name": "..."}} renames only and
 * a new {@code upId} re-parents.
 *
 * @author birdbro
 */
@Data
public class UpdateOrgRequest {

    @ApiModelProperty("New parent id (0 = re-root); validated to exist when non-zero")
    private Integer upId;

    @ApiModelProperty("New depth; pass explicitly when re-parenting")
    private Integer level;

    @ApiModelProperty("New org type")
    private Integer type;

    @ApiModelProperty("New display name")
    private String name;
}
