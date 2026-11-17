package org.birdbro.lumen.patient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.birdbro.lumen.patient.entity.PatientEventOutbox;

/**
 * MyBatis-Plus mapper for {@code patient_event_outbox} (patient-domain task 2.1).
 * The outbox is append-only (design D2) - the poller that flips status arrives with
 * the first consumer change; no delete path exists by design.
 *
 * @author birdbro
 */
@Mapper
public interface PatientEventOutboxMapper extends BaseMapper<PatientEventOutbox> {
}
