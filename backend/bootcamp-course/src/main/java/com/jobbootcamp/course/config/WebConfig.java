package com.jobbootcamp.course.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminPermissionInterceptor adminPermissionInterceptor;
    private final TeacherScopeInterceptor teacherScopeInterceptor;

    public WebConfig(AdminPermissionInterceptor adminPermissionInterceptor,
                     TeacherScopeInterceptor teacherScopeInterceptor) {
        this.adminPermissionInterceptor = adminPermissionInterceptor;
        this.teacherScopeInterceptor = teacherScopeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminPermissionInterceptor)
                .addPathPatterns("/api/v1/admin/**");
        registry.addInterceptor(teacherScopeInterceptor)
                .addPathPatterns("/api/v1/teacher/**");
    }
}