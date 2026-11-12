package org.birdbro.lumen.system.dto.deviceroom;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.birdbro.framework.config.PageSizeConfig;

/**
 * Room table query ({@code GET /api/v1/system/org-rooms/page}, org-device-room-api):
 * org-scoped with optional department and room-name filters. {@code orgId} is REQUIRED
 * by the spec - the service rejects a missing orgId with A0400 (GET-param binding; the
 * service guard is the enforcement point).
 *
 * @author birdbro
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class RoomPageQuery extends PageSizeConfig {

    @ApiModelProperty("所属机构 (required)")
    private Integer orgId;

    @ApiModelProperty("负责科室 (optional)")
    private Integer depId;

    @ApiModelProperty("诊室名称 contains 过滤 (optional)")
    private String name;
}
