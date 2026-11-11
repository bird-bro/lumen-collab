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


    /**
     * 操作日志分页查询：type / uid / dateYm 全部可选过滤，actionTime 倒序（id 倒序兜底
     * 同秒稳定性），deleted=0 与拷贝 XML 的裸 SQL 过滤同款。lumen 端点 GET /api/v1/system/logs
     * 的数据源（system-data task 3.2；加法式新方法，原 getLogInfoPage(LogRequest) 的
     * account/时间窗查询保持不动）。
     *
     * @author: birdbro
     * @since: 2026-11-03
     * @param: pageNum 页码（null=1）；pageSize 每页条数（null=20）
     * @param: type 操作类型（@Log 注解 type，可空）；uid 操作者（可空）；dateYm 年月键 yyyyMM（可空）
     * @return: 分页结果
     **/
    IPage<LogInfoEntity> getPage(Integer pageNum, Integer pageSize, String type, Integer uid, Integer dateYm);

    /**
     * 审计日志分页查询（audit-log-unify-api design D5/D8；加法式重载，取代上方 String
     * type 版本——V25 后 log_info.type 是 Integer 码，String 重载的等值查询对整型列已
     * 无意义）：type(Integer 码) / uid / account / dateYm 全部可选过滤，actionTime
     * 倒序（id 倒序兜底同秒稳定性），deleted=0 同款；select 排除 session 列——登录
     * 事件的凭证锚点（UUID）只存不出（设计 D5，取代 sign-log-query D7 的 token 列
     * 排除，同保证换新列）。GET /api/v1/system/logs 的唯一数据源。
     *
     * @author: birdbro
     * @since: 2026-11-10
     * @param: pageNum 页码（null=1）；pageSize 每页条数（null=20）
     * @param: type 审计类型码（可空）；uid 操作者（可空）；account 账号（可空，空串视同未传）；dateYm 年月键 yyyyMM（可空）
     * @return: 分页结果（不含 session 列）
     **/
    IPage<LogInfoEntity> getPage(Integer pageNum, Integer pageSize, Integer type, Integer uid, String account, Integer dateYm);
}
