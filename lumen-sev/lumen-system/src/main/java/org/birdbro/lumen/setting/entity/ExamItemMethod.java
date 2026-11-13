package org.birdbro.lumen.setting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import org.birdbro.common.core.base.EntityBase;

/**
 * ExamItemMethod ({@code exam_item_method}, exam-setting-api): the item↔method join -
 * the structural replacement for RCP's comma-joined method-id string. An item update
 * rewrites its binding set (delete by exam_id + batch insert) inside the item's
 * transaction; {@code method_id} references stay service-enforced (zero physical FKs)
 * and the delete guard refuses removing a method any live item still binds.
 *
 * @author birdbro
 */
@TableName("exam_item_method")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ExamItemMethod extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 检查项目 (exam_item.id, convention reference). */
    private Integer examId;

    /** 检查方法 (exam_method.id, convention reference). */
    private Integer methodId;
}
