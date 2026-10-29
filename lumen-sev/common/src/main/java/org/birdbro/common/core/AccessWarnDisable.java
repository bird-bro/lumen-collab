package org.birdbro.common.core;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 禁用 或 忽略 一些
 *
 * @author birdbro
 * @date 9:57 2022-4-28
 **/
@Slf4j
public class AccessWarnDisable {


    /**
     * 忽略非法反射警告  适用于jdk11
     *
     * @author: birdbro
     * @date: 2022-4-28 9:57
     **/
    @SuppressWarnings("unchecked")
    public static void disableIllegalAccessWarnings() {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field field = unsafeClass.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            Object unsafe = field.get(null);

            Method putObjectVolatile = unsafeClass.getDeclaredMethod("putObjectVolatile", Object.class, long.class, Object.class);
            Method staticFieldOffset = unsafeClass.getDeclaredMethod("staticFieldOffset", Field.class);

            Class<?> loggerClass = Class.forName("jdk.internal.module.IllegalAccessLogger");
            Field loggerField = loggerClass.getDeclaredField("logger");
            Long offset = (Long) staticFieldOffset.invoke(unsafe, loggerField);
            putObjectVolatile.invoke(unsafe, loggerClass, offset, null);
        } catch (Exception e) {
            log.error("AccessWarnings: " + e.getMessage());
        }
    }

}
