package org.birdbro.framework.log.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
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
}
