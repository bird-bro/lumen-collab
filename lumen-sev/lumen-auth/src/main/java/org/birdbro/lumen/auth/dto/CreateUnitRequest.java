package org.birdbro.lumen.auth.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Create-unit request body ({@code POST /api/v1/auth/units}, auth-rbac-foundation task
 * 4.1). {@code upId = 0} creates a ROOT - multi-organisation deployment is just another
 * upId-0 row, no schema change. {@code level} may be omitted: the service derives it from
 * the parent ({@code parent.level + 1}, roots default 1).
 *
 * @author birdbro
 */
@Data
public class CreateUnitRequest {

    @NotNull(message = "upId must not be null (0 = root)")
    @ApiModelProperty("Parent unit id: 0 = root (multi-root capable)")
    private Integer upId;

    @ApiModelProperty("Depth in the org tree; derived from the parent when omitted")
    private Integer level;

    @ApiModelProperty("Unit type (0 = hospital root)")
    private Integer type;

    @NotBlank(message = "name must not be blank")
    @ApiModelProperty("Display name")
    private String name;
}
