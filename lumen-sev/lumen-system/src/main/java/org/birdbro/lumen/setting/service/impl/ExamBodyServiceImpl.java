package org.birdbro.lumen.setting.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.setting.dto.BodyCreateRequest;
import org.birdbro.lumen.setting.dto.BodyTreeNode;
import org.birdbro.lumen.setting.dto.BodyUpdateRequest;
import org.birdbro.lumen.setting.entity.ExamBody;
import org.birdbro.lumen.setting.entity.ExamBodyGroup;
import org.birdbro.lumen.setting.entity.ExamItem;
import org.birdbro.lumen.setting.mapper.ExamBodyGroupMapper;
import org.birdbro.lumen.setting.mapper.ExamBodyMapper;
import org.birdbro.lumen.setting.mapper.ExamItemMapper;
import org.birdbro.lumen.setting.service.ExamBodyGroupService;
import org.birdbro.lumen.setting.service.ExamBodyService;
import org.springframework.stereotype.Service;

/**
 * {@link ExamBodyService} implementation - the body is unchanged from the former
 * concrete class (2026-11-13 all-services interface+impl ruling; injection points keep
 * autowiring the interface FQN, so controllers and tests are untouched).
 *
 * @author birdbro
 */
@Service
public class ExamBodyServiceImpl implements ExamBodyService {

    private final ExamBodyMapper examBodyMapper;
    private final ExamBodyGroupMapper examBodyGroupMapper;
    private final ExamItemMapper examItemMapper;
    private final ExamBodyGroupService examBodyGroupService;

    public ExamBodyServiceImpl(ExamBodyMapper examBodyMapper,
            ExamBodyGroupMapper examBodyGroupMapper, ExamItemMapper examItemMapper,
            ExamBodyGroupService examBodyGroupService) {
        this.examBodyMapper = examBodyMapper;
        this.examBodyGroupMapper = examBodyGroupMapper;
        this.examItemMapper = examItemMapper;
        this.examBodyGroupService = examBodyGroupService;
    }

    @Override
    public List<BodyTreeNode> getList(String modality, Integer groupId) {
        examBodyGroupService.requireModality(modality);
        List<ExamBody> bodies = examBodyMapper.selectList(new LambdaQueryWrapper<ExamBody>()
                .eq(ExamBody::getModality, modality)
                .eq(groupId != null, ExamBody::getGroupId, groupId)
                .orderByAsc(ExamBody::getSort)
                .orderByAsc(ExamBody::getId));

        Map<Integer, BodyTreeNode> nodesById = new LinkedHashMap<>();
        for (ExamBody body : bodies) {
            BodyTreeNode node = new BodyTreeNode();
            BeanUtil.copyProperties(body, node);
            nodesById.put(body.getId(), node);
        }
        List<BodyTreeNode> roots = new java.util.ArrayList<>();
        for (BodyTreeNode node : nodesById.values()) {
            BodyTreeNode parent = node.getUpId() != null ? nodesById.get(node.getUpId()) : null;
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    @Override
    public ExamBody getById(Integer id) {
        return examBodyMapper.selectById(id);
    }

    @Override
    public Integer create(BodyCreateRequest request) {
        ExamBodyGroup group = requireGroupOfModality(request.getGroupId(),
                request.getModality());
        requireParentInGroup(request.getUpId(), request.getGroupId(), null);

        ExamBody body = new ExamBody();
        BeanUtil.copyProperties(request, body, true);
        body.setModality(group.getModality());
        examBodyMapper.insert(body);
        return body.getId();
    }

    @Override
    public Boolean update(Integer id, BodyUpdateRequest request) {
        ExamBody current = examBodyMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "body id " + id + " does not exist");
        }
        requireGroupOfModality(request.getGroupId(), request.getModality());
        requireParentInGroup(request.getUpId(), request.getGroupId(), id);

        examBodyMapper.update(null, new LambdaUpdateWrapper<ExamBody>()
                .eq(ExamBody::getId, id)
                .set(ExamBody::getModality, request.getModality())
                .set(ExamBody::getGroupId, request.getGroupId())
                .set(ExamBody::getUpId, request.getUpId())
                .set(ExamBody::getName, request.getName())
                .set(ExamBody::getCode, request.getCode())
                .set(ExamBody::getSort, request.getSort())
                .set(ExamBody::getEnable, request.getEnable()));
        return Boolean.TRUE;
    }

    @Override
    public Boolean delete(Integer id) {
        ExamBody current = examBodyMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "body id " + id + " does not exist");
        }
        Long childCount = examBodyMapper.selectCount(new LambdaQueryWrapper<ExamBody>()
                .eq(ExamBody::getUpId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "该部位下仍有 " + childCount + " 个子部位，请先移除子部位后再删除");
        }
        Long itemCount = examItemMapper.selectCount(new LambdaQueryWrapper<ExamItem>()
                .eq(ExamItem::getBodyId, id));
        if (itemCount != null && itemCount > 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "该部位仍被 " + itemCount + " 个检查项目引用，请先调整项目后再删除");
        }
        examBodyMapper.deleteById(id);
        return Boolean.TRUE;
    }

    // ===================== shared guards =====================

    /**
     * The group anchor: must exist, stay live, and carry the SAME modality the request
     * claims (the split model keeps body/group modality in lockstep).
     */
    private ExamBodyGroup requireGroupOfModality(Integer groupId, String modality) {
        examBodyGroupService.requireModality(modality);
        ExamBodyGroup group = examBodyGroupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "部位分组 " + groupId + " 不存在");
        }
        if (!group.getModality().equals(modality)) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "部位所属检查类型 " + modality + " 与分组检查类型 " + group.getModality()
                            + " 不一致");
        }
        return group;
    }

    /** The parent anchor: a live BODY of the same group (a group id is not a parent). */
    private void requireParentInGroup(Integer upId, Integer groupId, Integer selfId) {
        if (upId == null) {
            return;
        }
        if (upId.equals(selfId)) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "上级部位不能是部位自身");
        }
        ExamBody parent = examBodyMapper.selectById(upId);
        if (parent == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "上级部位 " + upId + " 不存在");
        }
        if (!parent.getGroupId().equals(groupId)) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "上级部位必须属于同一部位分组");
        }
    }
}
