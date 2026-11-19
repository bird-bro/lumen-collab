package org.birdbro.lumen.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.birdbro.lumen.core.entity.ExamOrder;

/**
 * MyBatis-Plus mapper for {@code exam_order} (lumen-core-registration-kernel task 2.2).
 * The status column is written ONLY through the state-machine transition service (task
 * 3.1) - this mapper stays the raw persistence surface.
 *
 * @author birdbro
 */
@Mapper
public interface ExamOrderMapper extends BaseMapper<ExamOrder> {
}
