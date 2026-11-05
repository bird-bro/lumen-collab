package org.birdbro.lumen.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.birdbro.lumen.system.entity.DictDetailEntity;
import org.birdbro.lumen.system.mapper.DictDetailMapper;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 字典码→文案 回填助手（写入时冗余 *_value 列，沿用 OPS 习惯）
 *
 * <p>V9 起字典明细的展示文本就是 {@code value}（label 列已删）；V12 起
 * dict_catal 不再携带 code——目录锚点统一为 ca_id（种子目录 id 固定：
 * 1000 common_status、1001-1007 六套真实字典），方法直接按 caId 查明细。
 * 方法名保持 label/labels（调用方语义不变：码→显示文案）。</p>
 *
 * @author birdbro
 * @date 2026-10-29
 **/
@Component
@RequiredArgsConstructor
public class DictLabelHelper {

    private final DictDetailMapper dictDetailMapper;

    /** 单码 → 文案（查不到返回 null） */
    public String label(Integer caId, Integer code) {
        if (caId == null || code == null) {
            return null;
        }
        DictDetailEntity detail = dictDetailMapper.selectOne(new LambdaQueryWrapper<DictDetailEntity>()
                .eq(DictDetailEntity::getCaId, caId)
                .eq(DictDetailEntity::getCode, code)
                .last("LIMIT 1"));
        return detail == null ? null : detail.getValue();
    }

    /** 多码 csv（如 "1,3,5"）→ 文案 csv（如 "咳嗽咳痰,发热,乏力"） */
    public String labels(Integer caId, String codeCsv) {
        if (caId == null || StrUtil.isBlank(codeCsv)) {
            return null;
        }
        List<Integer> codes = Arrays.stream(codeCsv.split(","))
                .map(String::trim).filter(StrUtil::isNotBlank)
                .map(Integer::valueOf).collect(Collectors.toList());
        if (codes.isEmpty()) {
            return null;
        }
        Map<Integer, String> valueMap = dictDetailMapper.selectList(new LambdaQueryWrapper<DictDetailEntity>()
                        .eq(DictDetailEntity::getCaId, caId)
                        .in(DictDetailEntity::getCode, codes))
                .stream().collect(Collectors.toMap(DictDetailEntity::getCode,
                        DictDetailEntity::getValue, (a, b) -> a));
        return codes.stream()
                .map(c -> valueMap.getOrDefault(c, String.valueOf(c)))
                .collect(Collectors.joining(","));
    }
}
