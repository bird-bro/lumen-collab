package org.birdbro.lumen.us.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import org.birdbro.framework.config.PageSizeConfig;

/**
 * The order page query (us-station-registration-sev task 3.2, design D6; specs/us-station
 * "Order List" - pagination + the day's-work filters, ordered id desc so today lands
 * first): status (the state-machine vocabulary), accession 检查号 substring, and the
 * patient-name substring (matched INSIDE the jsonb snapshot - the order document carries
 * the identity, the master archive stays patient-side). {@code deleted = 0} rides the
 * framework {@code @TableLogic}.
 *
 * @author birdbro
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class RegistrationOrderPageQuery extends PageSizeConfig {

    @ApiModelProperty("状态过滤（BOOKED/REGISTERED/IN_PROGRESS/COMPLETED/BOOKING_CANCELLED/CANCELLED）")
    private org.birdbro.lumen.core.entity.ExamOrderStatus status;

    @ApiModelProperty("检查号包含过滤（LIKE contains）")
    private String accessionNo;

    @ApiModelProperty("患者姓名包含过滤（jsonb 快照 name，LIKE contains）")
    private String patientName;
}
