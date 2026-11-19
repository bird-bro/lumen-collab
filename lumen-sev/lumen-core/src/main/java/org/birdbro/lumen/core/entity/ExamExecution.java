package org.birdbro.lumen.core.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import org.birdbro.common.core.base.EntityBase;

/**
 * The exam-execution member of the aggregate (1:1 with the order document): when the
 * exam ran and who performed it. Radiology's PACS-driven 拍片状态 and the imaging
 * channel are department/integration concerns outside the kernel.
 *
 * @author birdbro
 */
@TableName("exam_execution")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExamExecution extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** The owning order id (1:1). */
    private Integer orderId;

    /** 检查开始时间. */
    private Date startTime;

    /** 检查结束时间. */
    private Date endTime;

    /** 执行人 uid (nullable - not yet dispatched). */
    private Integer operatorId;

    /** 执行备注. */
    private String remark;
}
