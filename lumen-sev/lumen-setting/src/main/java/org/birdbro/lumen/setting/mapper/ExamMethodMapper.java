package org.birdbro.lumen.setting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.birdbro.lumen.setting.entity.ExamMethod;

/**
 * MyBatis-Plus mapper for {@code ExamMethod} (exam-setting-api task 1.3). Plain BaseMapper -
 * the service-layer duplicate/referential checks run on wrappers; cross-table display
 * reads (item page body/method names) live on the item mapper as annotated selects.
 *
 * @author birdbro
 */
@Mapper
public interface ExamMethodMapper extends BaseMapper<ExamMethod> {
}
