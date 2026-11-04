package org.birdbro.framework.aspect;

import com.alibaba.fastjson2.JSON;
import eu.bitwalker.useragentutils.UserAgent;
import org.birdbro.framework.utils.UserAgents;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.birdbro.common.annotation.Log;
import org.birdbro.common.core.pojo.LogInfo;
import org.birdbro.common.core.pojo.user.UserLogin;
import org.birdbro.common.enums.HttpMethod;
import org.birdbro.common.filter.PropertyPreExcludeFilter;
import org.birdbro.common.utils.GadgetUtils;
import org.birdbro.common.utils.IpUtils;
import org.birdbro.common.utils.SecurityUtils;
import org.birdbro.common.utils.ServletUtils;
import org.birdbro.framework.manager.AsyncManager;
import org.birdbro.framework.manager.factory.AsyncFactory;
import org.springframework.core.NamedThreadLocal;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * 操作日志记录
 * AOP切面
 *
 * @author birdbro
 * @date 10:44 2024-05-30
 **/
@Slf4j
@Aspect
@Component
public class LogAspect {

    /**
     * 排除敏感属性字段
     */
    public static final String[] EXCLUDE_PROPERTIES = {"password", "oldPassword", "newPassword", "confirmPassword"};

    /**
     * 计算操作消耗时间
     */
    private static final ThreadLocal<Long> TIME_THREADLOCAL = new NamedThreadLocal<Long>("Cost Time");


    /**
     * 处理请求前执行
     */
    @Before(value = "@annotation(controllerLog)")
    public void boBefore(JoinPoint joinPoint, Log controllerLog) {
        TIME_THREADLOCAL.set(System.currentTimeMillis());
    }


    /**
     * 处理完请求后执行 回复
     *
     * @param joinPoint 切点
     */
    @AfterReturning(pointcut = "@annotation(controllerLog)", returning = "jsonResult")
    public void doAfterReturning(JoinPoint joinPoint, Log controllerLog, Object jsonResult) {
        handleLog(joinPoint, controllerLog, null, jsonResult);
    }

    /**
     * 拦截异常操作
     *
     * @param joinPoint 切点
     * @param e         异常
     */
    @AfterThrowing(value = "@annotation(controllerLog)", throwing = "e")
    public void doAfterThrowing(JoinPoint joinPoint, Log controllerLog, Exception e) {
        handleLog(joinPoint, controllerLog, e, null);
    }


    protected void handleLog(final JoinPoint joinPoint, Log controllerLog, final Exception e, Object jsonResult) {
        try {

            // 接收到请求，记录请求内容
            ServletRequestAttributes requestAttr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            assert requestAttr != null;
            HttpServletRequest request = requestAttr.getRequest();
            //请求地址
            String ip = IpUtils.getIpAddr(request);
            // lumen adaptation (10): null-safe UA parse - unversioned or missing headers are
            // the norm for API-first clients
            final UserAgent userAgent = UserAgents.fromHeader(
                    ServletUtils.getRequest().getHeader("User-Agent"));

            // 从切面织入点处通过反射机制获取织入点处的方法
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            // 获取切入点所在的方法
            Method method = signature.getMethod();

            LogInfo logInfo = LogInfo.builder()
                    .record(0)
                    .spent(System.currentTimeMillis() - TIME_THREADLOCAL.get())
                    .url(StringUtils.substring(request.getRequestURI(), 0, 255))
                    .method(request.getMethod())
                    .actionTime(new Date())
                    .pack(joinPoint.getSignature().toString())
                    .name(method.getName())
                    .ip(ip)
                    .os(userAgent.getOperatingSystem().getName())
                    .browser(userAgent.getBrowser().getName())
                    .engine(UserAgents.engine(userAgent))
                    .build();
            //记录操作用户信息
            UserLogin loginUser = SecurityUtils.getLoginUser();
            if (GadgetUtils.isNotNull(loginUser)) {
                logInfo.setUid(loginUser.getUser().getUid());
                logInfo.setAccount(loginUser.getUser().getAccount());
            }

            // 处理设置注解上的参数
            getControllerMethodDescription(joinPoint, controllerLog, logInfo, jsonResult);
            //异常
            if (e != null) {
                logInfo.setRecord(1);
                logInfo.setException(GadgetUtils.substring(e.getMessage(), 0, 2000));
            }
            // 保存数据库
            AsyncManager.me().execute(AsyncFactory.recordAction(logInfo));
        } catch (Exception exp) {
            // 记录本地异常日志
            log.error("异常信息:{}", exp.getMessage());
            exp.printStackTrace();
        } finally {
            TIME_THREADLOCAL.remove();
        }


    }


    /**
     * 获取注解中对方法的描述信息 用于Controller层注解
     *
     * @param log     日志
     * @param logInfo 操作日志
     * @throws Exception
     */
    public void getControllerMethodDescription(JoinPoint joinPoint, Log log, LogInfo logInfo, Object jsonResult) throws Exception {
        // 设置标题
        logInfo.setTitle(log.title());
        logInfo.setType(log.type());
        logInfo.setDescr(log.desc());

        // 是否需要保存request，参数和值
        if (log.isSaveRequestData()) {
            // 获取参数的信息，传入到数据库中。
            setRequestValue(joinPoint, logInfo, log.excludeParamNames());
        }
        // 是否需要保存response，参数和值
        if (log.isSaveResponseData() && GadgetUtils.isNotNull(jsonResult)) {
            logInfo.setResult(GadgetUtils.substring(JSON.toJSONString(jsonResult), 0, 2000));
        }
    }

    /**
     * 获取请求的参数，放到log中
     *
     * @param logInfo 操作日志
     * @throws Exception 异常
     */
    private void setRequestValue(JoinPoint joinPoint, LogInfo logInfo, String[] excludeParamNames) throws Exception {
        Map<?, ?> paramsMap = ServletUtils.getParamMap(ServletUtils.getRequest());
        String requestMethod = logInfo.getMethod();
        if (GadgetUtils.isEmpty(paramsMap)
                && (HttpMethod.PUT.name().equals(requestMethod) || HttpMethod.POST.name().equals(requestMethod))) {
            String params = argsArrayToString(joinPoint.getArgs(), excludeParamNames);
            logInfo.setParam(GadgetUtils.substring(params, 0, 2000));
        } else {
            logInfo.setParam(GadgetUtils.substring(JSON.toJSONString(paramsMap, excludePropertyPreFilter(excludeParamNames)), 0, 2000));
        }
    }

    /**
     * 参数拼装
     */
    private String argsArrayToString(Object[] paramsArray, String[] excludeParamNames) {
        StringBuilder params = new StringBuilder();
        if (paramsArray != null && paramsArray.length > 0) {
            for (Object o : paramsArray) {
                if (GadgetUtils.isNotNull(o) && !isFilterObject(o)) {
                    try {
                        String jsonObj = JSON.toJSONString(o, excludePropertyPreFilter(excludeParamNames));
                        params.append(jsonObj.toString());
                    } catch (Exception e) {
                        log.error("参数解析失败", e);
                    }
                }
            }
        }
        return params.toString().trim();
    }

    /**
     * 忽略敏感属性
     */
    public PropertyPreExcludeFilter excludePropertyPreFilter(String[] excludeParamNames) {
        return new PropertyPreExcludeFilter().addExcludes(ArrayUtils.addAll(EXCLUDE_PROPERTIES, excludeParamNames));
    }

    /**
     * 判断是否需要过滤的对象。
     *
     * @param o 对象信息。
     * @return 如果是需要过滤的对象，则返回true；否则返回false。
     */
    @SuppressWarnings("rawtypes")
    public boolean isFilterObject(final Object o) {
        Class<?> clazz = o.getClass();
        if (clazz.isArray()) {
            return clazz.getComponentType().isAssignableFrom(MultipartFile.class);
        } else if (Collection.class.isAssignableFrom(clazz)) {
            Collection collection = (Collection) o;
            for (Object value : collection) {
                return value instanceof MultipartFile;
            }
        } else if (Map.class.isAssignableFrom(clazz)) {
            Map map = (Map) o;
            for (Object value : map.entrySet()) {
                Map.Entry entry = (Map.Entry) value;
                return entry.getValue() instanceof MultipartFile;
            }
        }
        return o instanceof MultipartFile || o instanceof HttpServletRequest || o instanceof HttpServletResponse
                || o instanceof BindingResult;
    }
}
