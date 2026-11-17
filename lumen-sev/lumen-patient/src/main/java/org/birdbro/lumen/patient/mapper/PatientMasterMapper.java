package org.birdbro.lumen.patient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.birdbro.lumen.patient.entity.PatientMaster;

/**
 * MyBatis-Plus mapper for {@code patient_master} (patient-domain task 2.1). The
 * BaseMapper surface covers the batch-2 data contract; the resolve-chain reads
 * (id_no exact, card_no, soft candidates) arrive with the batch-3 archive services.
 *
 * @author birdbro
 */
@Mapper
public interface PatientMasterMapper extends BaseMapper<PatientMaster> {
}
