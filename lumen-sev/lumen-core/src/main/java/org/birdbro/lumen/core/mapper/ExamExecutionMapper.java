package org.birdbro.lumen.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.birdbro.lumen.core.entity.ExamExecution;

/**
 * MyBatis-Plus mapper for {@code exam_execution} (lumen-core-registration-kernel task
 * 2.2) - the 1:1 execution member of the aggregate.
 *
 * @author birdbro
 */
@Mapper
public interface ExamExecutionMapper extends BaseMapper<ExamExecution> {
}
