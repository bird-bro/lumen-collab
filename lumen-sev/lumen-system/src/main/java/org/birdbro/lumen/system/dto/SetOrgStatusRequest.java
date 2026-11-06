package org.birdbro.lumen.system.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Organization status request body ({@code PUT /api/v1/system/orgs/{id}/status},
 * org-status-toggle-api). Bean validation keeps out-of-range values away from the
 * service, mirroring the user-management enable command.
 *
 * @author birdbro
 */
@Data
public class SetOrgStatusRequest {

    @NotNull(message = "status must not be null")
    @Min(value = 0, message = "status must be 0 or 1")
    @Max(value = 1, message = "status must be 0 or 1")
    @ApiModelProperty("1 = enable the organization, 0 = disable")
    private Integer status;
}
