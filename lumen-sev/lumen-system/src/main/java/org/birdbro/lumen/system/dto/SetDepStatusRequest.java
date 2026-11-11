package org.birdbro.lumen.system.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Department status request body ({@code PUT /api/v1/system/org-deps/{id}/status}),
 * mirroring {@link SetOrgStatusRequest} for the org family. Bean validation keeps
 * out-of-range values away from the service.
 *
 * @author birdbro
 */
@Data
public class SetDepStatusRequest {

    @NotNull(message = "status must not be null")
    @Min(value = 0, message = "status must be 0 or 1")
    @Max(value = 1, message = "status must be 0 or 1")
    @ApiModelProperty("1 = enable the department, 0 = disable")
    private Integer status;
}
