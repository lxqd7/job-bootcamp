package com.jobbootcamp.learning.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final StudentAuthInterceptor studentAuthInterceptor;
    private final InternalAuthInterceptor internalAuthInterceptor;

    public WebConfig(StudentAuthInterceptor studentAuthInterceptor,
                     InternalAuthInterceptor internalAuthInterceptor) {
        this.studentAuthInterceptor = studentAuthInterceptor;
        this.internalAuthInterceptor = internalAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(studentAuthInterceptor)
                .addPathPatterns("/api/v1/me/**");
        registry.addInterceptor(internalAuthInterceptor)
                .addPathPatterns("/int/v1/**");
    }
}