package org.birdbro.lumen.setting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import org.birdbro.lumen.setting.entity.ExamItemMethod;

/**
 * MyBatis-Plus mapper for {@code exam_item_method} (exam-setting-api task 1.3 + 4.1).
 * The join rows are VALUE rows with no independent lifecycle: the item update rewrites
 * the set PHYSICALLY (delete-by-exam_id + batch insert) - a logic delete would leave
 * soft-deleted residue squatting on the (exam_id, method_id) unique key and poison the
 * next rewrite that re-binds the same method.
 *
 * @author birdbro
 */
@Mapper
public interface ExamItemMethodMapper extends BaseMapper<ExamItemMethod> {

    /**
     * PHYSICALLY removes every binding of one item (the rewrite's wipe step - runs
     * inside the item's transaction so a later failure rolls it back with the rest).
     */
    @Delete("DELETE FROM exam_item_method WHERE exam_id = #{examId}")
    int deleteByExamId(@Param("examId") Integer examId);
}
