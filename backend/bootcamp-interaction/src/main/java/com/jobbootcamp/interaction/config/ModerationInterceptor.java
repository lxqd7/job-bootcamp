package com.jobbootcamp.interaction.config;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.SessionCheckResponse;
import com.jobbootcamp.interaction.application.SessionAuthorizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Component
public class ModerationInterceptor implements HandlerInterceptor {

    public static final String ADMIN_ID_ATTRIBUTE = "interaction.adminId";

    private final SessionAuthorizer sessionAuthorizer;

    public ModerationInterceptor(SessionAuthorizer sessionAuthorizer) {
        this.sessionAuthorizer = sessionAuthorizer;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        SessionCheckResponse session = sessionAuthorizer.check(request.getHeader(HttpHeaders.AUTHORIZATION));
        List<String> permissions = session.permissions() == null ? List.of() : session.permissions();
        String uri = request.getRequestURI();
        String required = uri.startsWith("/api/v1/admin/questions/")
                ? "anonymous:trace"
                : uri.startsWith("/api/v1/admin/excellent-rewards") || uri.endsWith("/revoke-reward")
                ? "points:revoke" : "community:moderate";
        if (!"ADMIN".equals(session.role()) || !permissions.contains(required)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "没有该管理操作的权限");
        }
        request.setAttribute(ADMIN_ID_ATTRIBUTE, Long.valueOf(session.userId()));
        return true;
    }
}