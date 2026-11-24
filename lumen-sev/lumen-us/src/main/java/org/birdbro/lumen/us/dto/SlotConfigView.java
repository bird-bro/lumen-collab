package org.birdbro.lumen.us.dto;

import java.util.List;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.birdbro.lumen.core.entity.SlotWindow;

/**
 * The slot-config list/create/update row (us-registration-redesign-api tasks 4.1-4.3):
 * the live policy decoded - the windows render as STRUCTURED {start,end} pairs through
 * the kernel typed jsonb handler (never a raw string), mirroring the kernel
 * ExamSlotConfig shape 1:1.
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SlotConfigView {

    @ApiModelProperty("配置 id")
    private Integer id;

    @ApiModelProperty("模态 token（dict-1024 VALUE）")
    private String modality;

    @ApiModelProperty("开台时段粒度（分钟）")
    private Integer granularityMinutes;

    @ApiModelProperty("每时段容量")
    private Integer slotCapacity;

    @ApiModelProperty("开台时间窗（结构化 {start,end} 对）")
    private List<SlotWindow> openWindows;
}
