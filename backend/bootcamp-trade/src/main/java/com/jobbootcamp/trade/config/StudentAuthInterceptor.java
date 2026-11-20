package com.jobbootcamp.trade.config;

import com.jobbootcamp.contracts.internal.SessionCheckResponse;
import com.jobbootcamp.trade.application.SessionAuthorizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class StudentAuthInterceptor implements HandlerInterceptor {

    public static final String USER_ID_ATTRIBUTE = "trade.userId";
    public static final String ROLE_ATTRIBUTE = "trade.role";
    public static final String SESSION_ID_ATTRIBUTE = "trade.sessionId";

    private final SessionAuthorizer sessionAuthorizer;

    public StudentAuthInterceptor(SessionAuthorizer sessionAuthorizer) {
        this.sessionAuthorizer = sessionAuthorizer;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        SessionCheckResponse session = sessionAuthorizer.check(request.getHeader(HttpHeaders.AUTHORIZATION));
        request.setAttribute(USER_ID_ATTRIBUTE, Long.valueOf(session.userId()));
        request.setAttribute(ROLE_ATTRIBUTE, session.role());
        request.setAttribute(SESSION_ID_ATTRIBUTE, session.sessionId());
        return true;
    }
}