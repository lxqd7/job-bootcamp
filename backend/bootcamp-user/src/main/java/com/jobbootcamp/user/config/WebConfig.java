package com.jobbootcamp.user.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final InternalAuthInterceptor internalAuthInterceptor;

    public WebConfig(AuthInterceptor authInterceptor, InternalAuthInterceptor internalAuthInterceptor) {
        this.authInterceptor = authInterceptor;
        this.internalAuthInterceptor = internalAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/me", "/api/v1/me/**", "/api/v1/admin/**");
        registry.addInterceptor(internalAuthInterceptor)
                .addPathPatterns("/int/v1/**");
    }
}