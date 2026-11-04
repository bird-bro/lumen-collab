package org.birdbro.lumen.system.service.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import org.birdbro.common.utils.TreeUtils;
import org.birdbro.lumen.system.entity.DictCityEntity;
import org.birdbro.lumen.system.mapper.DictCityMapper;
import org.birdbro.lumen.system.service.IDictCityService;
import org.springframework.stereotype.Service;

/**
 * Read-only implementation over {@code public.dict_city} (dict-city-reference task
 * 1.2, design D3): the tree assembly reuses the org-tree mechanism ({@code TreeUtils}
 * keyed on {@code upId = 0} - same posture as {@code OrgTreeService}), the list applies
 * the optional level/upId filters. The entity carries no audit block and no logic-delete
 * field, so the full scan returns the complete frozen seed. NO mutation methods - the
 * seed migrations are the data source.
 *
 * @author birdbro
 */
@Service
public class DictCityServiceImpl extends ServiceImpl<DictCityMapper, DictCityEntity>
        implements IDictCityService {

    @Override
    public List<DictCityEntity> tree() {
        return TreeUtils.buildTree(this.list(),
                DictCityEntity::getId, DictCityEntity::getUpId, 0L,
                DictCityEntity::getChildren, DictCityEntity::setChildren);
    }

    @Override
    public List<DictCityEntity> list(Integer level, Integer upId) {
        return this.list(Wrappers.<DictCityEntity>lambdaQuery()
                .eq(level != null, DictCityEntity::getLevel, level)
                .eq(upId != null, DictCityEntity::getUpId, upId)
                .orderByAsc(DictCityEntity::getId));
    }
}
