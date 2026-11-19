package org.birdbro.lumen.manage.config;

import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import cn.hutool.core.util.StrUtil;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * The internal-service key guard for the machine issuance endpoint
 * (number-rule-unification-sev task 2.2, design D4): {@code /issue} is the platform's
 * FIRST service-to-service surface — it is exempt from user-token auth (the permit-all
 * list) and guarded by THIS interceptor instead. Fail-CLOSED: an UNCONFIGURED key
 * rejects every call (the endpoint is unusable, never open); a missing or wrong
 * header is an HTTP-401 envelope rejection and consumes NO serial. The key value rides
 * gitignored config on BOTH sides (env var / application-dev.yml) — never committed.
 * The heavyweight alternatives (service accounts, mTLS) defer to a future change.
 *
 * @author birdbro
 */
public class InternalKeyInterceptor implements HandlerInterceptor {

    /** The internal-service key header both sides agree on. */
    public static final String INTERNAL_KEY_HEADER = "X-Internal-Key";

    private final String configuredKey;

    public InternalKeyInterceptor(String configuredKey) {
        this.configuredKey = configuredKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) throws Exception {
        String provided = request.getHeader(INTERNAL_KEY_HEADER);
        if (StrUtil.isNotBlank(configuredKey) && configuredKey.equals(provided)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // the unified envelope shape (string code, HTTP-status family) - an auth
        // rejection, NOT an A-class request error: the caller is not authenticated
        response.getWriter().write("{\"code\":\"401\",\"msg\":\"内部服务密钥缺失或不正确，发号请求被拒绝\","
                + "\"timestamp\":\"" + java.time.LocalDateTime.now() + "\"}");
        return false;
    }
}
