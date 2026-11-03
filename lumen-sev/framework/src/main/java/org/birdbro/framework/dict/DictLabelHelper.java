package org.birdbro.framework.dict;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.birdbro.framework.dict.entity.DictDetailEntity;
import org.birdbro.framework.dict.mapper.DictDetailMapper;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 字典码→标签 回填助手（写入时冗余 *_value 列，沿用 OPS 习惯）
 *
 * @author birdbro
 * @date 2026-10-29
 **/
@Component
@RequiredArgsConstructor
public class DictLabelHelper {

    private final DictDetailMapper dictDetailMapper;

    /** 单码 → 标签（查不到返回 null） */
    public String label(String caCode, Integer code) {
        if (code == null) {
            return null;
        }
        DictDetailEntity detail = dictDetailMapper.selectOne(new LambdaQueryWrapper<DictDetailEntity>()
                .eq(DictDetailEntity::getCaCode, caCode)
                .eq(DictDetailEntity::getCode, code)
                .last("LIMIT 1"));
        return detail == null ? null : detail.getLabel();
    }

    /** 多码 csv（如 "1,3,5"）→ 标签 csv（如 "咳嗽咳痰,发热,乏力"） */
    public String labels(String caCode, String codeCsv) {
        if (StrUtil.isBlank(codeCsv)) {
            return null;
        }
        List<Integer> codes = Arrays.stream(codeCsv.split(","))
                .map(String::trim).filter(StrUtil::isNotBlank)
                .map(Integer::valueOf).collect(Collectors.toList());
        if (codes.isEmpty()) {
            return null;
        }
        Map<Integer, String> labelMap = dictDetailMapper.selectList(new LambdaQueryWrapper<DictDetailEntity>()
                        .eq(DictDetailEntity::getCaCode, caCode)
                        .in(DictDetailEntity::getCode, codes))
                .stream().collect(Collectors.toMap(DictDetailEntity::getCode,
                        DictDetailEntity::getLabel, (a, b) -> a));
        return codes.stream()
                .map(c -> labelMap.getOrDefault(c, String.valueOf(c)))
                .collect(Collectors.joining(","));
    }
}
