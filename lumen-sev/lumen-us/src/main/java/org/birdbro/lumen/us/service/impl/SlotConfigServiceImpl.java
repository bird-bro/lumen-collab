package org.birdbro.lumen.us.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import cn.hutool.core.util.StrUtil;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.core.entity.ExamSlotConfig;
import org.birdbro.lumen.core.mapper.ExamSlotConfigMapper;
import org.birdbro.lumen.us.dto.SlotConfigRequest;
import org.birdbro.lumen.us.dto.SlotConfigView;
import org.birdbro.lumen.us.service.SlotConfigService;
import org.springframework.stereotype.Service;

/**
 * Slot-config administration implementation (us-registration-redesign-api tasks 4.1-4.3,
 * design D2). Recorded decisions:
 *
 * <ol>
 *   <li>The token stores TRIMMED verbatim (StrUtil.trim - the number-rule CRUD
 *       precedent); NO dict-catalog re-validation (the ③ UI picks from the dict; the
 *       kernel consumes tokens verbatim; a bogus token simply fail-closes the
 *       modality's pre-registration at validation time).</li>
 *   <li>One LIVE row per modality: the create guard counts live rows for the trimmed
 *       token and refuses A0400 naming the duplicate - the kernel's live-unique partial
 *       index backstops the race.</li>
 *   <li>The token is the POOL IDENTITY: update refuses a changed token (A0400) - the
 *       pre-registration bookkeeping references the token, never the row id.</li>
 *   <li>Delete rides {@code deleteById} - the {@code @TableLogic} intercept makes it
 *       SOFT (the kernel live-unique index only guards live rows, so the freed token
 *       can re-create immediately).</li>
 * </ol>
 *
 * @author birdbro
 */
@Service
public class SlotConfigServiceImpl implements SlotConfigService {

    private final ExamSlotConfigMapper examSlotConfigMapper;

    public SlotConfigServiceImpl(ExamSlotConfigMapper examSlotConfigMapper) {
        this.examSlotConfigMapper = examSlotConfigMapper;
    }

    @Override
    public List<SlotConfigView> list() {
        return examSlotConfigMapper.selectList(new LambdaQueryWrapper<ExamSlotConfig>()
                        .orderByAsc(ExamSlotConfig::getModality))
                .stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    @Override
    public SlotConfigView create(SlotConfigRequest request) {
        String modality = trimToken(request.getModality());
        Long duplicates = examSlotConfigMapper.selectCount(
                new LambdaQueryWrapper<ExamSlotConfig>()
                        .eq(ExamSlotConfig::getModality, modality));
        if (duplicates != null && duplicates > 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "该模态已存在预约设置: " + modality);
        }
        ExamSlotConfig config = ExamSlotConfig.builder()
                .modality(modality)
                .granularityMinutes(request.getGranularityMinutes())
                .slotCapacity(request.getSlotCapacity())
                .openWindows(request.getOpenWindows())
                .build();
        config.setDeleted(0);
        examSlotConfigMapper.insert(config);
        return toView(config);
    }

    @Override
    public SlotConfigView update(Integer id, SlotConfigRequest request) {
        ExamSlotConfig config = liveById(id);
        String modality = trimToken(request.getModality());
        if (!modality.equals(config.getModality())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "预约设置的模态范围创建后不可变更: " + config.getModality());
        }
        config.setGranularityMinutes(request.getGranularityMinutes());
        config.setSlotCapacity(request.getSlotCapacity());
        config.setOpenWindows(request.getOpenWindows());
        examSlotConfigMapper.updateById(config);
        return toView(config);
    }

    @Override
    public void delete(Integer id) {
        ExamSlotConfig config = liveById(id);
        examSlotConfigMapper.deleteById(config.getId());
    }

    // ---------------------------------------------------------------- helpers

    private ExamSlotConfig liveById(Integer id) {
        ExamSlotConfig config = id == null ? null : examSlotConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "预约设置不存在: " + id);
        }
        return config;
    }

    /** The token normalizes by TRIM only (the number-rule CRUD precedent). */
    private String trimToken(String raw) {
        String token = StrUtil.trimToEmpty(raw);
        if (token.isEmpty()) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, "预约设置必须携带模态 token");
        }
        return token;
    }

    private SlotConfigView toView(ExamSlotConfig config) {
        return SlotConfigView.builder()
                .id(config.getId())
                .modality(config.getModality())
                .granularityMinutes(config.getGranularityMinutes())
                .slotCapacity(config.getSlotCapacity())
                .openWindows(config.getOpenWindows())
                .build();
    }
}
