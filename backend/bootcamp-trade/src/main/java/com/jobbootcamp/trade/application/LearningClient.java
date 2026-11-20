package com.jobbootcamp.trade.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.InternalTokenCodec;
import com.jobbootcamp.contracts.internal.AccessCheckRequest;
import com.jobbootcamp.contracts.internal.AccessCheckResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class LearningClient {

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final RestClient restClient = RestClient.create();
    private final String accessUrl;
    private final String internalSecret;

    public LearningClient(@Value("${app.learning.access-url}") String accessUrl,
                          @Value("${app.internal.secret}") String internalSecret) {
        this.accessUrl = accessUrl;
        this.internalSecret = internalSecret;
    }

    public AccessCheckResponse checkAccess(Long userId, Long courseId) {
        String serviceToken = InternalTokenCodec.issue(
                internalSecret, "bootcamp-trade", "bootcamp-learning", "access:check", 60);
        try {
            AccessCheckResponse response = restClient.post()
                    .uri(accessUrl)
                    .header(SERVICE_TOKEN_HEADER, serviceToken)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(new AccessCheckRequest(userId, courseId))
                    .retrieve()
                    .body(AccessCheckResponse.class);
            if (response == null) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "学习服务未返回权益结果");
            }
            return response;
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "学习服务暂时不可用");
        }
    }
}