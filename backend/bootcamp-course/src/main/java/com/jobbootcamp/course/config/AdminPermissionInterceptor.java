package com.jobbootcamp.course.config;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.SessionCheckResponse;
import com.jobbootcamp.course.application.SessionAuthorizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Component
public class AdminPermissionInterceptor implements HandlerInterceptor {

    private final SessionAuthorizer sessionAuthorizer;

    public AdminPermissionInterceptor(SessionAuthorizer sessionAuthorizer) {
        this.sessionAuthorizer = sessionAuthorizer;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        SessionCheckResponse session = sessionAuthorizer.check(request.getHeader("Authorization"));
        List<String> permissions = session.permissions() == null ? List.of() : session.permissions();
        String required = requiredPermission(request.getRequestURI());
        if (!"ADMIN".equals(session.role()) || !permissions.contains(required)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "没有该管理操作的权限");
        }
        request.setAttribute("currentUserId", Long.valueOf(session.userId()));
        request.setAttribute("currentSessionId", Long.valueOf(session.sessionId()));
        return true;
    }

    private String requiredPermission(String path) {
        if (path.startsWith("/api/v1/admin/routes")) {
            return "route:manage";
        }
        if (path.startsWith("/api/v1/admin/teachers")) {
            return "teacher:assign";
        }
        return "course:manage";
    }
}