package com.jobbootcamp.course.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.FreeClaimCommand;
import com.jobbootcamp.contracts.internal.FreeClaimResult;
import com.jobbootcamp.contracts.internal.InternalTokenCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class LearningClaimClient {

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final RestClient restClient = RestClient.create();
    private final String freeClaimUrl;
    private final String internalSecret;

    public LearningClaimClient(@Value("${app.learning.free-claim-url}") String freeClaimUrl,
                               @Value("${app.internal.secret}") String internalSecret) {
        this.freeClaimUrl = freeClaimUrl;
        this.internalSecret = internalSecret;
    }

    public FreeClaimResult grant(FreeClaimCommand command) {
        String serviceToken = InternalTokenCodec.issue(
                internalSecret, "bootcamp-course", "bootcamp-learning", "free-claims:create", 60);
        try {
            FreeClaimResult result = restClient.post()
                    .uri(freeClaimUrl)
                    .header(SERVICE_TOKEN_HEADER, serviceToken)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(command)
                    .retrieve()
                    .body(FreeClaimResult.class);
            if (result == null || result.sourceId() == null) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "学习服务未返回权益来源");
            }
            return result;
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "学习服务暂时不可用");
        }
    }
}