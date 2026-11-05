package org.birdbro.lumen.system.service.impl;

import java.util.List;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.system.entity.DictCatalEntity;
import org.birdbro.lumen.system.entity.DictDetailEntity;
import org.birdbro.lumen.system.dto.DetailCreate;
import org.birdbro.lumen.system.dto.DetailModify;
import org.birdbro.lumen.system.service.IDictCatalService;
import org.birdbro.lumen.system.service.IDictDetailService;
import org.birdbro.lumen.system.service.DictDetailManageService;
import org.springframework.stereotype.Service;

/**
 * Dictionary-detail management implementation (system-data task 3.1). The copied
 * {@code IDictDetailService} (IService) stays the data engine; this layer owns the
 * lumen-side rules:
 *
 * <ul>
 *   <li>the detail anchors to its catalog by {@code ca_id} alone (V9 dropped the
 *       denormalized {@code ca_code} column); the referenced catalog must be live on
 *       every create/re-anchor;</li>
 *   <li>the detail-level uniqueness is the (caId, value) pair - the DDL's partial
 *       unique index {@code uk_dict_detail_ca_id_value WHERE deleted = 0} - rejected
 *       with {@code A0714} at the service level, live rows only;</li>
 *   <li>the enable field flows Integer end to end here ({@code DetailCreate.enable} is
 *       already Integer - the Byte quirk is catal-side only, see
 *       {@code DictCatalServiceImpl}).</li>
 * </ul>
 *
 * @author birdbro
 */
@Service
public class DictDetailManageServiceImpl implements DictDetailManageService {

    private final IDictCatalService dictCatalService;
    private final IDictDetailService dictDetailService;

    public DictDetailManageServiceImpl(IDictCatalService dictCatalService, IDictDetailService dictDetailService) {
        this.dictCatalService = dictCatalService;
        this.dictDetailService = dictDetailService;
    }

    @Override
    public Integer create(DetailCreate request) {
        if (request.getCaId() == null || StrUtil.isBlank(request.getValue())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "dict detail caId and value are required");
        }
        DictCatalEntity catal = liveCatal(request.getCaId());
        rejectDuplicateValue(catal.getId(), request.getValue(), null);

        DictDetailEntity entity = new DictDetailEntity();
        BeanUtil.copyProperties(request, entity, true); // OPS house idiom - ignore nulls
        dictDetailService.save(entity);
        return entity.getId();
    }

    @Override
    public Boolean update(Integer id, DetailModify request) {
        DictDetailEntity existing = dictDetailService.getById(id);
        if (existing == null) {
            throw new BusinessException(ExceptionEnum.ERROR_B0031,
                    "dict detail id " + id + " does not exist");
        }
        // A re-anchor must resolve to a live catalog.
        Integer targetCaId = request.getCaId() != null ? request.getCaId() : existing.getCaId();
        DictCatalEntity catal = liveCatal(targetCaId);
        String targetValue = request.getValue() != null ? request.getValue() : existing.getValue();
        rejectDuplicateValue(catal.getId(), targetValue, id);

        DictDetailEntity patch = new DictDetailEntity();
        BeanUtil.copyProperties(request, patch, true);
        patch.setId(id); // the path param wins over the body
        dictDetailService.updateById(patch);
        return Boolean.TRUE;
    }

    @Override
    public Boolean delete(Integer id) {
        dictDetailService.removeById(id); // @TableLogic -> UPDATE deleted = 1
        return Boolean.TRUE;
    }

    @Override
    public List<DictDetailEntity> listByCaId(Integer caId) {
        return dictDetailService.listByCaId(caId);
    }

    /** Resolves the LIVE catalog behind a caId ({@code @TableLogic} hides deleted ones). */
    private DictCatalEntity liveCatal(Integer caId) {
        DictCatalEntity catal = dictCatalService.getById(caId);
        if (catal == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "dict catalog does not exist: caId " + caId);
        }
        return catal;
    }

    /**
     * The (caId, value) pair is unique among live rows (the partial unique index shape);
     * a blank value is skipped - NULL values stay distinct in the index, so the check
     * mirrors the DDL exactly. {@code excludeId} spares the updated row.
     */
    private void rejectDuplicateValue(Integer caId, String value, Integer excludeId) {
        if (StrUtil.isBlank(value)) {
            return;
        }
        LambdaQueryWrapper<DictDetailEntity> wrapper = new LambdaQueryWrapper<DictDetailEntity>()
                .eq(DictDetailEntity::getCaId, caId)
                .eq(DictDetailEntity::getValue, value);
        if (excludeId != null) {
            wrapper.ne(DictDetailEntity::getId, excludeId);
        }
        if (dictDetailService.count(wrapper) > 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0714,
                    "dict detail value already exists under caId " + caId + ": " + value);
        }
    }
}
