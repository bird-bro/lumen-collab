package org.birdbro.lumen.patient.service.impl;

import java.util.Date;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.patient.entity.PatientNumberRule;
import org.birdbro.lumen.patient.mapper.PatientNumberRuleMapper;
import org.birdbro.lumen.patient.service.PatientNumberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Number issuance implementation (patient-domain task 2.2).
 *
 * <p>Recorded decisions. (1) {@code REQUIRES_NEW} on {@link #issue(String)} - the
 * coordinator-pinned burned-serial semantic (brief decision 1): the row-locked
 * increment commits in its own transaction, so a caller rollback after issuance cannot
 * revert {@code serial_cur}; the failed archive creation burns the number. (2) Day
 * reset (decision 2): with {@code is_stamp = 1} the stamp token is the Java-pattern
 * {@code DateUtil.format} of NOW against the rule's {@code stamp} FORMAT parameter; a
 * token differing from {@code stamp_value} restarts the serial at 1, otherwise
 * {@code serial_cur + 1}; both columns are written back. With {@code is_stamp = 0} only
 * {@code serial_cur} advances - the null-ignoring update leaves {@code stamp_value}
 * untouched. (3) Format (decision 3): prefix + stamp token + zero-padded serial +
 * suffix. (4) Error mapping (decision 4): no active rule is a system-state B0001 (the
 * request itself is fine - an admin fixes the config); an invalid stamp pattern is an
 * A0400 request-parameter error.</p>
 *
 * @author birdbro
 */
@Service
public class PatientNumberServiceImpl implements PatientNumberService {

    private final PatientNumberRuleMapper patientNumberRuleMapper;

    public PatientNumberServiceImpl(PatientNumberRuleMapper patientNumberRuleMapper) {
        this.patientNumberRuleMapper = patientNumberRuleMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String issue(String ruleType) {
        PatientNumberRule rule = patientNumberRuleMapper.selectActiveForUpdate(ruleType);
        if (rule == null) {
            throw new BusinessException(ExceptionEnum.ERROR_B0001,
                    "未配置启用的影像号规则: " + ruleType);
        }

        String stampToken = null;
        if (Integer.valueOf(1).equals(rule.getIsStamp())) {
            try {
                stampToken = DateUtil.format(new Date(), rule.getStamp());
            } catch (RuntimeException e) {
                throw new BusinessException(ExceptionEnum.ERROR_A0400,
                        "影像号规则的日期戳格式非法: " + rule.getStamp());
            }
        }

        int serial;
        if (stampToken != null) {
            // same stamp day: continue; a new stamp day: restart (decision 2)
            serial = stampToken.equals(rule.getStampValue()) ? rule.getSerialCur() + 1 : 1;
        } else {
            serial = rule.getSerialCur() + 1;
        }

        PatientNumberRule update = new PatientNumberRule();
        update.setId(rule.getId());
        update.setSerialCur(serial);
        // null-ignoring update: with is_stamp = 0 this leaves stamp_value untouched
        update.setStampValue(stampToken);
        patientNumberRuleMapper.updateById(update);

        return rule.getPrefix()
                + (stampToken != null ? stampToken : "")
                + StrUtil.padPre(String.valueOf(serial), rule.getSerialLength(), '0')
                + rule.getSuffix();
    }
}
