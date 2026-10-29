package org.birdbro.framework.log.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.birdbro.common.Constants.Constants;
import org.birdbro.common.core.pojo.LogSign;
import org.birdbro.common.utils.DateUtils;
import org.birdbro.framework.log.entity.LogSignEntity;
import org.birdbro.framework.log.mapper.LogSignMapper;
import org.birdbro.framework.log.request.LogRequest;
import org.birdbro.framework.log.service.ILogSignService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author birdbro
 * @since 2024-11-01
 */
@Slf4j
@Service
public class LogSignServiceImpl extends ServiceImpl<LogSignMapper, LogSignEntity> implements ILogSignService {


    @Override
    public boolean insert(LogSign sign) {
        LogSignEntity entity = LogSignEntity.builder()
                .dateYm(Integer.parseInt(DateUtils.dateTimeNow("yyyyMM")))
                .app(Constants.APP_CODE.toString())
                .build();
        BeanUtil.copyProperties(sign, entity,true);
        baseMapper.insertSign(entity);
        return true;
    }

    @Override
    public IPage<LogSignEntity> getLogSignPage(LogRequest req) {
        IPage<LogSignEntity> page = new Page<>(req.getPageNum(),req.getPageSize());
        return baseMapper.getLogSignPage(page,req);
    }
}
