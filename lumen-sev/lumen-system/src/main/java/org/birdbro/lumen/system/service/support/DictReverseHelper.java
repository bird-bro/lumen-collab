package org.birdbro.lumen.system.service.support;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.birdbro.lumen.system.entity.DictDetailEntity;
import org.birdbro.lumen.system.mapper.DictDetailMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 字典文案→码 反查（导入用）
 *
 * <p>V9 起字典明细的展示文本就是 {@code value}（label 列已删）；V12 起
 * dict_catal 不再携带 code——目录锚点统一为 ca_id（种子目录 id 固定：
 * 1000 common_status、1001-1007 六套真实字典），方法直接按 caId 反查。
 * 方法名保持 codeOf/codesOf/labelMap（调用方语义不变：文案↔码）。</p>
 *
 * @author birdbro
 * @date 2026-10-29
 **/
@Component
@RequiredArgsConstructor
public class DictReverseHelper {

    private final DictDetailMapper dictDetailMapper;

    /** 文案 → 码（支持直接传码串；查不到返回 null） */
    public Integer codeOf(Integer caId, String labelOrCode) {
        if (caId == null || StrUtil.isBlank(labelOrCode)) {
            return null;
        }
        String s = labelOrCode.trim();
        // 直接传数字码
        if (s.matches("\\d+")) {
            return Integer.valueOf(s);
        }
        DictDetailEntity detail = dictDetailMapper.selectOne(new LambdaQueryWrapper<DictDetailEntity>()
                .eq(DictDetailEntity::getCaId, caId)
                .eq(DictDetailEntity::getValue, s)
                .last("LIMIT 1"));
        return detail == null ? null : detail.getCode();
    }

    /** 多文案 csv（"涂阳,初治"）→ 码 csv；任一失败返回 null 并把错误写入 errMsg */
    public String codesOf(Integer caId, String labelCsv, StringBuilder errMsg) {
        if (caId == null || StrUtil.isBlank(labelCsv)) {
            return null;
        }
        List<Integer> codes = new java.util.ArrayList<>();
        for (String label : labelCsv.split("[,，、]")) {
            if (StrUtil.isBlank(label)) {
                continue;
            }
            Integer code = codeOf(caId, label);
            if (code == null) {
                errMsg.append("字典[").append(caId).append("]无该项: ").append(label.trim());
                return null;
            }
            codes.add(code);
        }
        return codes.isEmpty() ? null : codes.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** 目录下全部 明细文案→码 映射（导出用码转文案） */
    public Map<Integer, String> labelMap(Integer caId) {
        if (caId == null) {
            return Map.of();
        }
        return dictDetailMapper.selectList(new LambdaQueryWrapper<DictDetailEntity>()
                        .eq(DictDetailEntity::getCaId, caId))
                .stream().collect(Collectors.toMap(DictDetailEntity::getCode,
                        DictDetailEntity::getValue, (a, b) -> a));
    }
}
