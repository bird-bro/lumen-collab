package org.birdbro.lumen.system.dto.deviceroom;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Room read row ({@code GET /api/v1/system/org-rooms/page} and {@code GET /{id}},
 * org-device-room-api): the room's own columns plus the bound device's summary
 * resolved by the mapper's LEFT JOIN - the split model's read shape. {@code devId} is
 * null for an unbound room and so are the summary fields (design D2: never copied,
 * always joined).
 *
 * @author birdbro
 */
@Data
public class RoomRow {

    @ApiModelProperty("诊室 id")
    private Integer id;

    @ApiModelProperty("所属机构")
    private Integer oid;

    @ApiModelProperty("负责科室")
    private Integer did;

    @ApiModelProperty("诊室名称")
    private String name;

    @ApiModelProperty("诊室编码")
    private String code;


    @ApiModelProperty("启用状态 (1 启用 / 0 停用)")
    private Integer enable;

    @ApiModelProperty("叫号屏 IP")
    private String callIp;

    @ApiModelProperty("叫号屏 port")
    private String callPort;

    @ApiModelProperty("叫号屏 MAC")
    private String callMac;

    @ApiModelProperty("绑定设备 id (null = 未绑定)")
    private Integer devId;

    @ApiModelProperty("绑定设备名称 (join)")
    private String devName;

    @ApiModelProperty("绑定设备 AE (join)")
    private String devAe;

    @ApiModelProperty("绑定设备 IP (join)")
    private String devIp;

    @ApiModelProperty("绑定设备 port (join)")
    private String devPort;
}
