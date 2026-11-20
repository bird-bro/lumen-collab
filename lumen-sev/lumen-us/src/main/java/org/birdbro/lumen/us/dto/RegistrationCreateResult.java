package org.birdbro.lumen.us.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.birdbro.lumen.core.entity.ExamOrderStatus;

/**
 * The create-order result (us-station-registration-sev task 3.2, design D6): the
 * persisted order id, the issued accession (检查号 - the platform number rule minted
 * INSIDE the registration transaction), and the entry status (BOOKED). The web surfaces
 * the accession on success ("the refreshed row shows the issued accession").
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegistrationCreateResult {

    @ApiModelProperty("新建检查单 id")
    private Integer orderId;

    @ApiModelProperty("检查号（号码平台发放）")
    private String accessionNo;

    @ApiModelProperty("入口状态（BOOKED）")
    private ExamOrderStatus status;
}
