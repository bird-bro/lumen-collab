package org.birdbro.framework.log.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.birdbro.common.core.pojo.LogInfo;
import org.birdbro.framework.log.entity.LogInfoEntity;
import org.birdbro.framework.log.request.LogRequest;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author birdbro
 * @since 2024-05-07
 */
public interface ILogInfoService extends IService<LogInfoEntity> {


    boolean insert(LogInfo info);

    IPage<LogInfoEntity> getLogInfoPage(LogRequest req);
}
