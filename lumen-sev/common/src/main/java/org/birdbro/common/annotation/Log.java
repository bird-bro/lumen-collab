package org.birdbro.common.annotation;

import java.lang.annotation.*;

/**
 * 日志注解
 *
 * @author birdbro
 * @date 13:58 2022-6-10
 **/
@Target({ElementType.PARAMETER, ElementType.METHOD}) //注解放置的目标位置，METHOD是可注解在方法级别上
@Retention(RetentionPolicy.RUNTIME) //注解在哪个阶段执行
@Documented
public @interface Log {

    /**
     * 操作模块
     **/
    String title() default "";

    /**
     * 操作类型
     **/
    String type() default "";

    /**
     * 操作说明
     **/
    String desc() default "";


    /**
     * 是否保存请求的参数
     */
    public boolean isSaveRequestData() default true;

    /**
     * 是否保存响应的参数
     */
    public boolean isSaveResponseData() default true;

    /**
     * 排除指定的请求参数
     */
    public String[] excludeParamNames() default {};

}
