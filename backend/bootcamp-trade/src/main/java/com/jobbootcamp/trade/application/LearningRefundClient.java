package com.jobbootcamp.trade.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.InternalTokenCodec;
import com.jobbootcamp.contracts.internal.SourceLifecycleRequest;
import com.jobbootcamp.contracts.internal.SourceLifecycleResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

/**
 * 交易在渠道退款成功后调用学习撤权，明确未退款关闭时调用恢复。
 */
@Component
public class LearningRefundClient {

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final RestClient restClient;
    private final String revokeUrl;
    private final String restoreUrl;
    private final String internalSecret;

    public LearningRefundClient(@Value("${app.learning.revoke-url}") String revokeUrl,
                                @Value("${app.learning.restore-url}") String restoreUrl,
                                @Value("${app.internal.secret}") String internalSecret) {
        this.revokeUrl = revokeUrl;
        this.restoreUrl = restoreUrl;
        this.internalSecret = internalSecret;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public SourceLifecycleResponse revoke(SourceLifecycleRequest request) {
        return post(revokeUrl, "sources:revoke", request);
    }

    public SourceLifecycleResponse restore(SourceLifecycleRequest request) {
        return post(restoreUrl, "sources:restore", request);
    }

    private SourceLifecycleResponse post(String url, String operation, SourceLifecycleRequest request) {
        String token = InternalTokenCodec.issue(internalSecret, "bootcamp-trade",
                "bootcamp-learning", operation, 60);
        try {
            return restClient.post().uri(url)
                    .header(SERVICE_TOKEN_HEADER, token)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(request)
                    .retrieve().body(SourceLifecycleResponse.class);
        } catch (RestClientResponseException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "学习来源状态服务拒绝请求");
        } catch (ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "学习来源状态服务暂时不可用");
        }
    }
}