package org.birdbro.lumen.patient.service.support;

import java.util.Locale;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.pinyin.PinyinUtil;

import org.springframework.stereotype.Component;

/**
 * Server-side patient pinyin (patient-domain b3 decision 9; NORMALIZED per the b4
 * search-contract ruling, coordinator decision 11): full pinyin of the archive name
 * normalized to SPACE-STRIPPED LOWERCASE ({@code 张三 -> "zhangsan"}) at GENERATION,
 * so the page search is a plain {@code pinyin LIKE 'prefix%'} - no double-side
 * normalization machinery. This supersedes the b3 EIS uppercase form (the helper's
 * output is the search contract, not a verbatim copy). The engine is Hutool
 * {@code PinyinUtil} auto-detecting the classpath TinyPinyin (io.github.biezhi,
 * EIS-lineage pin in lumen-bom); polyphonic imperfection is accepted - wrong pinyin
 * only degrades search, never identity.
 *
 * <p>{@code NULL name -> NULL pinyin}: the unnamed emergency placeholder is legal and
 * simply unsearchable by pinyin.</p>
 *
 * @author birdbro
 */
@Component
public class PatientPinyinHelper {

    /**
     * Converts the name to the normalized (space-stripped, lowercase) full pinyin;
     * blank input (or a blank conversion) yields {@code null}.
     *
     * @param name the archive name (may be null - unnamed emergency)
     * @return the normalized full pinyin, or null when there is nothing to convert
     */
    public String toPinyin(String name) {
        if (StrUtil.isBlank(name)) {
            return null;
        }
        String pinyin = PinyinUtil.getPinyin(name);
        if (StrUtil.isBlank(pinyin)) {
            return null;
        }
        return pinyin.replace(" ", "").toLowerCase(Locale.ROOT);
    }
}
