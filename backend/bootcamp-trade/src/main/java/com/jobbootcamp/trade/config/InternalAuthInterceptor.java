package com.jobbootcamp.trade.config;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.InternalTokenCodec;
import com.jobbootcamp.contracts.internal.InternalTokenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class InternalAuthInterceptor implements HandlerInterceptor {

    public static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    private static final String OPERATION = "orders:status";

    private final String secret;

    public InternalAuthInterceptor(@Value("${app.internal.secret}") String secret) {
        this.secret = secret;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            InternalTokenCodec.verify(secret, request.getHeader(SERVICE_TOKEN_HEADER),
                    "bootcamp-trade", OPERATION);
            return true;
        } catch (InternalTokenException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "内部服务凭据无效");
        }
    }
}