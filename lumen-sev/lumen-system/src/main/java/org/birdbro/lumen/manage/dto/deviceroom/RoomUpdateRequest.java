package org.birdbro.lumen.manage.dto.deviceroom;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Update-room request body ({@code PUT /api/v1/system/org-rooms/{id}},
 * org-device-room-api): the FULL editable surface, full-replace semantics (D9
 * null-aware - absent call fields clear). {@code devId} supports REBIND and CLEAR in
 * the same update: a different live same-org device id rebinds; {@code null} clears
 * the binding (spec "Exam Room Update and Delete"). The NOT NULL columns are required.
 *
 * @author birdbro
 */
@Data
public class RoomUpdateRequest {

    @NotNull(message = "oid must not be null")
    @ApiModelProperty("所属机构 (org_info.id)")
    private Integer oid;

    @NotNull(message = "did must not be null")
    @ApiModelProperty("负责科室 (org_dep.id)")
    private Integer did;

    @NotBlank(message = "name must not be blank")
    @Size(max = 128, message = "name must not exceed 128 characters")
    @ApiModelProperty("诊室名称")
    private String name;

    @Size(max = 32, message = "code must not exceed 32 characters")
    @ApiModelProperty("诊室编码 (null clears)")
    private String code;


    @ApiModelProperty("绑定设备 (rebind to a live same-org device; null clears the binding)")
    private Integer devId;

    @Size(max = 64, message = "callIp must not exceed 64 characters")
    @ApiModelProperty("叫号屏 IP")
    private String callIp;

    @Size(max = 16, message = "callPort must not exceed 16 characters")
    @ApiModelProperty("叫号屏 port")
    private String callPort;

    @Size(max = 64, message = "callMac must not exceed 64 characters")
    @ApiModelProperty("叫号屏 MAC")
    private String callMac;
}
