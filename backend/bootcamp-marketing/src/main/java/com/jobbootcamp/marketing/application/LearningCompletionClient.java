package com.jobbootcamp.marketing.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.CompletionEventItem;
import com.jobbootcamp.contracts.internal.CompletionEventsRequest;
import com.jobbootcamp.contracts.internal.CompletionEventsResponse;
import com.jobbootcamp.contracts.internal.InternalTokenCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
public class LearningCompletionClient {

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final RestClient restClient = RestClient.create();
    private final String url;
    private final String internalSecret;

    public LearningCompletionClient(@Value("${app.learning.completion-events-url}") String url,
                                    @Value("${app.internal.secret}") String internalSecret) {
        this.url = url;
        this.internalSecret = internalSecret;
    }

    public List<CompletionEventItem> fetch(long userId, long afterSequence, int limit) {
        String serviceToken = InternalTokenCodec.issue(
                internalSecret, "bootcamp-marketing", "bootcamp-learning", "completion-events:read", 60);
        try {
            CompletionEventsResponse response = restClient.post()
                    .uri(url)
                    .header(SERVICE_TOKEN_HEADER, serviceToken)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(new CompletionEventsRequest(userId, afterSequence, limit))
                    .retrieve()
                    .body(CompletionEventsResponse.class);
            return response == null || response.items() == null ? List.of() : response.items();
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "学习完成事件服务不可用");
        }
    }
}