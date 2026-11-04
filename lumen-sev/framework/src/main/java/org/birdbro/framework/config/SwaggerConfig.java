package org.birdbro.framework.config;

import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.oas.annotations.EnableOpenApi;
import springfox.documentation.service.*;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * Swagger 配置类
 *
 * @author birdbro
 * @date 14:27 2024-05-06
 **/
@EnableOpenApi
@Configuration
@EnableWebMvc
public class SwaggerConfig implements WebMvcConfigurer {

    /**
     * OpenAPI metadata via properties (lumen adaptation (9), user decision 2026-11-03:
     * framework/common may evolve on the OPS lineage) - the OPS literals below are the
     * @Value DEFAULTS, so zero-config behavior is unchanged; each service overrides them
     * in its own application.yml (lumen-system: swagger.title/description/version).
     */
    @Value("${swagger.title:JF-RCP}")
    private String swaggerTitle;

    @Value("${swagger.description:Lab-RCP}")
    private String swaggerDescription;

    @Value("${swagger.version:1.0.0}")
    private String swaggerVersion;

    @Value("${swagger.contact-name:birdbro}")
    private String swaggerContactName;


    @Bean
    @Order(value = 1)
    public Docket docket() {
        return new Docket(DocumentationType.OAS_30)
                .apiInfo(apiInfo())
                // ture 启用Swagger3.0， false 禁用（生产环境）
                .enable(true)
                .select()
                // 扫描的路径使用@Api的controller
                .apis(RequestHandlerSelectors.withClassAnnotation(Api.class))
                // 指定路径处理PathSelectors.any()代表所有的路径
                .paths(PathSelectors.any())
                .build()
                .securityContexts(securityContexts())
                .securitySchemes(securitySchemes())
                .directModelSubstitute(Date.class, java.util.Date.class);

    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title(swaggerTitle)
                .description(swaggerDescription)
                .termsOfServiceUrl("https://github.com/bird-bro/lumen-collab/")
                .contact(new Contact(swaggerContactName, "", ""))
                .version(swaggerVersion)
                .build();
    }

    /*
     * 解决swagger3.0 head传参失效的问题
     */

    private List<SecurityContext> securityContexts() {
        List<SecurityContext> securityContexts = new ArrayList<>();
        securityContexts.add(SecurityContext.builder()
                .securityReferences(defaultAuth())
                .forPaths(PathSelectors.regex("^(?!auth).*$")).build());
        return securityContexts;
    }


    private List<SecurityScheme> securitySchemes() {
        List<SecurityScheme> securitySchemes = new ArrayList<>();
        securitySchemes.add(new ApiKey("Authorization", "Authorization", "header"));
        securitySchemes.add(new ApiKey("USER-HEADER", "USER-HEADER", "header"));
        securitySchemes.add(new ApiKey("Accept-Language", "Accept-Language", "header"));
        return securitySchemes;
    }


    private List<SecurityReference> defaultAuth() {
        AuthorizationScope authorizationScope1 = new AuthorizationScope("global", "token");
        AuthorizationScope[] authorizationScopes1 = new AuthorizationScope[1];
        authorizationScopes1[0] = authorizationScope1;

        AuthorizationScope authorizationScope2 = new AuthorizationScope("global", "language");
        AuthorizationScope[] authorizationScopes2 = new AuthorizationScope[1];
        authorizationScopes2[0] = authorizationScope2;

        AuthorizationScope authorizationScope3 = new AuthorizationScope("global", "userHeader");
        AuthorizationScope[] authorizationScopes3 = new AuthorizationScope[1];
        authorizationScopes3[0] = authorizationScope3;

        List<SecurityReference> securityReferences = new ArrayList<>();
        securityReferences.add(new SecurityReference("Authorization", authorizationScopes1));
        securityReferences.add(new SecurityReference("Accept-Language", authorizationScopes2));
        securityReferences.add(new SecurityReference("userHeader", authorizationScopes3));
        return securityReferences;
    }
}
