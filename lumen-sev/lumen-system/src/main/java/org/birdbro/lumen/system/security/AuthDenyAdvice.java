package org.birdbro.lumen.system.security;

import org.birdbro.common.core.R;
import org.birdbro.common.enums.ExceptionEnum;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Renders the permission-denied envelope for METHOD-SECURITY denials
 * (server-side-perm-enforcement design D3): a class-level {@code @PreAuthorize} on a
 * controller throws {@link AccessDeniedException} from the handler-invocation AOP
 * layer, which propagates through {@code HandlerExceptionResolver} - so a lumen-owned
 * advice catches it (no copied {@code SecurityConfig} edit, no filter-level handler
 * registration). Tokenless requests never reach here: the copied entry point answers
 * them with {@code "203"} at the filter stage.
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} - the copied {@code GlobalExceptionHandler}
 * (unordered) carries an {@code Exception.class} catch-all that would otherwise win
 * the advice-resolution race and mask the denial as a 500.</p>
 *
 * @author birdbro
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class AuthDenyAdvice {

    /** 统一信封的权限拒绝：A0301 访问未授权（HTTP 200 传输，对齐 "203" 先例）。 */
    @ExceptionHandler(AccessDeniedException.class)
    public R<Void> handleAccessDenied(AccessDeniedException e) {
        return R.ofFail(ExceptionEnum.ERROR_A0301.getCode(),
                "访问未授权：缺少该资源所需的页面权限",
                e.getMessage());
    }
}
