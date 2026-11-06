package org.birdbro.framework.config;

import org.birdbro.common.Constants.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * 通用配置
 *
 * @author birdbro
 * @date 11:53 2024-05-22
 **/
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 本地文件上传根目录 (org-logo-upload-api, framework adaptation #7, 2026-11-06):
     * the copied OPS line below registered /profile/** with NO locations - an obviously
     * truncated chain (its own comment names the local upload path). When a service
     * configures upload.home (v1: lumen-system only), /profile/** serves those files
     * anonymously (SecurityConfig already permits GET /profile/**). The DISK TREE
     * MIRRORS THE URL TREE: FileUtils composes stored paths as
     * {@code /profile/...} under {@code upload.home} (the upload.* avatar prefix carries
     * the RESOURCE_PREFIX segment), so the location is {@code upload.home + /profile/} -
     * URL {@code /profile/avatar/2026/11/06/x.png} resolves at
     * {@code home/profile/avatar/2026/11/06/x.png}. The BLANK default keeps every other
     * service's registration exactly the OPS-parity no-locations form.
     */
    @Value("${upload.home:}")
    private String uploadHome = "";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        /** 本地文件上传路径 */
        ResourceHandlerRegistration profileRegistration =
                registry.addResourceHandler(Constants.RESOURCE_PREFIX + "/**");
        if (uploadHome != null && !uploadHome.isBlank()) {
            // file: locations need forward slashes and a trailing slash for prefix matching
            String root = uploadHome.replace('\\', '/') + "/" + Constants.RESOURCE_PREFIX;
            if (!root.endsWith("/")) {
                root = root + "/";
            }
            profileRegistration.addResourceLocations("file:" + root);
        }

        /** swagger配置 */
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/springfox-swagger-ui/")
                .setCacheControl(CacheControl.maxAge(5, TimeUnit.HOURS).cachePublic());
    }

    /**
     * 跨域配置
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        // 设置访问源地址
        config.addAllowedOriginPattern("*");
        // 设置访问源请求头
        config.addAllowedHeader("*");
        // 设置访问源请求方法
        config.addAllowedMethod("*");
        // 有效期 1800秒
        config.setMaxAge(1800L);
        // 添加映射路径，拦截一切请求
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        // 返回新的CorsFilter
        return new CorsFilter(source);
    }
}