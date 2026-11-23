package com.jobbootcamp.marketing.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final MarketingAuthInterceptor authInterceptor;
    private final InternalAuthInterceptor internalAuthInterceptor;

    public WebConfig(MarketingAuthInterceptor authInterceptor,
                     InternalAuthInterceptor internalAuthInterceptor) {
        this.authInterceptor = authInterceptor;
        this.internalAuthInterceptor = internalAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/me/**", "/api/v1/boards/**", "/api/v1/sign-ins",
                        "/api/v1/activities/**", "/api/v1/activity-items/**",
                        "/api/v1/admin/coupon-rules/**", "/api/v1/admin/activities/**",
                        "/api/v1/admin/activity-items/**", "/api/v1/admin/exchange-projects/**");
        registry.addInterceptor(internalAuthInterceptor).addPathPatterns("/int/v1/**");
    }
}