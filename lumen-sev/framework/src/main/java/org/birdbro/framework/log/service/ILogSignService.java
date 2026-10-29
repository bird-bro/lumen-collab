package org.birdbro.framework.log.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.birdbro.common.core.pojo.LogSign;
import org.birdbro.framework.log.entity.LogSignEntity;
import org.birdbro.framework.log.request.LogRequest;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author luzw
 * @since 2024-11-01
 */
public interface ILogSignService extends IService<LogSignEntity> {

    boolean insert(LogSign sign);

    IPage<LogSignEntity> getLogSignPage(LogRequest req);
}
