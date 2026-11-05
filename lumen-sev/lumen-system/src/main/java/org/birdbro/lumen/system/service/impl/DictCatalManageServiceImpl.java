package org.birdbro.lumen.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.system.entity.DictCatalEntity;
import org.birdbro.lumen.system.dto.CatalCreate;
import org.birdbro.lumen.system.dto.CatalModify;
import org.birdbro.lumen.system.dto.CatalQuery;
import org.birdbro.lumen.system.service.IDictCatalService;
import org.birdbro.lumen.system.service.DictCatalManageService;
import org.springframework.stereotype.Service;

/**
 * Dictionary-catalog management implementation (system-data task 3.1). The copied
 * {@code IDictCatalService} (IService) stays the data engine; this layer owns the
 * lumen-side rules:
 *
 * <ul>
 *   <li>the duplicate-name rejection ({@code A0714}) mirrors
 *       {@code RoleServiceImpl.rejectDuplicateName} - live rows only, so the partial
 *       unique index {@code uk_dict_catal_name (name) WHERE deleted = 0} never fights
 *       the logical delete. The catalog {@code code} column (and its
 *       {@code uk_dict_catal_code} index) was dropped by V12 - the display name is the
 *       only human-facing key a catalog has left, so it carries the uniqueness;</li>
 *   <li>the {@code Byte}→{@code Integer} enable adaptation: the OPS-blood
 *       {@code CatalCreate.enable} is a {@code Byte} while the copied entity (and every
 *       lumen table) is Integer end to end - converted EXPLICITLY here and reported in
 *       the batch-2 report (handoff item 3; the detail pojo's enable is already
 *       Integer);</li>
 *   <li>the audit identity is NOT set here on purpose - the copied
 *       {@code MetaObjectConfig} fill owns it.</li>
 * </ul>
 *
 * @author birdbro
 */
@Service
public class DictCatalManageServiceImpl implements DictCatalManageService {

    private final IDictCatalService dictCatalService;

    public DictCatalManageServiceImpl(IDictCatalService dictCatalService) {
        this.dictCatalService = dictCatalService;
    }

    @Override
    public Integer create(CatalCreate request) {
        if (StrUtil.isBlank(request.getName())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "dict catalog name is required");
        }
        rejectDuplicateName(request.getName(), null);

        DictCatalEntity entity = new DictCatalEntity();
        BeanUtil.copyProperties(request, entity, true); // OPS house idiom - ignore nulls
        if (request.getEnable() != null) {
            // CatalCreate.enable is Byte (OPS lineage); lumen is Integer end to end.
            entity.setEnable(request.getEnable().intValue());
        }
        dictCatalService.save(entity);
        return entity.getId();
    }

    @Override
    public Boolean update(Integer id, CatalModify request) {
        if (dictCatalService.getById(id) == null) {
            throw new BusinessException(ExceptionEnum.ERROR_B0031,
                    "dict catalog id " + id + " does not exist");
        }
        if (request.getName() != null) {
            rejectDuplicateName(request.getName(), id);
        }

        DictCatalEntity patch = new DictCatalEntity();
        BeanUtil.copyProperties(request, patch, true);
        patch.setId(id); // the path param wins over the body
        if (request.getEnable() != null) {
            patch.setEnable(request.getEnable().intValue()); // Byte (OPS) -> Integer (lumen)
        }
        dictCatalService.updateById(patch);
        return Boolean.TRUE;
    }

    @Override
    public Boolean delete(Integer id) {
        dictCatalService.removeById(id); // @TableLogic -> UPDATE deleted = 1
        return Boolean.TRUE;
    }

    @Override
    public IPage<DictCatalEntity> list(CatalQuery query) {
        return dictCatalService.getPage(query);
    }

    /** The catalog name is unique among live rows; {@code excludeId} spares the updated row. */
    private void rejectDuplicateName(String name, Integer excludeId) {
        LambdaQueryWrapper<DictCatalEntity> wrapper =
                new LambdaQueryWrapper<DictCatalEntity>().eq(DictCatalEntity::getName, name);
        if (excludeId != null) {
            wrapper.ne(DictCatalEntity::getId, excludeId);
        }
        if (dictCatalService.count(wrapper) > 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0714,
                    "dict catalog name already exists: " + name);
        }
    }
}
