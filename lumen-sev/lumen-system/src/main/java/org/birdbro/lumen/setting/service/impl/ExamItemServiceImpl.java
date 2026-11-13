package org.birdbro.lumen.setting.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.setting.dto.ItemCreateRequest;
import org.birdbro.lumen.setting.dto.ItemPageQuery;
import org.birdbro.lumen.setting.dto.ItemUpdateRequest;
import org.birdbro.lumen.setting.dto.ItemVO;
import org.birdbro.lumen.setting.entity.ExamBody;
import org.birdbro.lumen.setting.entity.ExamBodyGroup;
import org.birdbro.lumen.setting.entity.ExamItem;
import org.birdbro.lumen.setting.entity.ExamItemMethod;
import org.birdbro.lumen.setting.entity.ExamMethod;
import org.birdbro.lumen.setting.mapper.ExamBodyGroupMapper;
import org.birdbro.lumen.setting.mapper.ExamBodyMapper;
import org.birdbro.lumen.setting.mapper.ExamItemMapper;
import org.birdbro.lumen.setting.mapper.ExamItemMethodMapper;
import org.birdbro.lumen.setting.mapper.ExamMethodMapper;
import org.birdbro.lumen.setting.service.ExamBodyGroupService;
import org.birdbro.lumen.setting.service.ExamBodyService;
import org.birdbro.lumen.setting.service.ExamItemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ExamItemService} implementation - the body is unchanged from the former
 * concrete class (2026-11-13 all-services interface+impl ruling; injection points keep
 * autowiring the interface FQN, so controllers and tests are untouched).
 *
 * @author birdbro
 */
@Service
public class ExamItemServiceImpl implements ExamItemService {

    private final ExamItemMapper examItemMapper;
    private final ExamItemMethodMapper examItemMethodMapper;
    private final ExamBodyMapper examBodyMapper;
    private final ExamBodyGroupMapper examBodyGroupMapper;
    private final ExamMethodMapper examMethodMapper;
    private final ExamBodyService examBodyService;
    private final ExamBodyGroupService examBodyGroupService;

    public ExamItemServiceImpl(ExamItemMapper examItemMapper,
            ExamItemMethodMapper examItemMethodMapper, ExamBodyMapper examBodyMapper,
            ExamBodyGroupMapper examBodyGroupMapper, ExamMethodMapper examMethodMapper,
            ExamBodyService examBodyService, ExamBodyGroupService examBodyGroupService) {
        this.examItemMapper = examItemMapper;
        this.examItemMethodMapper = examItemMethodMapper;
        this.examBodyMapper = examBodyMapper;
        this.examBodyGroupMapper = examBodyGroupMapper;
        this.examMethodMapper = examMethodMapper;
        this.examBodyService = examBodyService;
        this.examBodyGroupService = examBodyGroupService;
    }

    @Override
    public IPage<ItemVO> getPage(ItemPageQuery query) {
        examBodyGroupService.requireModality(query.getModality());

        LambdaQueryWrapper<ExamItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamItem::getModality, query.getModality());
        if (query.getGroupId() != null) {
            List<Integer> bodyIds = examBodyMapper.selectList(
                            new LambdaQueryWrapper<ExamBody>()
                                    .eq(ExamBody::getGroupId, query.getGroupId()))
                    .stream().map(ExamBody::getId).collect(Collectors.toList());
            if (bodyIds.isEmpty()) {
                return new Page<>(query.getPageNum(), query.getPageSize());
            }
            wrapper.in(ExamItem::getBodyId, bodyIds);
        }
        if (query.getIsCommon() != null) {
            wrapper.eq(ExamItem::getIsCommon, query.getIsCommon());
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(ExamItem::getExam, query.getKeyword())
                    .or().like(ExamItem::getCode, query.getKeyword()));
        }
        wrapper.orderByDesc(ExamItem::getId);

        IPage<ExamItem> page = examItemMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<ItemVO> records = assembleVOs(page.getRecords());
        Page<ItemVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    public ItemVO getById(Integer id) {
        ExamItem item = examItemMapper.selectById(id);
        if (item == null) {
            return null;
        }
        return assembleVOs(Collections.singletonList(item)).get(0);
    }

    @Override
    @Transactional
    public Integer create(ItemCreateRequest request) {
        ExamBody body = requireBodyOfModality(request.getBodyId(), request.getModality());
        rejectDuplicateCode(request.getModality(), request.getCode(), null);

        ExamItem item = new ExamItem();
        BeanUtil.copyProperties(request, item, true);
        item.setModality(body.getModality());
        examItemMapper.insert(item);
        rewriteBindings(item.getId(), request.getMethodIds());
        return item.getId();
    }

    @Override
    @Transactional
    public Boolean update(Integer id, ItemUpdateRequest request) {
        ExamItem current = examItemMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "exam item id " + id + " does not exist");
        }
        requireBodyOfModality(request.getBodyId(), request.getModality());
        rejectDuplicateCode(request.getModality(), request.getCode(), id);

        examItemMapper.update(null, new LambdaUpdateWrapper<ExamItem>()
                .eq(ExamItem::getId, id)
                .set(ExamItem::getModality, request.getModality())
                .set(ExamItem::getExam, request.getExam())
                .set(ExamItem::getCode, request.getCode())
                .set(ExamItem::getBodyId, request.getBodyId())
                .set(ExamItem::getPrice, request.getPrice())
                .set(ExamItem::getRate, request.getRate())
                .set(ExamItem::getTag, request.getTag())
                .set(ExamItem::getScoreRep, request.getScoreRep())
                .set(ExamItem::getScoreAud, request.getScoreAud())
                .set(ExamItem::getIsCommon, request.getIsCommon())
                .set(ExamItem::getSort, request.getSort())
                .set(ExamItem::getEnable, request.getEnable())
                .set(ExamItem::getRemark, request.getRemark()));
        rewriteBindings(id, request.getMethodIds());
        return Boolean.TRUE;
    }

    @Override
    public Boolean delete(Integer id) {
        ExamItem current = examItemMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "exam item id " + id + " does not exist");
        }
        examItemMapper.deleteById(id);
        return Boolean.TRUE;
    }

    // ===================== internals =====================

    /**
     * The binding rewrite: PHYSICAL wipe by exam_id + batch insert, in the item's
     * transaction. Duplicates inside the request collapse (a LinkedHashSet keeps the
     * request order for stable display).
     */
    private void rewriteBindings(Integer examId, List<Integer> methodIds) {
        examItemMethodMapper.deleteByExamId(examId);
        if (CollUtil.isEmpty(methodIds)) {
            return;
        }
        for (Integer methodId : new LinkedHashSet<>(methodIds)) {
            examItemMethodMapper.insert(ExamItemMethod.builder()
                    .examId(examId)
                    .methodId(methodId)
                    .build());
        }
    }

    /** The body anchor: live, and its modality in lockstep with the request's. */
    private ExamBody requireBodyOfModality(Integer bodyId, String modality) {
        examBodyGroupService.requireModality(modality);
        ExamBody body = examBodyMapper.selectById(bodyId);
        if (body == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "检查部位 " + bodyId + " 不存在");
        }
        if (!body.getModality().equals(modality)) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "项目检查类型 " + modality + " 与部位检查类型 " + body.getModality()
                            + " 不一致");
        }
        return body;
    }

    /** The code uniqueness guard (scoped to the modality, live rows only, self-aware). */
    private void rejectDuplicateCode(String modality, String code, Integer selfId) {
        if (StrUtil.isBlank(code)) {
            return;
        }
        ExamItem duplicate = examItemMapper.selectOne(new LambdaQueryWrapper<ExamItem>()
                .eq(ExamItem::getModality, modality)
                .eq(ExamItem::getCode, code));
        if (duplicate != null && !duplicate.getId().equals(selfId)) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "该检查类型下已存在同编码项目「" + code + "」");
        }
    }

    /**
     * The D2 display assembly: one query per referenced table (bodies, groups, methods)
     * - catalog-scale batches, never per-row queries; the method names follow the
     * binding insertion order (the request order the console submitted).
     */
    private List<ItemVO> assembleVOs(List<ExamItem> items) {
        if (CollUtil.isEmpty(items)) {
            return new ArrayList<>();
        }
        Set<Integer> bodyIds = items.stream().map(ExamItem::getBodyId)
                .collect(Collectors.toSet());
        Map<Integer, ExamBody> bodies = bodyIds.isEmpty() ? Map.of()
                : examBodyMapper.selectBatchIds(bodyIds).stream()
                        .collect(Collectors.toMap(ExamBody::getId, Function.identity()));

        Set<Integer> groupIds = bodies.values().stream().map(ExamBody::getGroupId)
                .collect(Collectors.toSet());
        Map<Integer, ExamBodyGroup> groups = groupIds.isEmpty() ? Map.of()
                : examBodyGroupMapper.selectBatchIds(groupIds).stream()
                        .collect(Collectors.toMap(ExamBodyGroup::getId, Function.identity()));

        Set<Integer> examIds = items.stream().map(ExamItem::getId)
                .collect(Collectors.toSet());
        Map<Integer, List<Integer>> methodIdsByExam =
                examItemMethodMapper.selectList(new LambdaQueryWrapper<ExamItemMethod>()
                                .in(ExamItemMethod::getExamId, examIds)
                                .orderByAsc(ExamItemMethod::getId))
                        .stream()
                        .collect(Collectors.groupingBy(ExamItemMethod::getExamId,
                                Collectors.mapping(ExamItemMethod::getMethodId,
                                        Collectors.toList())));
        Set<Integer> methodIds = methodIdsByExam.values().stream()
                .flatMap(List::stream).collect(Collectors.toSet());
        Map<Integer, ExamMethod> methods = methodIds.isEmpty() ? Map.of()
                : examMethodMapper.selectBatchIds(methodIds).stream()
                        .collect(Collectors.toMap(ExamMethod::getId, Function.identity()));

        List<ItemVO> vos = new ArrayList<>(items.size());
        for (ExamItem item : items) {
            ItemVO vo = new ItemVO();
            BeanUtil.copyProperties(item, vo);
            ExamBody body = bodies.get(item.getBodyId());
            if (body != null) {
                vo.setBodyName(body.getName());
                ExamBodyGroup group = groups.get(body.getGroupId());
                vo.setGroupName(group != null ? group.getName() : null);
            }
            List<Integer> ids = methodIdsByExam.getOrDefault(item.getId(),
                    Collections.emptyList());
            vo.setMethodIds(ids);
            vo.setMethodNames(ids.stream()
                    .map(methodId -> {
                        ExamMethod method = methods.get(methodId);
                        return method != null ? method.getName() : null;
                    })
                    .filter(StrUtil::isNotEmpty)
                    .collect(Collectors.joining(",")));
            vos.add(vo);
        }
        return vos;
    }
}
