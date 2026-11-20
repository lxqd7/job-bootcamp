package com.jobbootcamp.course.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminPermissionInterceptor adminPermissionInterceptor;
    private final TeacherScopeInterceptor teacherScopeInterceptor;
    private final StudentAuthInterceptor studentAuthInterceptor;
    private final InternalAuthInterceptor internalAuthInterceptor;

    public WebConfig(AdminPermissionInterceptor adminPermissionInterceptor,
                     TeacherScopeInterceptor teacherScopeInterceptor,
                     StudentAuthInterceptor studentAuthInterceptor,
                     InternalAuthInterceptor internalAuthInterceptor) {
        this.adminPermissionInterceptor = adminPermissionInterceptor;
        this.teacherScopeInterceptor = teacherScopeInterceptor;
        this.studentAuthInterceptor = studentAuthInterceptor;
        this.internalAuthInterceptor = internalAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminPermissionInterceptor)
                .addPathPatterns("/api/v1/admin/**");
        registry.addInterceptor(teacherScopeInterceptor)
                .addPathPatterns("/api/v1/teacher/**");
        registry.addInterceptor(studentAuthInterceptor)
                .addPathPatterns("/api/v1/courses/*/claim", "/api/v1/claims/**");
        registry.addInterceptor(internalAuthInterceptor)
                .addPathPatterns("/int/v1/**");
    }
}