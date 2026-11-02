package org.birdbro.lumen.auth.dto;

import javax.validation.constraints.NotBlank;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Create-role request body ({@code POST /api/v1/auth/roles}, auth-rbac-foundation task
 * 4.4). {@code type} follows the copied {@code RoleConstants} lineage (10 system /
 * 11 business) and falls to the V1 column default 11 when omitted. {@code name} is
 * unique among live rows (service check + partial unique index backstop).
 *
 * @author birdbro
 */
@Data
public class CreateRoleRequest {

    @NotBlank(message = "name must not be blank")
    @ApiModelProperty("Role name, unique among live rows")
    private String name;

    @ApiModelProperty("Role type: 10 system / 11 business (defaults to 11)")
    private Integer type;

    @ApiModelProperty("Free-text remark")
    private String remark;
}
