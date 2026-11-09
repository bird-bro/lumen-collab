package org.birdbro.lumen.system.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Enable-toggle request body (role-type-dict-api D7) - the {@code SetOrgStatusRequest}
 * mirror with the {@code enable} field name (dict vocabulary): anything other than
 * {@code 0}/{@code 1} is rejected by the bean validation before the service runs.
 *
 * @author birdbro
 */
@Data
public class SetRoleEnableRequest {

    @NotNull(message = "enable must not be null")
    @Min(value = 0, message = "enable must be 0 or 1")
    @Max(value = 1, message = "enable must be 0 or 1")
    @ApiModelProperty("1 = enable the role, 0 = disable")
    private Integer enable;
}
