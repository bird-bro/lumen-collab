package org.birdbro.lumen.setting.service.impl;

import java.util.List;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.setting.dto.BodyGroupCreateRequest;
import org.birdbro.lumen.setting.dto.BodyGroupUpdateRequest;
import org.birdbro.lumen.setting.entity.ExamBody;
import org.birdbro.lumen.setting.entity.ExamBodyGroup;
import org.birdbro.lumen.setting.mapper.DictModalityMapper;
import org.birdbro.lumen.setting.mapper.ExamBodyGroupMapper;
import org.birdbro.lumen.setting.mapper.ExamBodyMapper;
import org.birdbro.lumen.setting.service.ExamBodyGroupService;
import org.springframework.stereotype.Service;

/**
 * {@link ExamBodyGroupService} implementation - the body is unchanged from the former
 * concrete class (2026-11-13 all-services interface+impl ruling; injection points keep
 * autowiring the interface FQN, so controllers and tests are untouched).
 *
 * @author birdbro
 */
@Service
public class ExamBodyGroupServiceImpl implements ExamBodyGroupService {

    private final ExamBodyGroupMapper examBodyGroupMapper;
    private final ExamBodyMapper examBodyMapper;
    private final DictModalityMapper dictModalityMapper;

    public ExamBodyGroupServiceImpl(ExamBodyGroupMapper examBodyGroupMapper,
            ExamBodyMapper examBodyMapper, DictModalityMapper dictModalityMapper) {
        this.examBodyGroupMapper = examBodyGroupMapper;
        this.examBodyMapper = examBodyMapper;
        this.dictModalityMapper = dictModalityMapper;
    }

    @Override
    public List<ExamBodyGroup> getList(String modality) {
        requireModality(modality);
        return examBodyGroupMapper.selectList(new LambdaQueryWrapper<ExamBodyGroup>()
                .eq(ExamBodyGroup::getModality, modality)
                .orderByAsc(ExamBodyGroup::getSort)
                .orderByAsc(ExamBodyGroup::getId));
    }

    @Override
    public Integer create(BodyGroupCreateRequest request) {
        requireModality(request.getModality());
        rejectDuplicateName(request.getModality(), request.getName(), null);

        ExamBodyGroup group = new ExamBodyGroup();
        BeanUtil.copyProperties(request, group, true);
        examBodyGroupMapper.insert(group);
        return group.getId();
    }

    @Override
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

    @Override
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

    @Override
    public void requireModality(String modality) {
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
