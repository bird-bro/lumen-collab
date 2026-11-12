package org.birdbro.lumen.setting.service;

import java.util.List;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.setting.dto.examsetting.BodyGroupCreateRequest;
import org.birdbro.lumen.setting.dto.examsetting.BodyGroupUpdateRequest;
import org.birdbro.lumen.setting.entity.ExamBody;
import org.birdbro.lumen.setting.entity.ExamBodyGroup;
import org.birdbro.lumen.setting.mapper.DictModalityMapper;
import org.birdbro.lumen.setting.mapper.ExamBodyGroupMapper;
import org.birdbro.lumen.setting.mapper.ExamBodyMapper;
import org.springframework.stereotype.Service;

/**
 * Body-group management (exam-setting-api task 2.1; concrete service - the
 * service-package-layout ruling): CRUD over {@code exam_body_group}, the flat business
 * classification of the 2026-11-12 Option-A split. Every write validates the modality
 * against dict 1027 (the single platform vocabulary - {@link DictModalityMapper}); the
 * name is unique within the modality among LIVE rows (service check A0400, partial
 * unique index backstop); deletion is guarded by live bodies and NAMES the blocking
 * count so the console can surface it verbatim. Updates are D9 null-aware
 * full-replaces (explicit wrapper {@code set}s, never the null-skipping updateById).
 *
 * @author birdbro
 */
@Service
public class ExamBodyGroupService {

    private final ExamBodyGroupMapper examBodyGroupMapper;
    private final ExamBodyMapper examBodyMapper;
    private final DictModalityMapper dictModalityMapper;

    public ExamBodyGroupService(ExamBodyGroupMapper examBodyGroupMapper,
            ExamBodyMapper examBodyMapper, DictModalityMapper dictModalityMapper) {
        this.examBodyGroupMapper = examBodyGroupMapper;
        this.examBodyMapper = examBodyMapper;
        this.dictModalityMapper = dictModalityMapper;
    }

    /** The modality's live groups in sort order (the cascade/filter feed). */
    public List<ExamBodyGroup> getList(String modality) {
        requireModality(modality);
        return examBodyGroupMapper.selectList(new LambdaQueryWrapper<ExamBodyGroup>()
                .eq(ExamBodyGroup::getModality, modality)
                .orderByAsc(ExamBodyGroup::getSort)
                .orderByAsc(ExamBodyGroup::getId));
    }

    /** Creates a group; returns the database-generated id. */
    public Integer create(BodyGroupCreateRequest request) {
        requireModality(request.getModality());
        rejectDuplicateName(request.getModality(), request.getName(), null);

        ExamBodyGroup group = new ExamBodyGroup();
        BeanUtil.copyProperties(request, group, true);
        examBodyGroupMapper.insert(group);
        return group.getId();
    }

    /** Full-replaces the editable surface (D9 null-aware); a missing id rejects A0400. */
    public Boolean update(Integer id, BodyGroupUpdateRequest request) {
        ExamBodyGroup current = examBodyGroupMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "body group id " + id + " does not exist");
        }
        requireModality(request.getModality());
        rejectDuplicateName(request.getModality(), request.getName(), id);

        examBodyGroupMapper.update(null, new LambdaUpdateWrapper<ExamBodyGroup>()
                .eq(ExamBodyGroup::getId, id)
                .set(ExamBodyGroup::getModality, request.getModality())
                .set(ExamBodyGroup::getName, request.getName())
                .set(ExamBodyGroup::getSort, request.getSort())
                .set(ExamBodyGroup::getEnable, request.getEnable()));
        return Boolean.TRUE;
    }

    /**
     * Logical delete, guarded by live bodies (the group must empty out first) - the
     * refusal names the blocking body count.
     */
    public Boolean delete(Integer id) {
        ExamBodyGroup current = examBodyGroupMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "body group id " + id + " does not exist");
        }
        Long bodyCount = examBodyMapper.selectCount(new LambdaQueryWrapper<ExamBody>()
                .eq(ExamBody::getGroupId, id));
        if (bodyCount != null && bodyCount > 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "该分组下仍有 " + bodyCount + " 个部位，请先移除部位后再删除分组");
        }
        examBodyGroupMapper.deleteById(id);
        return Boolean.TRUE;
    }

    // ===================== shared guards =====================

    /**
     * The modality write guard: the value must be a live, enabled detail of dict 1027
     * 检查类型 - the setting domain defines NO vocabulary of its own (D5).
     */
    void requireModality(String modality) {
        if (StrUtil.isBlank(modality)
                || dictModalityMapper.countLiveModality(modality) == 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "检查类型 " + modality + " 不在字典 1027 检查类型 中");
        }
    }

    private void rejectDuplicateName(String modality, String name, Integer selfId) {
        ExamBodyGroup duplicate = examBodyGroupMapper.selectOne(
                new LambdaQueryWrapper<ExamBodyGroup>()
                        .eq(ExamBodyGroup::getModality, modality)
                        .eq(ExamBodyGroup::getName, name));
        if (duplicate != null && !duplicate.getId().equals(selfId)) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "该检查类型下已存在同名分组「" + name + "」");
        }
    }
}
