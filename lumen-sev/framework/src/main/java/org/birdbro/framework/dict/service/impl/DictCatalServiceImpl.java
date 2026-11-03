package org.birdbro.framework.dict.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.birdbro.common.utils.GadgetUtils;
import org.birdbro.framework.dict.entity.DictCatalEntity;
import org.birdbro.framework.dict.mapper.DictCatalMapper;
import org.birdbro.framework.dict.pojo.request.CatalQuery;
import org.birdbro.framework.dict.service.IDictCatalService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author birdbro
 * @since 2024-05-07
 */
@Slf4j
@Service
public class DictCatalServiceImpl extends ServiceImpl<DictCatalMapper, DictCatalEntity> implements IDictCatalService {


    @Override
    public boolean modify(Integer id, Integer enable) {
        return this.update(
                Wrappers.<DictCatalEntity>lambdaUpdate()
                        .set(DictCatalEntity::getEnable, enable)
                        .eq(DictCatalEntity::getId, id)
        );
    }


    @Override
    public IPage<DictCatalEntity> getPage(CatalQuery query) {
        Page<DictCatalEntity> page = new Page<>(query.getPageNum(), query.getPageSize());
        return this.page(page, Wrappers.<DictCatalEntity>lambdaQuery()
                .eq(GadgetUtils.isNotNull(query.getCls()), DictCatalEntity::getCls, query.getCls())
                .like(GadgetUtils.isNotEmpty(query.getName()), DictCatalEntity::getName, query.getName())
                .orderByDesc(DictCatalEntity::getId)
        );
    }
}
