package org.birdbro.framework.manager.factory;


import cn.hutool.core.util.ObjUtil;
import eu.bitwalker.useragentutils.UserAgent;
import lombok.extern.slf4j.Slf4j;
import org.birdbro.common.core.pojo.LogInfo;
import org.birdbro.common.core.pojo.LogSign;
import org.birdbro.common.core.pojo.user.UserLogin;
import org.birdbro.common.utils.GadgetUtils;
import org.birdbro.common.utils.IpUtils;
import org.birdbro.common.utils.ServletUtils;
import org.birdbro.common.utils.SpringUtils;
import org.birdbro.framework.log.service.ILogInfoService;
import org.birdbro.framework.log.service.ILogSignService;

import java.util.Date;
import java.util.TimerTask;

/**
 * 异步工厂（产生任务用）
 *
 * @author ruoyi
 */
@Slf4j
public class AsyncFactory {

    /**
     * 记录登录信息
     *
     * @param account 用户名
     * @param status  状态
     * @param message 消息
     * @param args    列表
     * @return 任务task
     */
    public static TimerTask recordForSign(final String account,
                                          final Integer status,
                                          final String message,
                                          final String descr,
                                          final Object... args) {
        final UserAgent userAgent = UserAgent.parseUserAgentString(ServletUtils.getRequest().getHeader("User-Agent"));
        final String ip = IpUtils.getIpAddr();
        return new TimerTask() {
            @Override
            public void run() {
                // 获取客户端操作系统
                String os = userAgent.getOperatingSystem().getName();
                // 获取客户端浏览器
                String browser = userAgent.getBrowser().getName();

                StringBuilder str = new StringBuilder();
                str.append(GadgetUtils.getBlock(ip));
                str.append(GadgetUtils.getBlock(account));
                str.append(GadgetUtils.getBlock(status));
                str.append(GadgetUtils.getBlock(descr));
                // 打印信息到日志
                log.info(str.toString(), args);

                UserLogin userLogin = new UserLogin();
                if (ObjUtil.isNotEmpty(args)) {
                    userLogin = (UserLogin) args[0];
                    log.info("token:{};uid:{}", userLogin.getToken(), userLogin.getUserId());
                }

                // 封装对象
                LogSign logSign = LogSign.builder()
                        .msg(message)
                        .descr(descr)
                        .token(userLogin.getToken())
                        .uid(userLogin.getUserId())
                        .account(account)
                        .ip(ip)
                        //.remark(IpUtils.getRealAddressByIP(ip))
                        .browser(browser)
                        .os(os)
                        .engine(userAgent.getBrowserVersion().getVersion())
                        .actionTime(new Date())
                        .type(status)
                        .build();
                // 插入数据
                SpringUtils.getBean(ILogSignService.class).insert(logSign);
            }
        };
    }

    /**
     * AOP-日志记录
     *
     * @param logInfo 操作日志信息
     * @return 任务task
     */
    public static TimerTask recordAction(final LogInfo logInfo) {
        return new TimerTask() {
            @Override
            public void run() {
                SpringUtils.getBean(ILogInfoService.class).insert(logInfo);
            }
        };
    }
}
