package org.birdbro.lumen.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.birdbro.lumen.core.entity.ExamReport;

/**
 * MyBatis-Plus mapper for {@code exam_report} (lumen-core-registration-kernel task
 * 2.2) - the 1:1 report-placeholder member of the aggregate. The report domain lands
 * with its own later proposal; this mapper stays the minimal persistence surface the
 * kernel guards read.
 *
 * @author birdbro
 */
@Mapper
public interface ExamReportMapper extends BaseMapper<ExamReport> {
}
