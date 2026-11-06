package org.birdbro.lumen.system.dto;

import javax.validation.constraints.Size;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Update-org request body ({@code PUT /api/v1/system/orgs/{id}},
 * org-management-redesign): a partial profile update. The old {@code upId}, {@code
 * level}, and {@code type} fields remain for compatibility; the service rejects a
 * different type/parent and ignores client-provided level.
 *
 * @author birdbro
 */
@Data
public class UpdateOrgRequest {

    @ApiModelProperty("Legacy parent field; a different parent is rejected")
    private Integer upId;

    @ApiModelProperty("Legacy depth field; ignored because level is server-derived")
    private Integer level;

    @ApiModelProperty("Legacy type field; a different type is rejected")
    private Integer type;

    @Size(max = 128, message = "name must not exceed 128 characters")
    @ApiModelProperty("Display name")
    private String name;

    @Size(max = 32, message = "code must not exceed 32 characters")
    @ApiModelProperty("Stable organization code, unique among live siblings")
    private String code;

    @Size(max = 64, message = "shortName must not exceed 64 characters")
    @ApiModelProperty("Abbreviated display name")
    private String shortName;

    @ApiModelProperty("Lifecycle status: 1 = enabled, 0 = disabled")
    private Integer status;

    @Size(max = 32, message = "provinceCode must not exceed 32 characters")
    @ApiModelProperty("Province administrative-division code")
    private String provinceCode;

    @Size(max = 32, message = "cityCode must not exceed 32 characters")
    @ApiModelProperty("City administrative-division code")
    private String cityCode;

    @Size(max = 32, message = "districtCode must not exceed 32 characters")
    @ApiModelProperty("District administrative-division code")
    private String districtCode;

    @Size(max = 255, message = "address must not exceed 255 characters")
    @ApiModelProperty("Street address")
    private String address;

    @Size(max = 64, message = "contact must not exceed 64 characters")
    @ApiModelProperty("Contact person")
    private String contact;

    @Size(max = 32, message = "phone must not exceed 32 characters")
    @ApiModelProperty("Contact phone")
    private String phone;

    @Size(max = 128, message = "email must not exceed 128 characters")
    @ApiModelProperty("Contact email")
    private String email;

    @Size(max = 255, message = "logoUrl must not exceed 255 characters")
    @ApiModelProperty("Logo URL")
    private String logoUrl;

    @ApiModelProperty("Console display order")
    private Integer sort;
}
