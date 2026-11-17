package org.birdbro.lumen.patient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.birdbro.lumen.patient.entity.PatientIdentifier;

/**
 * MyBatis-Plus mapper for {@code patient_identifier} (patient-domain task 2.1).
 *
 * @author birdbro
 */
@Mapper
public interface PatientIdentifierMapper extends BaseMapper<PatientIdentifier> {
}
