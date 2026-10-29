package org.birdbro.common.exception;

import lombok.Getter;
import org.birdbro.common.enums.ExceptionEnum;

/**
 * 业务异常 - 重构优化版
 *
 * 设计原则：
 * 1. 所有异常必须有错误码（code），不允许纯文本异常
 * 2. HTTP 状态码只用于真正的 HTTP 协议层面错误
 * 3. 业务异常统一返回 200，通过 code 区分错误类型
 * 4. 保留原始异常信息，便于问题定位
 *
 * @author bird
 * @date 2024-03-13
 **/
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 业务错误码（A/B/C 开头）
     */
    private final String code;

    /**
     * 用户友好的错误消息
     */
    private final String msg;

    /**
     * 是否记录详细错误日志（默认true）
     */
    private final boolean needLog;

    // ==================== 推荐构造器 ====================

    /**
     * 使用预定义异常枚举（推荐）
     * 示例：throw new BusinessException(ExceptionEnum.ERROR_A0410);
     */
    public BusinessException(ExceptionEnum exceptionEnum) {
        super(String.format("[%s] %s", exceptionEnum.getCode(), exceptionEnum.getMsg()));
        this.code = exceptionEnum.getCode();
        this.msg = exceptionEnum.getMsg();
        this.needLog = true;
    }

    /**
     * 使用预定义枚举 + 自定义详情（推荐）
     * 示例：throw new BusinessException(ExceptionEnum.ERROR_A0410, "用户名不能为空");
     */
    public BusinessException(ExceptionEnum exceptionEnum, String detail) {
        super(String.format("[%s] %s - %s", exceptionEnum.getCode(), exceptionEnum.getMsg(), detail));
        this.code = exceptionEnum.getCode();
        this.msg = exceptionEnum.getMsg() + "：" + detail;
        this.needLog = true;
    }

    /**
     * 使用预定义枚举 + 自定义详情 + 原始异常（推荐）
     * 示例：throw new BusinessException(ExceptionEnum.ERROR_C0300, "连接超时", e);
     */
    public BusinessException(ExceptionEnum exceptionEnum, String detail, Throwable cause) {
        super(String.format("[%s] %s - %s", exceptionEnum.getCode(), exceptionEnum.getMsg(), detail), cause);
        this.code = exceptionEnum.getCode();
        this.msg = exceptionEnum.getMsg() + "：" + detail;
        this.needLog = true;
    }

    // ==================== 扩展构造器（谨慎使用）====================

    /**
     * 自定义错误码（用于临时扩展，后续应补充到 ExceptionEnum）
     * 示例：throw new BusinessException("A9999", "业务特定错误");
     */
    public BusinessException(String code, String msg) {
        super(String.format("[%s] %s", code, msg));
        this.code = code;
        this.msg = msg;
        this.needLog = true;
    }

    /**
     * 自定义错误码 + 原始异常
     */
    public BusinessException(String code, String msg, Throwable cause) {
        super(String.format("[%s] %s", code, msg), cause);
        this.code = code;
        this.msg = msg;
        this.needLog = true;
    }

}
