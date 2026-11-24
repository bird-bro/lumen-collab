package org.birdbro.lumen.us.dto;

import java.util.List;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.birdbro.lumen.core.entity.SlotWindow;

/**
 * The slot-config create/update body (us-registration-redesign-api tasks 4.1-4.3,
 * design D2): the modality token (dict-1024 VALUE - trimmed verbatim on create,
 * IMMUTABLE after), the slot granularity/capacity, and the open time windows. The API
 * does NOT re-validate the token against the dict catalog (the ③ UI picks from the
 * dict; the kernel consumes tokens verbatim) nor the window shape (the kernel
 * validation fails CLOSED on malformed windows - the safe default protects consumers).
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SlotConfigRequest {

    @ApiModelProperty("模态 token（dict-1024 VALUE；创建后不可变更）")
    private String modality;

    @ApiModelProperty("开台时段粒度（分钟，缺省 30）")
    private Integer granularityMinutes;

    @ApiModelProperty("每时段容量（缺省 4）")
    private Integer slotCapacity;

    @ApiModelProperty("开台时间窗 [{start,end}] HH:mm；空数组=未开台（预登记拒绝）")
    private List<SlotWindow> openWindows;
}
