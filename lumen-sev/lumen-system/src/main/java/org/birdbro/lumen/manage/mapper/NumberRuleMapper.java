package org.birdbro.lumen.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.birdbro.lumen.manage.entity.NumberRule;

/**
 * MyBatis-Plus mapper for {@code number_rule} (number-rule-unification-sev task 2.1).
 * Beyond the BaseMapper surface it carries the issuance hot-path read (design D3): the
 * {@code FOR UPDATE} row lock that serializes concurrent issuances of one rule family
 * around the {@code serial_cur} increment.
 *
 * @author birdbro
 */
@Mapper
public interface NumberRuleMapper extends BaseMapper<NumberRule> {

    /**
     * Locks and returns the ACTIVE live rule of the given type (design D3 row lock).
     * The lock is held until the caller's transaction ends - concurrent issuances of
     * the same family queue up on this row. Returns {@code null} when the type has no
     * active rule (the service rejects with {@code B0001} - a system-state error).
     *
     * @param ruleType the number family (dict 编号类型 1028 token - 影像号 /
     *                 登记号 / future families)
     * @return the locked active rule row, or {@code null} when none exists
     */
    @Select("SELECT * FROM number_rule WHERE rule_type = #{ruleType}"
            + " AND active = 1 AND deleted = 0 FOR UPDATE")
    NumberRule selectActiveForUpdate(@Param("ruleType") String ruleType);
}
