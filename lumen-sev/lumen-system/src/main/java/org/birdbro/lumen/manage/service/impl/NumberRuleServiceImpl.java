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
 *       rule for one family is an A0714 data-duplication rejection (the patient-domain
 *       message pattern).</li>
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
                && activeRuleExists(request.getRuleType(), null)) {
            throw duplicateActive(request.getRuleType());
        }
        NumberRule rule = new NumberRule();
        cn.hutool.core.bean.BeanUtil.copyProperties(request, rule, true);
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
                && activeRuleExists(request.getRuleType(), id)) {
            throw duplicateActive(request.getRuleType());
        }
        NumberRule patch = new NumberRule();
        patch.setId(id);
        cn.hutool.core.bean.BeanUtil.copyProperties(request, patch, true);
        patch.setRuleType(null); // null-ignoring update: the family never changes
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
        if (Integer.valueOf(1).equals(active) && activeRuleExists(existing.getRuleType(), id)) {
            throw duplicateActive(existing.getRuleType());
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

    private boolean activeRuleExists(String ruleType, Integer excludeId) {
        LambdaQueryWrapper<NumberRule> query = new LambdaQueryWrapper<NumberRule>()
                .eq(NumberRule::getRuleType, ruleType)
                .eq(NumberRule::getActive, 1)
                .eq(NumberRule::getDeleted, 0)
                .ne(excludeId != null, NumberRule::getId, excludeId);
        return numberRuleMapper.selectCount(query) > 0;
    }

    private BusinessException duplicateActive(String ruleType) {
        return new BusinessException(ExceptionEnum.ERROR_A0714,
                "数据重复：号码规则冲突: rule_type=" + ruleType + " 已存在启用规则（同类型仅允许一条启用规则）");
    }

    /** A stamped rule must carry a usable Java date pattern (fail at config time). */
    private void guardStampPattern(NumberRuleSaveRequest request) {
        if (Integer.valueOf(1).equals(request.getIsStamp())
                && StrUtil.isBlank(request.getStamp())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "启用日期戳的规则必须携带日期戳格式");
        }
    }
}
