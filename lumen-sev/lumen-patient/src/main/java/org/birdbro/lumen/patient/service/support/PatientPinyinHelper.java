package org.birdbro.lumen.patient.service.support;

import java.util.Locale;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.pinyin.PinyinUtil;

import org.springframework.stereotype.Component;

/**
 * Server-side patient pinyin (patient-domain b3 brief decision 9): full pinyin of the
 * archive name, UPPER CASED per the EIS 1:1 precedent
 * ({@code lumen-reference/code/eis_scv-single-hospital OrderPatientServiceImpl}).
 * The engine is Hutool {@code PinyinUtil} auto-detecting the classpath TinyPinyin
 * (io.github.biezhi, EIS-lineage pin in lumen-bom); polyphonic imperfection is
 * accepted - wrong pinyin only degrades search, never identity.
 *
 * <p>{@code NULL name -> NULL pinyin}: the unnamed emergency placeholder is legal and
 * simply unsearchable by pinyin.</p>
 *
 * @author birdbro
 */
@Component
public class PatientPinyinHelper {

    /**
     * Converts the name to uppercase full pinyin; blank input (or a blank conversion)
     * yields {@code null}.
     *
     * @param name the archive name (may be null - unnamed emergency)
     * @return the uppercase full pinyin, or null when there is nothing to convert
     */
    public String toPinyin(String name) {
        if (StrUtil.isBlank(name)) {
            return null;
        }
        String pinyin = PinyinUtil.getPinyin(name);
        return StrUtil.isBlank(pinyin) ? null : pinyin.toUpperCase(Locale.ROOT);
    }
}
