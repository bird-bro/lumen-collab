package org.birdbro.lumen.auth.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Create-department request body ({@code POST /api/v1/auth/departments},
 * auth-rbac-foundation task 4.2). {@code unitId} is the FK anchoring the medical
 * department to the org tree (design D2 - medical attributes stay out of unit_info);
 * {@code code} is the stable identifier lumen-integration routes DICOM/HL7 by, unique
 * among live rows.
 *
 * @author birdbro
 */
@Data
public class CreateDepartmentRequest {

    @NotNull(message = "unitId must not be null")
    @ApiModelProperty("Owning org-tree node (FK unit_info.id)")
    private Integer unitId;

    @NotBlank(message = "code must not be blank")
    @ApiModelProperty("Stable department code (radiology/us/endo...), unique among live rows")
    private String code;

    @NotBlank(message = "name must not be blank")
    @ApiModelProperty("Display name")
    private String name;
}
