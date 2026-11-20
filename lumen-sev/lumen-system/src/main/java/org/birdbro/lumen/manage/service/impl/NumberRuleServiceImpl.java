package org.birdbro.lumen.manage.service.impl;

import cn.hutool.core.util.StrUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.manage.dto.NumberRuleSaveRequest;
import org.birdbro.lumen.manage.entity.NumberRule;
import org.birdbro.lumen.manage.mapper.NumberRuleMapper;
import org.birdbro.lumen.manage.service.NumberRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Number-rule directory implementation (number-rule-unification-sev task 2.2, design
 * D1). Recorded decisions:
 *
 * <ol>
 *   <li>Duplicate-active guard BEFORE the write (the partial unique index
 *       {@code uk_number_rule_active} is the concurrency backstop): a second active
 *       rule for one family AND scope is an A0714 data-duplication rejection (the
 *       patient-domain message pattern; the NULL-safe guard keys the modality scope,
 *       V49 - a scoped active coexists with the family global, design D2).</li>
 *   <li>The rule family is immutable after create (a family switch would orphan the
 *       burned-serial state) - the update patches format parameters only.</li>
 *   <li>Active rules refuse delete (A0400, 先停用再删除 - the patient discipline): a
 *       family without an active rule fails issuance with B0001 until reconfigured.</li>
 *   <li>1:1 request→entity copies ride {@code BeanUtil.copyProperties(src, dest, true)}
 *       (the house idiom).</li>
 * </ol>
 *
 * @author birdbro
 */
@Service
public class NumberRuleServiceImpl implements NumberRuleService {

    private final NumberRuleMapper numberRuleMapper;

    public NumberRuleServiceImpl(NumberRuleMapper numberRuleMapper) {
        this.numberRuleMapper = numberRuleMapper;
    }

    @Override
    public Page<NumberRule> page(long pageNum, long pageSize) {
        return numberRuleMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<NumberRule>()
                        .eq(NumberRule::getDeleted, 0)
                        .orderByAsc(NumberRule::getRuleType));
    }

    @Override
    @Transactional
    public Integer create(NumberRuleSaveRequest request) {
        guardStampPattern(request);
        if (Integer.valueOf(1).equals(request.getActive())
                && activeRuleExists(request.getRuleType(), request.getModality(), null)) {
            throw duplicateActive(request.getRuleType(), request.getModality());
        }
        NumberRule rule = new NumberRule();
        cn.hutool.core.bean.BeanUtil.copyProperties(request, rule, true);
        // trim-then-blank-to-null: a whitespace-padded token (" CT ") normalizes to the
        // clean token (stored verbatim it would be invisible to the exact lookup), and a
        // blank token IS 全局 - the NULL shape the index and the issuer resolution key on
        rule.setModality(StrUtil.trimToNull(rule.getModality()));
        numberRuleMapper.insert(rule);
        return rule.getId();
    }

    @Override
    @Transactional
    public Boolean update(Integer id, NumberRuleSaveRequest request) {
        NumberRule existing = numberRuleMapper.selectById(id);
        if (existing == null || Integer.valueOf(1).equals(existing.getDeleted())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "号码规则不存在: " + id);
        }
        guardStampPattern(request);
        if (Integer.valueOf(1).equals(request.getActive())
                && !request.getRuleType().equals(existing.getRuleType())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "规则类型不可修改: " + id);
        }
        if (Integer.valueOf(1).equals(request.getActive())
                && activeRuleExists(request.getRuleType(), existing.getModality(), id)) {
            throw duplicateActive(request.getRuleType(), existing.getModality());
        }
        NumberRule patch = new NumberRule();
        patch.setId(id);
        cn.hutool.core.bean.BeanUtil.copyProperties(request, patch, true);
        patch.setRuleType(null); // null-ignoring update: the family never changes
        patch.setModality(null); // null-ignoring update: the scope is pool identity - immutable (design D2)
        numberRuleMapper.updateById(patch);
        return true;
    }

    @Override
    @Transactional
    public Boolean setActive(Integer id, Integer active) {
        NumberRule existing = numberRuleMapper.selectById(id);
        if (existing == null || Integer.valueOf(1).equals(existing.getDeleted())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "号码规则不存在: " + id);
        }
        if (active.equals(existing.getActive())) {
            return true; // idempotent re-set - no write, no guard
        }
        if (Integer.valueOf(1).equals(active)
                && activeRuleExists(existing.getRuleType(), existing.getModality(), id)) {
            throw duplicateActive(existing.getRuleType(), existing.getModality());
        }
        NumberRule patch = new NumberRule();
        patch.setId(id);
        patch.setActive(active);
        numberRuleMapper.updateById(patch);
        return true;
    }

    @Override
    @Transactional
    public Boolean delete(Integer id) {
        NumberRule existing = numberRuleMapper.selectById(id);
        if (existing == null || Integer.valueOf(1).equals(existing.getDeleted())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "号码规则不存在: " + id);
        }
        if (Integer.valueOf(1).equals(existing.getActive())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "启用的规则不可删除，请先停用: rule id " + id);
        }
        NumberRule patch = new NumberRule();
        patch.setId(id);
        patch.setDeleted(1);
        numberRuleMapper.updateById(patch);
        return true;
    }

    /**
     * One active rule per family AND scope (V49, number-rule-modality-scope task 1.3,
     * design D2): the guard is NULL-safe - a blank/null scope queries the GLOBAL shape
     * ({@code modality IS NULL}), a token queries its exact scope.
     */
    private boolean activeRuleExists(String ruleType, String modality, Integer excludeId) {
        LambdaQueryWrapper<NumberRule> query = new LambdaQueryWrapper<NumberRule>()
                .eq(NumberRule::getRuleType, ruleType)
                .eq(NumberRule::getActive, 1)
                .eq(NumberRule::getDeleted, 0)
                .ne(excludeId != null, NumberRule::getId, excludeId);
        if (StrUtil.isBlank(modality)) {
            query.isNull(NumberRule::getModality);
        } else {
            query.eq(NumberRule::getModality, modality);
        }
        return numberRuleMapper.selectCount(query) > 0;
    }

    /** The A0714 rejection names the conflicting scope (全局 or the token). */
    private BusinessException duplicateActive(String ruleType, String modality) {
        return new BusinessException(ExceptionEnum.ERROR_A0714,
                "号码规则冲突: rule_type=" + ruleType
                        + " modality=" + (StrUtil.isBlank(modality) ? "全局" : modality)
                        + " 已存在同范围启用规则（同族同范围仅允许一条启用规则）");
    }

    /** A stamped rule must carry a usable Java date pattern (fail at config time). */
    private void guardStampPattern(NumberRuleSaveRequest request) {
        if (Integer.valueOf(1).equals(request.getIsStamp())
                && StrUtil.isBlank(request.getStamp())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "启用日期戳的规则必须携带日期戳格式");
        }
    }
}
