package org.birdbro.framework.dict;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.birdbro.framework.dict.entity.DictDetailEntity;
import org.birdbro.framework.dict.mapper.DictDetailMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 字典标签→码 反查（导入用）
 *
 * @author birdbro
 * @date 2026-10-29
 **/
@Component
@RequiredArgsConstructor
public class DictReverseHelper {

    private final DictDetailMapper dictDetailMapper;

    /** 标签 → 码（支持直接传码串；查不到返回 null） */
    public Integer codeOf(String caCode, String labelOrCode) {
        if (StrUtil.isBlank(labelOrCode)) {
            return null;
        }
        String s = labelOrCode.trim();
        // 直接传数字码
        if (s.matches("\\d+")) {
            return Integer.valueOf(s);
        }
        DictDetailEntity detail = dictDetailMapper.selectOne(new LambdaQueryWrapper<DictDetailEntity>()
                .eq(DictDetailEntity::getCaCode, caCode)
                .eq(DictDetailEntity::getLabel, s)
                .last("LIMIT 1"));
        return detail == null ? null : detail.getCode();
    }

    /** 多标签 csv（"涂阳,初治"）→ 码 csv；任一失败返回 null 并把错误写入 errMsg */
    public String codesOf(String caCode, String labelCsv, StringBuilder errMsg) {
        if (StrUtil.isBlank(labelCsv)) {
            return null;
        }
        List<Integer> codes = new java.util.ArrayList<>();
        for (String label : labelCsv.split("[,，、]")) {
            if (StrUtil.isBlank(label)) {
                continue;
            }
            Integer code = codeOf(caCode, label);
            if (code == null) {
                errMsg.append("字典[").append(caCode).append("]无该项: ").append(label.trim());
                return null;
            }
            codes.add(code);
        }
        return codes.isEmpty() ? null : codes.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** 目录下全部 明细label→明细 实体映射（导出用码转标签） */
    public Map<Integer, String> labelMap(String caCode) {
        return dictDetailMapper.selectList(new LambdaQueryWrapper<DictDetailEntity>()
                        .eq(DictDetailEntity::getCaCode, caCode))
                .stream().collect(Collectors.toMap(DictDetailEntity::getCode,
                        DictDetailEntity::getLabel, (a, b) -> a));
    }
}
