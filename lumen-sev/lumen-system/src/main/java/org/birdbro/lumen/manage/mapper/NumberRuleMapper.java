package org.birdbro.lumen.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.birdbro.lumen.manage.entity.NumberRule;

/**
 * MyBatis-Plus mapper for {@code number_rule} (number-rule-unification-sev task 2.1).
 * Beyond the BaseMapper surface it carries the issuance hot-path read (design D3): the
 * {@code FOR UPDATE} row locks that serialize concurrent issuances around the
 * {@code serial_cur} increment - the family's GLOBAL rule (single-arg) and the
 * modality-scoped rule (V49, number-rule-modality-scope task 1.2).
 *
 * @author birdbro
 */
@Mapper
public interface NumberRuleMapper extends BaseMapper<NumberRule> {

    /**
     * Locks and returns the ACTIVE GLOBAL live rule of the given type - the family's
     * {@code modality IS NULL} platform-wide shared pool (V49, design D1: NULL = 全局;
     * before V49 no scoped rows existed, so the query's behavior for legacy callers is
     * unchanged). The lock is held until the caller's transaction ends - concurrent
     * issuances of the family's global pool queue up on this row. Returns {@code null}
     * when the family has no active global rule (the issuer falls back per design D3's
     * resolution chain, then rejects with {@code B0001} - a system-state error).
     *
     * @param ruleType the number family (dict 编号类型 1028 token - 影像号 /
     *                 登记号 / future families)
     * @return the locked active global rule row, or {@code null} when none exists
     */
    @Select("SELECT * FROM number_rule WHERE rule_type = #{ruleType}"
            + " AND modality IS NULL"
            + " AND active = 1 AND deleted = 0 FOR UPDATE")
    NumberRule selectActiveForUpdate(@Param("ruleType") String ruleType);

    /**
     * Locks and returns the ACTIVE live rule of the given family AND modality scope -
     * exact match on the dict-1024 value token (V49, number-rule-modality-scope task
     * 1.2, design D3: the resolution chain tries the scoped rule first and falls back
     * to the family global). Called ONLY with a non-blank token - a blank/null scope
     * rides the single-arg global query. Row-lock and null-return semantics as the
     * single-arg form. NOT a same-name overload: MyBatis keys mapped statements by
     * method NAME, so a second {@code selectActiveForUpdate} would be silently ignored
     * (the MybatisConfiguration duplicate-statement drop) and scoped requests would
     * execute the global SQL - the distinct name is load-bearing.
     *
     * @param ruleType the number family
     * @param modality the modality scope token (dict 模态 1024 value, e.g. CT)
     * @return the locked active scoped rule row, or {@code null} when none exists
     */
    @Select("SELECT * FROM number_rule WHERE rule_type = #{ruleType}"
            + " AND modality = #{modality}"
            + " AND active = 1 AND deleted = 0 FOR UPDATE")
    NumberRule selectScopedActiveForUpdate(@Param("ruleType") String ruleType,
            @Param("modality") String modality);
}
