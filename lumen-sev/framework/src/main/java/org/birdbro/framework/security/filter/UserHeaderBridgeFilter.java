package org.birdbro.framework.security.filter;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import com.alibaba.fastjson2.JSON;

import org.birdbro.common.core.pojo.user.UserHeader;
import org.birdbro.common.core.pojo.user.UserLogin;
import org.birdbro.common.utils.SecurityUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

/**
 * Derives the OPS-style {@code USER-HEADER} request header from the JWT-authenticated
 * principal (auth-rbac-foundation task 3.3, binding design point 3; relocated to framework as
 * lumen adaptation (6) - user ruling 2026-11-03: framework/common may evolve on the OPS
 * lineage, and this bridge is needed by EVERY service with authenticated writes,
 * since lumen has no Java gateway to inject USER-HEADER).
 *
 * <p><b>Why this filter exists:</b> the copied {@code MetaObjectConfig} fills
 * {@code create_by} from {@code SecurityUtils.getUserHeader()}, which reads the
 * {@code USER-HEADER} HTTP header - a URL-encoded JSON of the copied
 * {@code UserHeader} POJO that the OPS gateway injects on every request. lumen-auth has
 * NO gateway: the copied JWT filter populates the security context with the
 * {@link UserLogin} principal, but nothing translates that identity into the header the
 * copied audit machinery consumes. This bridge is the framework-owned translation point (lumen adaptation (6)) -
 * it makes the spec sentence "token populates the security context with the identity
 * used by MetaObjectConfig" literally true. Verbatim framework/common classes stay
 * untouched.</p>
 *
 * <p><b>Mechanics:</b> a plain {@code @Component} {@code OncePerRequestFilter} - Boot
 * registers it in the servlet chain AFTER the security chain (default lowest precedence),
 * so the {@code JwtAuthenticationTokenFilter} has already set the {@code UserLogin}
 * principal when this filter runs. When the principal is a {@code UserLogin} with a
 * {@code UserSign} AND the incoming request carries no {@code USER-HEADER} yet (an
 * upstream gateway wins when one exists), the chain continues wrapped so that
 * {@code getHeader}/{@code getHeaders}/{@code getHeaderNames} surface the URL-encoded
 * JSON of {@code UserHeader{uid, name}} - exactly what {@code SecurityUtils.getUserHeader()}
 * decodes. Any other request (anonymous, no {@code UserSign}, header already present)
 * passes through unwrapped.</p>
 *
 * @author birdbro
 */
@Component
public class UserHeaderBridgeFilter extends OncePerRequestFilter {

    /** The header the copied MetaObjectConfig/SecurityUtils machinery consumes. */
    private static final String USER_HEADER = "USER-HEADER";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        UserLogin loginUser = principal(request);
        if (loginUser == null || loginUser.getUser() == null
                || request.getHeader(USER_HEADER) != null) {
            // Anonymous request, identity without a UserSign, or an upstream-injected
            // header: nothing to bridge - continue unwrapped.
            filterChain.doFilter(request, response);
            return;
        }

        UserHeader userHeader = new UserHeader();
        userHeader.setUid(loginUser.getUserId());
        userHeader.setName(loginUser.getUser().getName());
        // SecurityUtils.getUserHeader() decodes with URLDecoder, so encode symmetrically.
        String encoded = URLEncoder.encode(JSON.toJSONString(userHeader), "UTF-8");
        filterChain.doFilter(new BridgedRequest(request, USER_HEADER, encoded), response);
    }

    private UserLogin principal(HttpServletRequest request) {
        try {
            if (SecurityUtils.getAuthentication() == null) {
                return null;
            }
            Object principal = SecurityUtils.getAuthentication().getPrincipal();
            return principal instanceof UserLogin ? (UserLogin) principal : null;
        } catch (Exception e) {
            // Never let identity bridging break the request - audit fill degrades to null.
            return null;
        }
    }

    /**
     * Surfaces one extra header on top of the wrapped request. Header lookup is
     * case-insensitive (HTTP header names are), matching what the copied machinery and
     * any downstream code expect.
     */
    private static class BridgedRequest extends HttpServletRequestWrapper {

        private final String bridgedName;
        private final String bridgedValue;

        BridgedRequest(HttpServletRequest request, String bridgedName, String bridgedValue) {
            super(request);
            this.bridgedName = bridgedName;
            this.bridgedValue = bridgedValue;
        }

        private boolean isBridged(String name) {
            return bridgedName.equalsIgnoreCase(name);
        }

        @Override
        public String getHeader(String name) {
            if (isBridged(name)) {
                return bridgedValue;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (isBridged(name)) {
                return Collections.enumeration(Collections.singletonList(bridgedValue));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>(Collections.list(super.getHeaderNames()));
            if (!names.stream().anyMatch(this::isBridged)) {
                names.add(bridgedName);
            }
            return Collections.enumeration(names);
        }
    }
}
