package com.jobbootcamp.course.config;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.SessionCheckResponse;
import com.jobbootcamp.course.application.SessionAuthorizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TeacherScopeInterceptor implements HandlerInterceptor {

    private final SessionAuthorizer sessionAuthorizer;

    public TeacherScopeInterceptor(SessionAuthorizer sessionAuthorizer) {
        this.sessionAuthorizer = sessionAuthorizer;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        SessionCheckResponse session = sessionAuthorizer.check(request.getHeader("Authorization"));
        if (!"TEACHER".equals(session.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有教师可以访问该工作区");
        }
        request.setAttribute("currentTeacherId", Long.valueOf(session.userId()));
        return true;
    }
}