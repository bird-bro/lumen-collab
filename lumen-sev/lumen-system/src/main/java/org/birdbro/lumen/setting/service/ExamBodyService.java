package org.birdbro.lumen.setting.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.setting.dto.examsetting.BodyCreateRequest;
import org.birdbro.lumen.setting.dto.examsetting.BodyTreeNode;
import org.birdbro.lumen.setting.dto.examsetting.BodyUpdateRequest;
import org.birdbro.lumen.setting.entity.ExamBody;
import org.birdbro.lumen.setting.entity.ExamBodyGroup;
import org.birdbro.lumen.setting.entity.ExamItem;
import org.birdbro.lumen.setting.mapper.ExamBodyGroupMapper;
import org.birdbro.lumen.setting.mapper.ExamBodyMapper;
import org.birdbro.lumen.setting.mapper.ExamItemMapper;
import org.springframework.stereotype.Service;

/**
 * Body-part management (exam-setting-api task 2.2; concrete service): CRUD over
 * {@code exam_body} - the anatomy concept of the Option-A split. A body anchors on a
 * LIVE group of the SAME modality and may nest under a parent of the SAME group
 * (service-enforced, zero physical FKs). {@code GET /list} assembles the up_id forest
 * service-side. Deletion is guarded twice: live child bodies, then live item
 * references - each refusal NAMES its blocking resource (部位/项目) so the console
 * surfaces it verbatim. Updates are D9 null-aware full-replaces.
 *
 * @author birdbro
 */
@Service
public class ExamBodyService {

    private final ExamBodyMapper examBodyMapper;
    private final ExamBodyGroupMapper examBodyGroupMapper;
    private final ExamItemMapper examItemMapper;
    private final ExamBodyGroupService examBodyGroupService;

    public ExamBodyService(ExamBodyMapper examBodyMapper,
            ExamBodyGroupMapper examBodyGroupMapper, ExamItemMapper examItemMapper,
            ExamBodyGroupService examBodyGroupService) {
        this.examBodyMapper = examBodyMapper;
        this.examBodyGroupMapper = examBodyGroupMapper;
        this.examItemMapper = examItemMapper;
        this.examBodyGroupService = examBodyGroupService;
    }

    /**
     * The modality's (optionally group-scoped) live bodies as an up_id-nested forest -
     * the item drawer's cascade feed and the 检查部位 page's table.
     */
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

    /** One body (the edit drawer's echo), or null when the id misses a live row. */
    public ExamBody getById(Integer id) {
        return examBodyMapper.selectById(id);
    }

    /** Creates a body; returns the database-generated id. */
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

    /** Full-replaces the editable surface (D9 null-aware); a missing id rejects A0400. */
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

    /**
     * Logical delete, guarded by live children AND live item references - each refusal
     * names its blocking resource.
     */
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
