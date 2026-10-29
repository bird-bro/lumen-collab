package org.birdbro.framework.config;

import org.birdbro.framework.global.GlobalExceptionHandler;
import org.birdbro.framework.global.GlobalProperties;
import org.birdbro.framework.global.GlobalResponseAdvice;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * @author bird
 * @date 2021-7-21 8:54
 **/
@Configuration
@EnableConfigurationProperties(GlobalProperties.class)
@PropertySource(value = "classpath:config/dispose.properties", encoding = "UTF-8")
public class GlobalConfig {
    @Bean
    public GlobalExceptionHandler globalDefaultExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    public GlobalResponseAdvice commonResponseDataAdvice(GlobalProperties globalDefaultProperties) {
        return new GlobalResponseAdvice(globalDefaultProperties);
    }
}
