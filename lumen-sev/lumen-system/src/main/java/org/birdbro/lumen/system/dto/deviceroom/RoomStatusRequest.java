package org.birdbro.lumen.system.dto.deviceroom;

import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Room status-toggle body ({@code PUT /api/v1/system/org-rooms/{id}/status},
 * org-device-room-api, design D6): only the {@code enable} column changes - the
 * dedicated command mirrors the OrgDepController status endpoint.
 *
 * @author birdbro
 */
@Data
public class RoomStatusRequest {

    @NotNull(message = "enable must not be null")
    @ApiModelProperty("启用状态 (1 启用 / 0 停用)")
    private Integer enable;
}
