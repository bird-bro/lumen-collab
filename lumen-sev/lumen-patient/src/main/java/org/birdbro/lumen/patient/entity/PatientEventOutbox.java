package org.birdbro.lumen.patient.entity;

import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import org.birdbro.common.core.base.EntityBase;
import org.birdbro.lumen.patient.mapper.PatientJsonbTypeHandler;

/**
 * Transactional-outbox carriage ({@code patient_event_outbox}, patient-domain task 2.1,
 * design D2): rows land PENDING inside the patient transaction; the first consumer
 * change (lumen-core snapshot refresh / exam-order repoint) adds the poller that flips
 * status. APPEND-ONLY: no code path deletes or updates payload rows.
 *
 * <p>{@code payload} is a jsonb column mapped through a {@code Map<String, Object>}
 * field + {@link org.birdbro.lumen.patient.mapper.PatientJsonbTypeHandler} with
 * {@code autoResultMap = true} (b2-review amendment: a REAL jsonb object, not a
 * double-encoded quoted string - the handler writes
 * {@code setObject(Types.OTHER)}, so no URL {@code stringtype} flag is involved).</p>
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

    /** 事件载荷（jsonb；Map 字段 + PatientJsonbTypeHandler 映射，b2 审查修订：真实 jsonb 对象）. */
    @TableField(typeHandler = PatientJsonbTypeHandler.class)
    private Map<String, Object> payload;

    /** 投递状态（PENDING，直到第一个消费者变更加入轮询）. */
    private String status;
}
