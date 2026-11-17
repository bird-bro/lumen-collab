package org.birdbro.lumen.patient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.birdbro.lumen.patient.entity.PatientNumberRule;

/**
 * MyBatis-Plus mapper for {@code patient_number_rule} (patient-domain task 2.1).
 * Beyond the BaseMapper surface it carries the issuance hot-path read (design D1):
 * the {@code FOR UPDATE} row lock that serializes concurrent issuances of one rule
 * family around the {@code serial_cur} increment.
 *
 * @author birdbro
 */
@Mapper
public interface PatientNumberRuleMapper extends BaseMapper<PatientNumberRule> {

    /**
     * Locks and returns the ACTIVE live rule of the given type (design D1 row lock).
     * The lock is held until the caller's transaction ends - concurrent issuances of
     * the same family queue up on this row. Returns {@code null} when the type has no
     * active rule (the service rejects with {@code B0001} - a system-state error).
     *
     * @param ruleType the number rule family ({@code pat_id} today)
     * @return the locked active rule row, or {@code null} when none exists
     */
    @Select("SELECT * FROM patient_number_rule WHERE rule_type = #{ruleType}"
            + " AND active = 1 AND deleted = 0 FOR UPDATE")
    PatientNumberRule selectActiveForUpdate(@Param("ruleType") String ruleType);
}
