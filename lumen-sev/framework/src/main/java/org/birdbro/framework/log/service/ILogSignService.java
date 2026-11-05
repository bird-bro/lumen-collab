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
 * @author birdbro
 * @since 2024-11-01
 */
public interface ILogSignService extends IService<LogSignEntity> {

    boolean insert(LogSign sign);

    IPage<LogSignEntity> getLogSignPage(LogRequest req);


    /**
     * 登录日志分页查询：account / uid / dateYm 全部可选过滤，actionTime 倒序（id 倒序兜底
     * 同秒稳定性），deleted=0 与拷贝 XML 的裸 SQL 过滤同款；select 排除 token 列——凭证材料
     * 永不出服务层（sign-log-query 设计 D7）。lumen 端点 GET /api/v1/system/logs/sign 的
     * 数据源（sign-log-query task 2.2；加法式新方法，原 getLogSignPage(LogRequest) 的
     * account/时间窗查询保持不动）。
     *
     * @author: birdbro
     * @since: 2026-11-05
     * @param: pageNum 页码（null=1）；pageSize 每页条数（null=20）
     * @param: account 登录账号（可空，空串视同未传）；uid 操作人（可空）；dateYm 年月键 yyyyMM（可空）
     * @return: 分页结果（不含 token 列）
     **/
    IPage<LogSignEntity> getPage(Integer pageNum, Integer pageSize, String account, Integer uid, Integer dateYm);
}
