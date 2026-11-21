package com.jobbootcamp.marketing.config;

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

    private final String secret;

    public InternalAuthInterceptor(@Value("${app.internal.secret}") String secret) {
        this.secret = secret;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();
        String operation;
        if (uri.contains("/pricing/status")) {
            operation = "pricing:status";
        } else if (uri.contains("/pricing/calculate")) {
            operation = "pricing:calculate";
        } else if (uri.contains("/coupon-locks/prepare")) {
            operation = "coupon:lock";
        } else if (uri.contains("/coupon-locks/use")) {
            operation = "coupon:use";
        } else if (uri.contains("/coupon-locks/release")) {
            operation = "coupon:release";
        } else if (uri.contains("/coupon-locks/recover")) {
            operation = "coupon:recover";
        } else {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未知的内部服务操作");
        }
        try {
            InternalTokenCodec.verify(secret, request.getHeader(SERVICE_TOKEN_HEADER),
                    "bootcamp-marketing", operation);
            return true;
        } catch (InternalTokenException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "内部服务凭据无效");
        }
    }
}