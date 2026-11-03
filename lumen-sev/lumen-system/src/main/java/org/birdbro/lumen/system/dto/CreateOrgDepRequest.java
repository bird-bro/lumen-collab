package org.birdbro.lumen.system.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Create-department request body ({@code POST /api/v1/system/org-deps},
 * auth-rbac-foundation task 4.2). {@code orgId} is the FK anchoring the medical
 * department to the org tree (design D2 - medical attributes stay out of org_info);
 * {@code code} is the stable identifier lumen-integration routes DICOM/HL7 by, unique
 * among live rows.
 *
 * @author birdbro
 */
@Data
public class CreateOrgDepRequest {

    @NotNull(message = "orgId must not be null")
    @ApiModelProperty("Owning org-tree node (FK org_info.id)")
    private Integer orgId;

    @NotBlank(message = "code must not be blank")
    @ApiModelProperty("Stable department code (radiology/us/endo...), unique among live rows")
    private String code;

    @NotBlank(message = "name must not be blank")
    @ApiModelProperty("Display name")
    private String name;
}
