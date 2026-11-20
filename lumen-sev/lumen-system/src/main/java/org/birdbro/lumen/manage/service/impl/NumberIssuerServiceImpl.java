package org.birdbro.lumen.manage.service.impl;

import java.util.Date;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.manage.entity.NumberRule;
import org.birdbro.lumen.manage.mapper.NumberRuleMapper;
import org.birdbro.lumen.manage.service.NumberIssuerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform number-issuer implementation (number-rule-unification-sev task 2.1, design
 * D3): the patient-domain issuance semantics relocated 1:1 onto the platform directory.
 * Recorded decisions:
 *
 * <ol>
 *   <li>{@code REQUIRES_NEW} on {@link #issue(String, String)} - the burned-serial
 *     semantic: the row-locked increment commits in its OWN transaction, so a caller
 *     rollback after issuance cannot revert {@code serial_cur}; the failed caller
 *     operation burns the number (spec: Burned serial survives a caller rollback).</li>
 *   <li>Day reset: with {@code is_stamp = 1} the stamp token is the Java-pattern
 *     {@code DateUtil.format} of NOW against the rule's {@code stamp} FORMAT parameter;
 *     a token differing from {@code stamp_value} restarts the serial at 1, otherwise
 *     {@code serial_cur + 1}; both columns are written back. With {@code is_stamp = 0}
 *     only {@code serial_cur} advances - the null-ignoring update leaves
 *     {@code stamp_value} untouched.</li>
 *   <li>Format: prefix + stamp token + zero-padded serial + suffix. Error mapping: no
 *     active rule is a system-state B0001 (the request itself is fine - an admin fixes
 *     the config); an invalid stamp pattern is an A0400 request-parameter error.</li>
 *   <li>Modality resolution (number-rule-modality-scope task 1.2, design D3): a
 *     non-blank token tries the exact scoped rule first, then the family's global rule,
 *     then B0001 naming the family and (when present) the token. Each scope row carries
 *     its own {@code serial_cur} - scoped pools count independently. The single-arg
 *     legacy shape delegates with a null scope (patient 影像号 and any legacy caller
 *     stay global).</li>
 * </ol>
 *
 * @author birdbro
 */
@Service
public class NumberIssuerServiceImpl implements NumberIssuerService {

    private final NumberRuleMapper numberRuleMapper;

    public NumberIssuerServiceImpl(NumberRuleMapper numberRuleMapper) {
        this.numberRuleMapper = numberRuleMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String issue(String ruleType) {
        return issue(ruleType, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String issue(String ruleType, String modality) {
        // resolution chain (design D3): exact scope -> family global -> B0001
        NumberRule rule = null;
        if (StrUtil.isNotBlank(modality)) {
            rule = numberRuleMapper.selectScopedActiveForUpdate(ruleType, modality);
        }
        if (rule == null) {
            rule = numberRuleMapper.selectActiveForUpdate(ruleType);
        }
        if (rule == null) {
            throw new BusinessException(ExceptionEnum.ERROR_B0001,
                    "未配置启用的号码规则: " + ruleType
                            + (StrUtil.isNotBlank(modality) ? "（模态 " + modality + "）" : ""));
        }

        String stampToken = null;
        if (Integer.valueOf(1).equals(rule.getIsStamp())) {
            try {
                stampToken = DateUtil.format(new Date(), rule.getStamp());
            } catch (RuntimeException e) {
                throw new BusinessException(ExceptionEnum.ERROR_A0400,
                        "号码规则的日期戳格式非法: " + rule.getStamp());
            }
        }

        int serial;
        if (stampToken != null) {
            // same stamp day: continue; a new stamp day: restart
            serial = stampToken.equals(rule.getStampValue()) ? rule.getSerialCur() + 1 : 1;
        } else {
            serial = rule.getSerialCur() + 1;
        }

        NumberRule update = new NumberRule();
        update.setId(rule.getId());
        update.setSerialCur(serial);
        // null-ignoring update: with is_stamp = 0 this leaves stamp_value untouched
        update.setStampValue(stampToken);
        numberRuleMapper.updateById(update);

        return StrUtil.nullToEmpty(rule.getPrefix())
                + (stampToken != null ? stampToken : "")
                + StrUtil.padPre(String.valueOf(serial), rule.getSerialLength(), '0')
                + StrUtil.nullToEmpty(rule.getSuffix());
    }
}
