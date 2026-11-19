package com.jobbootcamp.user.config;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.user.application.SessionValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SessionValidator sessionValidator;

    public AuthInterceptor(SessionValidator sessionValidator) {
        this.sessionValidator = sessionValidator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw unauthorized();
        }
        SessionValidator.ValidatedSession validated = sessionValidator
                .validate(authorization.substring(BEARER_PREFIX.length()))
                .orElseThrow(this::unauthorized);
        request.setAttribute("currentUserId", validated.user().getId());
        return true;
    }

    private BusinessException unauthorized() {
        return new BusinessException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
    }
}