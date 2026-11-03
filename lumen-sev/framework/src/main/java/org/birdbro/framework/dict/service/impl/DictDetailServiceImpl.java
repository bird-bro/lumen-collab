package org.birdbro.framework.dict.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.birdbro.common.core.pojo.IdList;
import org.birdbro.common.utils.GadgetUtils;
import org.birdbro.framework.dict.entity.DictDetailEntity;
import org.birdbro.framework.dict.mapper.DictDetailMapper;
import org.birdbro.framework.dict.pojo.request.DetailQuery;
import org.birdbro.framework.dict.service.IDictDetailService;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.birdbro.framework.config.RedisConfig.CACHE_DICT;

/**
 * <p>
 * 字典数据 服务实现类
 * </p>
 *
 * @author birdbro
 * @since 2024-06-04
 */
@Slf4j
@Service
@CacheConfig(cacheNames = CACHE_DICT)
public class DictDetailServiceImpl extends ServiceImpl<DictDetailMapper, DictDetailEntity> implements IDictDetailService {




    @Override
    @CacheEvict(key = "'listByCatalId' + #id")
    public boolean modify(Integer id, Integer enable) {
        return this.update(
                Wrappers.<DictDetailEntity>lambdaUpdate()
                        .set(DictDetailEntity::getEnable, enable)
                        .eq(DictDetailEntity::getId, id)
        );
    }


    /**
     * 优化：批量查询后内存分组，避免N+1查询
     */
    @Override
    public Map<Integer, List<DictDetailEntity>> mapByCatalIds(IdList query) {
        List<Integer> ids = query.getIds();
        // 单次批量查询
        // lumen enable semantics: 1 = live (the OPS copy used EnableConstants.TRUE = 11;
        // copy adaptation recorded in the batch report - lumen tables seed 1/0)
        List<DictDetailEntity> allDetails = list(
                Wrappers.<DictDetailEntity>lambdaQuery()
                        .in(DictDetailEntity::getCaId, ids)
                        .eq(DictDetailEntity::getEnable, 1)
                        .orderByAsc(DictDetailEntity::getSort)
                        .orderByAsc(DictDetailEntity::getCode)
        );
        // 内存分组
        Map<Integer, List<DictDetailEntity>> map = new HashMap<>();
        for (Integer id : ids) {
            map.put(id, allDetails.stream()
                    .filter(item -> item.getCaId().equals(id))
                    .collect(java.util.stream.Collectors.toList()));
        }
        return map;
    }


    @Override
    public Map<Integer, String> valueMapByCatalId(Integer catalId) {
        return list(Wrappers.<DictDetailEntity>lambdaQuery()
                        .eq(DictDetailEntity::getCaId, catalId)
                        .eq(DictDetailEntity::getEnable, 1)
                        .orderByAsc(DictDetailEntity::getSort)
                        .orderByAsc(DictDetailEntity::getCode))
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        DictDetailEntity::getCode, DictDetailEntity::getValue, (first, second) -> first));
    }


    @Override
    public IPage<DictDetailEntity> getPage(DetailQuery query) {
        Page<DictDetailEntity> page = new Page<>(query.getPageNum(), query.getPageSize());
        return this.page(page, Wrappers.<DictDetailEntity>lambdaQuery()
                .eq(GadgetUtils.isNotNull(query.getCaId()), DictDetailEntity::getCaId, query.getCaId())
                .like(GadgetUtils.isNotNull(query.getValue()), DictDetailEntity::getValue, query.getValue())
                .orderByAsc(DictDetailEntity::getSort)
        );
    }





}
