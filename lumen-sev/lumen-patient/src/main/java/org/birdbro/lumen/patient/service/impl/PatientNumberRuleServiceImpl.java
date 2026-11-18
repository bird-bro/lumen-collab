package org.birdbro.lumen.patient.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.patient.dto.PatientNumberRuleCreateRequest;
import org.birdbro.lumen.patient.dto.PatientNumberRulePageQuery;
import org.birdbro.lumen.patient.dto.PatientNumberRuleUpdateRequest;
import org.birdbro.lumen.patient.entity.PatientNumberRule;
import org.birdbro.lumen.patient.mapper.PatientNumberRuleMapper;
import org.birdbro.lumen.patient.service.PatientNumberRuleService;

/**
 * Number-rule management implementation (patient-domain task 4.1, brief decision 5).
 * Recorded decisions:
 *
 * <p>(1) ACTIVE-RULE UNIQUENESS: exactly one {@code active=1} live rule per
 * {@code rule_type} - the in-service check (A0714 detail naming the type + existing
 * rule id) runs before the write and the partial index
 * {@code uk_patient_number_rule_active} is the concurrency backstop (the b3 archive
 * house pattern: the {@link DuplicateKeyException} re-throws the SAME A0714 payload
 * re-queried from the live duplicate). (2) ACTIVE-RULE DELETE GUARD: deleting the
 * ACTIVE rule is A0400 启用的规则不可删除，请先停用 (the b1 ruling); inactive rules
 * soft-delete through the {@code @TableLogic} logic delete. (3) RUNTIME STATE:
 * {@code serial_cur}/{@code stamp_value} have no CRUD request field and the service
 * never writes them - only {@code PatientNumberService.issue} moves them under its
 * row lock. (4) STAMP DEFAULT: {@code isStamp=1} with a null/blank {@code stamp}
 * defaults to {@code yyMMdd} (the Java pattern; never the lowercase form - uppercase
 * 'Y' is java's week-year trap), identically on create and update. (5) UPDATE is a
 * non-null patch ({@code BeanUtil.copyProperties(request, patch, true)}, the house
 * 1:1 idiom): absent optionals keep their values; a missing target is B0031 (house
 * update-target precedent). Field validation (serialLength 1-10, prefix/suffix ≤8
 * chars of {@code [A-Za-z0-9]}) rides the request DTOs' bean validation.</p>
 *
 * @author birdbro
 */
@Service
public class PatientNumberRuleServiceImpl implements PatientNumberRuleService {

    /** The stamp default when {@code isStamp=1} carries no format (decision 5). */
    private static final String DEFAULT_STAMP = "yyMMdd";

    private final PatientNumberRuleMapper patientNumberRuleMapper;

    public PatientNumberRuleServiceImpl(PatientNumberRuleMapper patientNumberRuleMapper) {
        this.patientNumberRuleMapper = patientNumberRuleMapper;
    }

    @Override
    @Transactional
    public Integer create(PatientNumberRuleCreateRequest request) {
        PatientNumberRule rule = new PatientNumberRule();
        BeanUtil.copyProperties(request, rule, true);
        applyStampDefault(request.getIsStamp(), request.getStamp(), rule);

        // the A0714 conflict fires only when the incoming rule ACTIVATES - an inactive
        // rule may coexist with the active one (spec: "two rules exist for pat_id with
        // one active"); update's check gates identically
        PatientNumberRule existing = Integer.valueOf(1).equals(request.getActive())
                ? activeRuleOfType(rule.getRuleType(), null) : null;
        if (existing != null) {
            throw activeConflict(rule.getRuleType(), existing);
        }
        try {
            patientNumberRuleMapper.insert(rule);
        } catch (DuplicateKeyException e) {
            // index backstop: a concurrent create of the same active type passed the
            // in-service check - re-throw the SAME A0714 payload (b3 house pattern)
            PatientNumberRule concurrent = activeRuleOfType(rule.getRuleType(), null);
            if (concurrent != null) {
                throw activeConflict(rule.getRuleType(), concurrent);
            }
            throw e;
        }
        return rule.getId();
    }

    @Override
    @Transactional
    public Boolean update(Integer id, PatientNumberRuleUpdateRequest request) {
        PatientNumberRule current = patientNumberRuleMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(ExceptionEnum.ERROR_B0031,
                    "number rule id " + id + " does not exist");
        }

        // the uniqueness check runs BEFORE the patch - the effective state is judged
        // from the request (ruleType/active are @NotNull), self excluded
        if (Integer.valueOf(1).equals(request.getActive())) {
            PatientNumberRule existing = activeRuleOfType(request.getRuleType(), id);
            if (existing != null) {
                throw activeConflict(request.getRuleType(), existing);
            }
        }

        PatientNumberRule patch = new PatientNumberRule();
        BeanUtil.copyProperties(request, patch, true);
        applyStampDefault(request.getIsStamp(), request.getStamp(), patch);
        patch.setId(id);
        try {
            patientNumberRuleMapper.updateById(patch);
        } catch (DuplicateKeyException e) {
            // index backstop: a concurrent activation of the same type passed the
            // in-service check - re-throw the SAME A0714 payload (create's b4 pattern)
            PatientNumberRule concurrent = activeRuleOfType(request.getRuleType(), null);
            if (concurrent != null) {
                throw activeConflict(request.getRuleType(), concurrent);
            }
            throw e;
        }
        return Boolean.TRUE;
    }

    @Override
    @Transactional
    public Boolean delete(Integer id) {
        PatientNumberRule current = patientNumberRuleMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(ExceptionEnum.ERROR_B0031,
                    "number rule id " + id + " does not exist");
        }
        if (Integer.valueOf(1).equals(current.getActive())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "启用的规则不可删除，请先停用: rule id " + id + " (type "
                            + current.getRuleType() + ")");
        }
        patientNumberRuleMapper.deleteById(id); // @TableLogic logic delete
        return Boolean.TRUE;
    }

    @Override
    public IPage<PatientNumberRule> page(PatientNumberRulePageQuery query) {
        return patientNumberRuleMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<PatientNumberRule>()
                        .orderByDesc(PatientNumberRule::getId));
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The live ACTIVE rule of one type, optionally excluding one id (the update's
     * self-exclusion). The {@code @TableLogic} deleted=0 filter rides the entity.
     */
    private PatientNumberRule activeRuleOfType(String ruleType, Integer excludeId) {
        if (StrUtil.isBlank(ruleType)) {
            return null;
        }
        LambdaQueryWrapper<PatientNumberRule> wrapper = new LambdaQueryWrapper<PatientNumberRule>()
                .eq(PatientNumberRule::getRuleType, ruleType)
                .eq(PatientNumberRule::getActive, 1);
        if (excludeId != null) {
            wrapper.ne(PatientNumberRule::getId, excludeId);
        }
        return patientNumberRuleMapper.selectOne(wrapper);
    }

    /** The A0714 conflict payload naming the type and the existing rule (decision 5). */
    private BusinessException activeConflict(String ruleType, PatientNumberRule existing) {
        return new BusinessException(ExceptionEnum.ERROR_A0714,
                "号码规则冲突: rule_type=" + ruleType + " 已存在启用规则 id=" + existing.getId()
                        + "（同类型仅允许一条启用规则）");
    }

    /**
     * The stamp default (decision 5): {@code isStamp=1} with a null/blank stamp fills
     * {@code yyMMdd}; a carried format always wins.
     */
    private void applyStampDefault(Integer isStamp, String stamp, PatientNumberRule rule) {
        if (Integer.valueOf(1).equals(isStamp) && StrUtil.isBlank(stamp)) {
            rule.setStamp(DEFAULT_STAMP);
        }
    }
}
