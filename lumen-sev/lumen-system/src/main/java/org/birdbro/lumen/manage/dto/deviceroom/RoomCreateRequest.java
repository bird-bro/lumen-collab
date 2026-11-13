package org.birdbro.lumen.manage.dto.deviceroom;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Create-room request body ({@code POST /api/v1/system/org-rooms}, org-device-room-api).
 * Required: oid/did/name/type/enable; {@code devId} and the 叫号 block are optional.
 * {@code devId} must identify a live device of the SAME org; violations reject A0400
 * (spec "Exam Room Binding Validation"). The room stores ONLY the device reference -
 * the split model's core consequence (design D2).
 *
 * @author birdbro
 */
@Data
public class RoomCreateRequest {

    @NotNull(message = "oid must not be null")
    @ApiModelProperty("所属机构 (org_info.id)")
    private Integer oid;

    @NotNull(message = "did must not be null")
    @ApiModelProperty("负责科室 (org_dep.id)")
    private Integer did;

    @NotBlank(message = "name must not be blank")
    @Size(max = 128, message = "name must not exceed 128 characters")
    @ApiModelProperty("诊室名称, unique within the owning department")
    private String name;

    @Size(max = 32, message = "code must not exceed 32 characters")
    @ApiModelProperty("诊室编码 (optional)")
    private String code;


    @NotNull(message = "enable must not be null")
    @ApiModelProperty("启用状态 (1 启用 / 0 停用)")
    private Integer enable;

    @ApiModelProperty("绑定设备 (org_device.id, live + same-org; null = 暂不绑定)")
    private Integer devId;

    // ---- 叫号 block: the queue screen hangs at the room door (split ruling) ----

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
