package com.jobbootcamp.interaction.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final InteractionAuthInterceptor authInterceptor;
    private final ModerationInterceptor moderationInterceptor;

    public WebConfig(InteractionAuthInterceptor authInterceptor,
                     ModerationInterceptor moderationInterceptor) {
        this.authInterceptor = authInterceptor;
        this.moderationInterceptor = moderationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/courses/*/questions", "/api/v1/questions/**",
                        "/api/v1/answers/**", "/api/v1/comments/**",
                        "/api/v1/courses/*/notes", "/api/v1/me/notes", "/api/v1/notes/**",
                        "/api/v1/likes/**", "/api/v1/teacher/notes/**");
        registry.addInterceptor(moderationInterceptor)
                .addPathPatterns("/api/v1/admin/**");
    }
}