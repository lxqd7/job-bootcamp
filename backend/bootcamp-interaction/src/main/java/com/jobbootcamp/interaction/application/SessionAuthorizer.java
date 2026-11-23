package com.jobbootcamp.interaction.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.InternalTokenCodec;
import com.jobbootcamp.contracts.internal.SessionCheckResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

@Component
public class SessionAuthorizer {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final RestClient restClient;
    private final String sessionCheckUrl;
    private final String internalSecret;

    public SessionAuthorizer(@Value("${app.user.session-check-url}") String sessionCheckUrl,
                             @Value("${app.internal.secret}") String internalSecret) {
        this.sessionCheckUrl = sessionCheckUrl;
        this.internalSecret = internalSecret;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public SessionCheckResponse check(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        String serviceToken = InternalTokenCodec.issue(
                internalSecret, "bootcamp-interaction", "bootcamp-user", "sessions:check", 60);
        try {
            SessionCheckResponse response = restClient.post()
                    .uri(sessionCheckUrl)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .header(SERVICE_TOKEN_HEADER, serviceToken)
                    .retrieve()
                    .body(SessionCheckResponse.class);
            if (response == null || !response.active()) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
            }
            return response;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
            }
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "认证服务不可用，请稍后重试");
        } catch (ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "认证服务不可用，请稍后重试");
        }
    }
}