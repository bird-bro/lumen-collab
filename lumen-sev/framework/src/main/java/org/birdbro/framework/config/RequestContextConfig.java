package org.birdbro.framework.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.autoconfigure.web.servlet.ConditionalOnMissingFilterBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.filter.OrderedRequestContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextListener;
import org.springframework.web.filter.RequestContextFilter;

import javax.servlet.DispatcherType;

/**
 * Binds the {@code RequestContextHolder} for the WHOLE request lifecycle, not only the
 * MVC dispatch (system-data batch 2, recorded lumen adaptation).
 *
 * <p><b>Why this exists:</b> the copied log machinery reads the request context OUTSIDE
 * the MVC dispatch - {@code AsyncFactory.recordForSign} runs synchronously on the request
 * thread inside {@code LogoutSuccessHandlerImpl}, which the security chain invokes from
 * {@code LogoutFilter}, BEFORE any {@code DispatcherServlet} could bind the context. With
 * the context unbound, {@code ServletUtils.getRequest()} NPEs and every logout fails with
 * a 500 (observed in the batch-2 red run) - the sign row can never land. The OPS runtime
 * this copy came from evidently kept the context bound for the whole request; on lumen no
 * auto-configured {@code RequestContextFilter} is registered (verified by the batch-2
 * filter probe: jwtAuthenticationTokenFilter / userHeaderBridgeFilter / corsFilter /
 * springSecurityFilterChain / characterEncodingFilter only), so the binding is missing
 * exactly where the copy needs it.</p>
 *
 * <p><b>Mechanics:</b> a framework-owned {@link OrderedRequestContextFilter} registration
 * ordered one step BEFORE the security filter chain ({@code SecurityProperties.
 * DEFAULT_FILTER_ORDER} = -100), REQUEST dispatch only - Boot's default dispatch set. It
 * only binds/resets thread state (it never wraps the request), so the only behavioral
 * change is the presence of the context the copied machinery assumes. The conditions
 * mirror Boot's own (skipped) {@code RequestContextFilter} auto-config, so exactly one
 * filter exists whichever way an environment resolves them.</p>
 *
 * @author birdbro
 */
@Configuration(proxyBeanMethods = false)
public class RequestContextConfig {

    @Bean
    @ConditionalOnMissingBean({RequestContextListener.class, RequestContextFilter.class})
    @ConditionalOnMissingFilterBean(RequestContextFilter.class)
    public FilterRegistrationBean<RequestContextFilter> requestContextFilterRegistration() {
        FilterRegistrationBean<RequestContextFilter> registration =
                new FilterRegistrationBean<>(new OrderedRequestContextFilter());
        registration.setName("requestContextFilter");
        // One step before the security chain (-100), mirroring Boot's
        // OrderedRequestContextFilter default order.
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 5);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        return registration;
    }
}
