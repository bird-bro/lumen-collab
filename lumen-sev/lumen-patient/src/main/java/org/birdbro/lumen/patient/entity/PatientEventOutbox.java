package org.birdbro.lumen.patient.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import org.birdbro.common.core.base.EntityBase;

/**
 * Transactional-outbox carriage ({@code patient_event_outbox}, patient-domain task 2.1,
 * design D2): rows land PENDING inside the patient transaction; the first consumer
 * change (lumen-core snapshot refresh / exam-order repoint) adds the poller that flips
 * status. APPEND-ONLY: no code path deletes or updates payload rows.
 *
 * <p>{@code payload} is a jsonb column mapped through a String field + JacksonTypeHandler
 * with {@code autoResultMap = true} (brief decision 6) - the PostgreSQL JDBC URL needs
 * {@code stringtype=unspecified} so the handler's {@code setString} write binds as the
 * jsonb target type.</p>
 *
 * @author birdbro
 */
@TableName(value = "patient_event_outbox", autoResultMap = true)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PatientEventOutbox extends EntityBase {

    /** Primary key, database auto-increment. */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 事件类型（PATIENT_CHANGED / PATIENT_MERGED...）. */
    private String eventType;

    /** 所属患者 id（patient_master.id，服务级引用）. */
    private Integer patientId;

    /** 事件载荷（jsonb；String 字段 + JacksonTypeHandler 映射）. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private String payload;

    /** 投递状态（PENDING，直到第一个消费者变更加入轮询）. */
    private String status;
}
