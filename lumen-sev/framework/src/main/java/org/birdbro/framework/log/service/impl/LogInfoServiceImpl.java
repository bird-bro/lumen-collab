package org.birdbro.framework.log.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.birdbro.common.Constants.Constants;
import org.birdbro.common.core.pojo.LogInfo;
import org.birdbro.common.utils.DateUtils;
import org.birdbro.framework.log.entity.LogInfoEntity;
import org.birdbro.framework.log.mapper.LogInfoMapper;
import org.birdbro.framework.log.request.LogRequest;
import org.birdbro.framework.log.service.ILogInfoService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

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
public class LogInfoServiceImpl extends ServiceImpl<LogInfoMapper, LogInfoEntity> implements ILogInfoService {


    @Override
    public boolean insert(LogInfo info) {
        LogInfoEntity entity = LogInfoEntity.builder()
                .dateYm(Integer.parseInt(DateUtils.dateTimeNow("yyyyMM")))
                .app(Constants.APP_CODE.toString())
                .build();
        BeanUtil.copyProperties(info, entity,true);
        baseMapper.insertInfo(entity);
        return true;
    }

    @Override
    public IPage<LogInfoEntity> getLogInfoPage(LogRequest req) {
        IPage<LogInfoEntity> page = new Page<>(req.getPageNum(),req.getPageSize());
        return baseMapper.getLogInfoPage(page,req);
    }


    /**
     * 实体无 @TableLogic（拷贝件只在 XML 里裸 SQL 过滤），此处用同款 deleted = 0 原文条件；
     * type 过滤用 StrUtil.isNotBlank（空串视同未传）。
     */
    @Override
    public IPage<LogInfoEntity> getPage(Integer pageNum, Integer pageSize, String type, Integer uid, Integer dateYm) {
        Page<LogInfoEntity> page = new Page<>(
                pageNum == null ? 1 : Math.max(1, pageNum),
                pageSize == null ? 20 : Math.max(1, pageSize));
        return this.page(page, Wrappers.<LogInfoEntity>lambdaQuery()
                .eq(StrUtil.isNotBlank(type), LogInfoEntity::getType, type)
                .eq(uid != null, LogInfoEntity::getUid, uid)
                .eq(dateYm != null, LogInfoEntity::getDateYm, dateYm)
                .apply("deleted = 0")
                .orderByDesc(LogInfoEntity::getActionTime)
                .orderByDesc(LogInfoEntity::getId)
        );
    }
}
