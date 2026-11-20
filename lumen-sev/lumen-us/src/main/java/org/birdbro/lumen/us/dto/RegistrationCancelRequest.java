package org.birdbro.lumen.us.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * The cancel body (us-station-registration-sev task 3.2, design D6; specs/us-station
 * "Cancel carries a reason"): the cancel reason the state machine stores on the matching
 * cancel branch (BOOKING_CANCELLED from BOOKED, CANCELLED otherwise). The web's 取消
 * flow requires a reason input with confirmation before it posts.
 *
 * @author birdbro
 */
@Data
public class RegistrationCancelRequest {

    @ApiModelProperty("取消原因（存入 cancel_reason）")
    private String reason;
}
