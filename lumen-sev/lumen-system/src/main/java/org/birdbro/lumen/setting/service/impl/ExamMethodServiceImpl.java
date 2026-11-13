package org.birdbro.lumen.setting.service.impl;

import java.util.List;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.setting.dto.MethodCreateRequest;
import org.birdbro.lumen.setting.dto.MethodPageQuery;
import org.birdbro.lumen.setting.dto.MethodUpdateRequest;
import org.birdbro.lumen.setting.entity.ExamItemMethod;
import org.birdbro.lumen.setting.entity.ExamMethod;
import org.birdbro.lumen.setting.mapper.ExamItemMethodMapper;
import org.birdbro.lumen.setting.mapper.ExamMethodMapper;
import org.birdbro.lumen.setting.service.ExamBodyGroupService;
import org.birdbro.lumen.setting.service.ExamMethodService;
import org.springframework.stereotype.Service;

/**
 * {@link ExamMethodService} implementation - the body is unchanged from the former
 * concrete class (2026-11-13 all-services interface+impl ruling; injection points keep
 * autowiring the interface FQN, so controllers and tests are untouched).
 *
 * @author birdbro
 */
@Service
public class ExamMethodServiceImpl implements ExamMethodService {

    private final ExamMethodMapper examMethodMapper;
    private final ExamItemMethodMapper examItemMethodMapper;
    private final ExamBodyGroupService examBodyGroupService;

    public ExamMethodServiceImpl(ExamMethodMapper examMethodMapper,
            ExamItemMethodMapper examItemMethodMapper,
            ExamBodyGroupService examBodyGroupService) {
        this.examMethodMapper = examMethodMapper;
        this.examItemMethodMapper = examItemMethodMapper;
        this.examBodyGroupService = examBodyGroupService;
    }

    @Override
    public IPage<ExamMethod> getPage(MethodPageQuery query) {
        examBodyGroupService.requireModality(query.getModality());
        LambdaQueryWrapper<ExamMethod> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamMethod::getModality, query.getModality());
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(ExamMethod::getName, query.getKeyword())
                    .or().like(ExamMethod::getCode, query.getKeyword()));
        }
        wrapper.orderByDesc(ExamMethod::getId);
        return examMethodMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                wrapper);
    }

    @Override
    public List<ExamMethod> getList(String modality, String keyword) {
        examBodyGroupService.requireModality(modality);
        LambdaQueryWrapper<ExamMethod> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamMethod::getModality, modality);
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(ExamMethod::getName, keyword)
                    .or().like(ExamMethod::getCode, keyword));
        }
        wrapper.orderByAsc(ExamMethod::getSort)
                .orderByAsc(ExamMethod::getId);
        return examMethodMapper.selectList(wrapper);
    }

    @Override
    public Integer create(MethodCreateRequest request) {
        examBodyGroupService.requireModality(request.getModality());
        rejectDuplicateCode(request.getModality(), request.getCode(), null);

        ExamMethod method = new ExamMethod();
        BeanUtil.copyProperties(request, method, true);
        examMethodMapper.insert(method);
        return method.getId();
    }

    @Override
    public Boolean update(Integer id, MethodUpdateRequest request) {
        ExamMethod current = examMethodMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "method id " + id + " does not exist");
        }
        examBodyGroupService.requireModality(request.getModality());
        rejectDuplicateCode(request.getModality(), request.getCode(), id);

        examMethodMapper.update(null, new LambdaUpdateWrapper<ExamMethod>()
                .eq(ExamMethod::getId, id)
                .set(ExamMethod::getModality, request.getModality())
                .set(ExamMethod::getName, request.getName())
                .set(ExamMethod::getCode, request.getCode())
                .set(ExamMethod::getSort, request.getSort())
                .set(ExamMethod::getEnable, request.getEnable()));
        return Boolean.TRUE;
    }

    @Override
    public Boolean delete(Integer id) {
        ExamMethod current = examMethodMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "method id " + id + " does not exist");
        }
        Long bindingCount = examItemMethodMapper.selectCount(
                new LambdaQueryWrapper<ExamItemMethod>().eq(ExamItemMethod::getMethodId, id));
        if (bindingCount != null && bindingCount > 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "该方法仍被 " + bindingCount + " 个检查项目使用，请先调整项目后再删除");
        }
        examMethodMapper.deleteById(id);
        return Boolean.TRUE;
    }

    // ===================== shared guards =====================

    /** The code uniqueness guard (scoped to the modality, live rows only, self-aware). */
    private void rejectDuplicateCode(String modality, String code, Integer selfId) {
        if (StrUtil.isBlank(code)) {
            return;
        }
        ExamMethod duplicate = examMethodMapper.selectOne(
                new LambdaQueryWrapper<ExamMethod>()
                        .eq(ExamMethod::getModality, modality)
                        .eq(ExamMethod::getCode, code));
        if (duplicate != null && !duplicate.getId().equals(selfId)) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "该检查类型下已存在同编码方法「" + code + "」");
        }
    }
}
