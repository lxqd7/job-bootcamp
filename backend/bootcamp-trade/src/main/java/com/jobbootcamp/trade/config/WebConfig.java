package com.jobbootcamp.trade.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final StudentAuthInterceptor studentAuthInterceptor;

    public WebConfig(StudentAuthInterceptor studentAuthInterceptor) {
        this.studentAuthInterceptor = studentAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(studentAuthInterceptor)
                .addPathPatterns("/api/v1/trade/**", "/api/v1/orders/**",
                        "/api/v1/refunds/**", "/api/v1/dev/payments/**", "/dev/payments/**");
    }
}