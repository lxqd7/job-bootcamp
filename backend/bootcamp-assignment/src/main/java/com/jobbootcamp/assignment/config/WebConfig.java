package com.jobbootcamp.assignment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AssignmentAuthInterceptor authInterceptor;

    public WebConfig(AssignmentAuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/admin/assignments/**", "/api/v1/assignments/**",
                        "/api/v1/attempts/**", "/api/v1/submissions/**",
                        "/api/v1/courses/*/assignments", "/api/v1/teacher/**",
                        "/api/v1/benefit-assignments/**", "/api/v1/routes/*/benefit-assignments",
                        "/api/v1/dev/assignments/**");
    }
}