package org.birdbro.framework.global;

import lombok.extern.slf4j.Slf4j;
import org.birdbro.common.core.R;
import org.birdbro.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局异常处理器 - 优化版
 *
 * 改进点：
 * 1. 统一响应格式，业务异常返回 200（code 区分），系统异常返回 500
 * 2. 简化日志输出，避免重复代码
 * 3. 优化参数校验错误提示
 * 4. 业务异常统一由 BusinessException 承载（旧版 BusinessExceptionV2 已移除）
 *
 * @author bird
 * @date 2024-03-13
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 业务异常（返回 200，code 标识错误）====================

    /**
     * 新版业务异常处理
     */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        if (e.isNeedLog()) {
            log.error("[业务异常] URI: {}, Code: {}, Msg: {}",
                    request.getRequestURI(), e.getCode(), e.getMsg());
        }
        return R.ofFail(e.getCode(), e.getMsg(), e.getMessage());
    }

    // ==================== 请求参数异常（返回 400）====================

    /**
     * 方法参数校验失败（@Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        // msg: 用户可见的具体字段提示（前端 toast 此字段）；detail: 带字段名明细供日志/排查
        String msg = fieldErrors.stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        String detail = fieldErrors.stream()
                .map(error -> String.format("[%s]%s", error.getField(), error.getDefaultMessage()))
                .collect(Collectors.joining(", "));

        log.warn("[参数校验失败] URI: {}, 错误: {}", request.getRequestURI(), detail);

        return R.ofFail("A0400", msg, detail);
    }

    /**
     * 约束校验失败（@RequestParam 等）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        // msg: 用户可见的具体提示；detail: 带属性路径明细供日志/排查
        String msg = violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        String detail = violations.stream()
                .map(v -> String.format("[%s]%s", v.getPropertyPath(), v.getMessage()))
                .collect(Collectors.joining(", "));

        log.warn("[参数约束失败] URI: {}, 错误: {}", request.getRequestURI(), detail);

        return R.ofFail("A0400", msg, detail);
    }

    /**
     * 参数绑定失败
     */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e, HttpServletRequest request) {
        List<FieldError> fieldErrors = e.getFieldErrors();
        // msg: 用户可见的具体提示；detail: 带字段名明细供日志/排查
        String msg = fieldErrors.stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        String detail = fieldErrors.stream()
                .map(error -> String.format("[%s]%s", error.getField(), error.getDefaultMessage()))
                .collect(Collectors.joining(", "));

        log.warn("[参数绑定失败] URI: {}, 错误: {}", request.getRequestURI(), detail);

        return R.ofFail("A0400", msg, detail);
    }

    /**
     * 参数类型不匹配
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String message = String.format("参数[%s]类型错误，期望: %s，实际: %s",
                e.getName(), e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown", e.getValue());

        log.warn("[参数类型错误] URI: {}, 错误: {}", request.getRequestURI(), message);

        return R.ofFail("A0421", "参数格式不匹配", message);
    }

    /**
     * 缺少必要参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e, HttpServletRequest request) {
        String message = String.format("缺少必要参数: %s (%s)", e.getParameterName(), e.getParameterType());

        log.warn("[缺少参数] URI: {}, 错误: {}", request.getRequestURI(), message);

        return R.ofFail("A0410", "请求必填参数为空", message);
    }

    /**
     * 请求体不可读（JSON 解析失败等）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleMessageNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        String message = "请求体格式错误，请检查 JSON 格式";
        if (e.getMessage() != null && e.getMessage().contains("JSON")) {
            message = "JSON 解析失败，请检查请求体格式";
        }

        log.warn("[请求体错误] URI: {}, 错误: {}", request.getRequestURI(), e.getMessage());

        return R.ofFail("A0427", "请求 JSON 解析失败", message);
    }

    // ==================== HTTP 协议异常（返回对应 HTTP 状态码）====================

    /**
     * 请求方法不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("[请求方法不支持] URI: {}, Method: {}, 支持: {}",
                request.getRequestURI(), e.getMethod(), e.getSupportedHttpMethods());

        return R.ofFail(String.valueOf(HttpStatus.METHOD_NOT_ALLOWED.value()),
                "请求方法不支持: " + e.getMethod(), null);
    }

    /**
     * 404 处理器
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public R<Void> handleNoHandlerFound(NoHandlerFoundException e, HttpServletRequest request) {
        log.warn("[接口不存在] URI: {}", request.getRequestURI());

        return R.ofFail(String.valueOf(HttpStatus.NOT_FOUND.value()),
                "接口不存在: " + request.getRequestURI(), null);
    }

    // ==================== 系统异常（返回 500）====================

    /**
     * 兜底异常处理器
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("[系统异常] URI: {}, 错误: {}", request.getRequestURI(), e.getMessage(), e);

        return R.ofFail(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()),
                "系统异常，请稍后重试", e.getMessage());
    }

}
