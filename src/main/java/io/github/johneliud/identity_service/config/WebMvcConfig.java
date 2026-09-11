package io.github.johneliud.identity_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.github.johneliud.identity_service.interceptor.ApiVersionResponseInterceptor;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    static final String DEFAULT_VERSION = "1";

    private final ApiVersionStrategy apiVersionStrategy;

    public WebMvcConfig(@Lazy ApiVersionStrategy apiVersionStrategy) {
        this.apiVersionStrategy = apiVersionStrategy;
    }

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer
                .useRequestHeader("X-API-Version")
                .setDefaultVersion(DEFAULT_VERSION)
                .setVersionRequired(false);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ApiVersionResponseInterceptor(apiVersionStrategy, DEFAULT_VERSION));
    }
}
