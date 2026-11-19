package org.birdbro.lumen.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.birdbro.lumen.core.entity.ExamApplyCache;

/**
 * MyBatis-Plus mapper for {@code exam_apply_cache} (lumen-core-registration-kernel
 * task 6.1) - the two-phase intake cache surface.
 *
 * @author birdbro
 */
@Mapper
public interface ExamApplyCacheMapper extends BaseMapper<ExamApplyCache> {
}
