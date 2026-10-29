package org.birdbro.common.exception;

import org.birdbro.common.enums.ExceptionEnum;

import java.util.Collection;
import java.util.Objects;

/**
 * 业务异常断言工具类
 * 简化异常抛出逻辑，避免大量 if 判断嵌套
 *
 * 使用示例：
 * <pre>
 * Assert.notNull(user, ExceptionEnum.ERROR_A0410, "用户信息不能为空");
 * Assert.notBlank(name, ExceptionEnum.ERROR_A0410, "用户名不能为空");
 * Assert.isTrue(count > 0, ExceptionEnum.ERROR_B0001, "数据保存失败");
 * Assert.notEmpty(list, ExceptionEnum.ERROR_A0500, "查询结果为空");
 * </pre>
 *
 * @author bird
 * @date 2024-03-13
 */
public final class Assert {

    private Assert() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    // ==================== 对象断言 ====================

    public static void notNull(Object obj, ExceptionEnum exceptionEnum) {
        if (Objects.isNull(obj)) {
            throw new BusinessException(exceptionEnum);
        }
    }

    public static void notNull(Object obj, ExceptionEnum exceptionEnum, String detail) {
        if (Objects.isNull(obj)) {
            throw new BusinessException(exceptionEnum, detail);
        }
    }

    public static void isNull(Object obj, ExceptionEnum exceptionEnum) {
        if (Objects.nonNull(obj)) {
            throw new BusinessException(exceptionEnum);
        }
    }

    // ==================== 字符串断言 ====================

    public static void notBlank(String str, ExceptionEnum exceptionEnum) {
        if (str == null || str.trim().isEmpty()) {
            throw new BusinessException(exceptionEnum);
        }
    }

    public static void notBlank(String str, ExceptionEnum exceptionEnum, String detail) {
        if (str == null || str.trim().isEmpty()) {
            throw new BusinessException(exceptionEnum, detail);
        }
    }

    // ==================== 布尔断言 ====================

    public static void isTrue(boolean condition, ExceptionEnum exceptionEnum) {
        if (!condition) {
            throw new BusinessException(exceptionEnum);
        }
    }

    public static void isTrue(boolean condition, ExceptionEnum exceptionEnum, String detail) {
        if (!condition) {
            throw new BusinessException(exceptionEnum, detail);
        }
    }

    // ==================== 集合断言 ====================

    public static void notEmpty(Collection<?> collection, ExceptionEnum exceptionEnum) {
        if (collection == null || collection.isEmpty()) {
            throw new BusinessException(exceptionEnum);
        }
    }

    public static void notEmpty(Collection<?> collection, ExceptionEnum exceptionEnum, String detail) {
        if (collection == null || collection.isEmpty()) {
            throw new BusinessException(exceptionEnum, detail);
        }
    }

    // ==================== 直接抛出（用于 catch 块）====================

    public static void fail(ExceptionEnum exceptionEnum) {
        throw new BusinessException(exceptionEnum);
    }

    public static void fail(ExceptionEnum exceptionEnum, String detail) {
        throw new BusinessException(exceptionEnum, detail);
    }

}
