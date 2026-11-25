package org.birdbro.lumen.us.dto;

import java.util.List;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The booking slot-options read (us-booking-slot-picker; specs/us-station "Pre-registration
 * and Slot Capacity" - the prototype's slot dropdown): the modality's live config rendered
 * as the DATE's slot grid with LIVE occupancy, consumed by the registration workbench's
 * 预约时段 select (full slots render disabled with the 已满 n/n suffix - the prototype's
 * 满员标灰 half of the 双保险; the create path's A0400 refusals stay the other half).
 * `configured=false` = no live config for the modality (the create path refuses
 * 该检查类型未开通预登记 fail-closed; the picker disables the select).
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SlotOptionsView {

    @ApiModelProperty("该模态是否已配置预约设置（false 时 slots 为空）")
    private boolean configured;

    @ApiModelProperty("开台时间窗内的档位列表（按窗 × 粒度枚举，窗内顺序）")
    private List<SlotOption> slots;

    /**
     * One granularity slot of the day's grid: the HH:mm bounds, the LIVE BOOKED
     * occupancy (the create path's count predicate: status BOOKED + booking_time in
     * the window + first-item modality token), and the config's per-slot capacity.
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SlotOption {

        @ApiModelProperty("档位开始 HH:mm（提交值，拼 bookingTime 用）")
        private String start;

        @ApiModelProperty("档位结束 HH:mm（展示用）")
        private String end;

        @ApiModelProperty("该档已占用（BOOKED 单数）")
        private int booked;

        @ApiModelProperty("每档上限（配置的 slot_capacity）")
        private int capacity;
    }
}
