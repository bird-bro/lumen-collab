package org.birdbro.lumen.system.dto;

import java.util.List;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Profile-update request body ({@code PUT /api/v1/system/users/{id}},
 * user-edit-delete-profile-api task 3.1). Deliberately NOT the create request: NO
 * username (immutable through this endpoint - the spec pins the posted value is
 * ignored, not applied) and NO password (the reset stays its own SM2-encrypted
 * endpoint). {@code roleIds} present means FULL-REPLACE of the user's bindings (the
 * role-menu assignment precedent); absent means untouched.
 *
 * @author birdbro
 */
@Data
public class UpdateUserRequest {

    @ApiModelProperty("Display name (absent = untouched - the partial-update semantics; "
            + "the service degrades a BLANK value the same way, the console always sends one)")
    private String name;

    @ApiModelProperty("Phone number")
    private String phone;

    @ApiModelProperty("Email")
    private String email;

    @ApiModelProperty("Dict 性别 catalog code")
    private Integer gender;

    @ApiModelProperty("Dict 职称 catalog code")
    private Integer title;

    @ApiModelProperty("Owning department (org_dep.id; must reference a live department - A0400 on a miss)")
    private Integer depId;

    @ApiModelProperty("Present = FULL-REPLACE of the user's role bindings (each id must exist, A0400 on a miss)")
    private List<Integer> roleIds;

    @ApiModelProperty("Exam-type visibility, CSV of dict 检查类型 codes (the RCP lineage shape)")
    private String modalitys;

    @ApiModelProperty("Report-workflow authority tier (dict 审核权限 code; null = no tier)")
    private Integer repAudit;

    @ApiModelProperty("Report-signature image path (the common upload's stored path)")
    private String signature;
}
