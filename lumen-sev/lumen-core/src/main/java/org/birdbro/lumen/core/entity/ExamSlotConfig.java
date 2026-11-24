package org.birdbro.lumen.core.entity;

import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import org.birdbro.common.core.base.EntityBase;
import org.birdbro.lumen.core.mapper.SlotWindowListJsonbTypeHandler;

/**
 * The per-modality pre-registration slot policy (kernel-pre-registration tasks 1.4,
 * design D3): granularity (minutes per slot), capacity per slot, and the open time
 * windows. ONE row per modality (dict-1024 VALUE token, live-unique); config ROWS
 * belong to the department schema (each service vendors the V2 DDL and owns its
 * rows) - the kernel owns only this shape, the validation service, and the copy-from
 * source. An empty {@code open_windows} = pre-registration refused (opt-in).
 *
 * @author birdbro
 */
@TableName(value = "exam_slot_config", autoResultMap = true)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExamSlotConfig extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 模态 token (dict-1024 VALUE, e.g. US/CT/MR) - live-unique, one policy row per modality. */
    private String modality;

    /** 开台时段粒度（分钟）- the grid the booking time floors onto (design D4). */
    private Integer granularityMinutes;

    /** 每时段容量 - the live BOOKED count ceiling per slot window. */
    private Integer slotCapacity;

    /**
     * 开台时间窗 jsonb ([{"start":"08:00","end":"12:00"},...], end exclusive). Reads go
     * through the TYPED list handler (the raw {@code List.class} parse yields
     * {@code List<LinkedHashMap>} - the ExamItemRefListJsonbTypeHandler lesson).
     */
    @TableField(typeHandler = SlotWindowListJsonbTypeHandler.class)
    private List<SlotWindow> openWindows;
}
