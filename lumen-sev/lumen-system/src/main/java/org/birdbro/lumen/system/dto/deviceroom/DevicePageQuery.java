package org.birdbro.lumen.system.dto.deviceroom;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.birdbro.framework.config.PageSizeConfig;

/**
 * Device table query ({@code GET /api/v1/system/org-devices/page},
 * org-device-room-api): org-scoped with type/status/keyword filters. {@code orgId} is
 * REQUIRED by the spec (the page is always org-scoped) - the service rejects a missing
 * orgId with A0400 (the query binds from GET params, where bean validation would not
 * fire; the service guard is the enforcement point).
 *
 * @author birdbro
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DevicePageQuery extends PageSizeConfig {

    @ApiModelProperty("所属机构 (required)")
    private Integer orgId;

    @ApiModelProperty("设备类型 (dict 1024 code, optional)")
    private String type;

    @ApiModelProperty("设备状态 (dict 1025 code, optional)")
    private Integer status;

    @ApiModelProperty("关键字, matched against device name or code")
    private String keyword;
}
