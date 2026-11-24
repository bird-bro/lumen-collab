package org.birdbro.lumen.core.mapper;

import java.util.Date;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import org.birdbro.lumen.core.entity.ExamSlotConfig;

/**
 * MyBatis-Plus mapper for the per-modality slot config (kernel-pre-registration task
 * 1.4). The capacity service's row lock rides the wrapper {@code .last("FOR UPDATE")}
 * idiom so the SELECT keeps the {@code autoResultMap} typed-jsonb mapping
 * (hand-written entity SQL would need explicit {@code @Results} and re-open the
 * same-name-statement registration trap - the mapper-overload lesson); the ONE
 * annotation method is the scalar DB-server clock (design D4/Risks - the now/past
 * boundary never uses app clocks, and a scalar has no result-map concerns). Config
 * rows belong to the department schema (design D3); this mapper operates on whatever
 * schema the consuming service points it at.
 *
 * @author birdbro
 */
public interface ExamSlotConfigMapper extends BaseMapper<ExamSlotConfig> {

    /**
     * The DB-server wall clock as a naive timestamp ({@code now()::timestamp}) - the
     * pre-registration now/past boundary (design Risks row 2). The session time zone
     * materializes both this value and {@code booking_time} consistently through the
     * same JVM/session TZ pair (single-host v1 - the skew risk is design-documented).
     *
     * @return the DB-server current timestamp
     */
    @Select("SELECT now()::timestamp")
    Date selectDbNow();
}
