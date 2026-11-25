package org.birdbro.lumen.us.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.birdbro.lumen.core.entity.ExamOrderStatus;
import org.birdbro.lumen.core.entity.ExamRegistrationType;

/**
 * The create-order result (us-station-registration-sev task 3.2, design D6): the
 * persisted order id, the issued accession (检查号 - the platform number rule minted
 * INSIDE the registration transaction), and the DERIVED entry status. The web surfaces
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

    @ApiModelProperty("入口状态（两种单据都落 REGISTERED：无预约时间=现场单提交即登记；未来时段=预登记）")
    private ExamOrderStatus status;

    @ApiModelProperty("登记类别（服务端落值：WALK_IN 现场单 / PRE_REGISTERED 预登记）")
    private ExamRegistrationType registrationType;

    @ApiModelProperty("预登记时段锚点（回显；现场单为 null）")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date bookingTime;
}
