package com.jobbootcamp.trade.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.InternalTokenCodec;
import com.jobbootcamp.contracts.internal.AccessCheckRequest;
import com.jobbootcamp.contracts.internal.AccessCheckResponse;
import com.jobbootcamp.contracts.internal.PurchaseActionRequest;
import com.jobbootcamp.contracts.internal.PurchasePrepareRequest;
import com.jobbootcamp.contracts.internal.PurchasePrepareResponse;
import com.jobbootcamp.contracts.internal.SourceSelectionRequest;
import com.jobbootcamp.contracts.internal.SourceSelectionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
public class LearningClient {

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final RestClient restClient = RestClient.create();
    private final String accessUrl;
    private final String sourcesUrl;
    private final String prepareUrl;
    private final String commitUrl;
    private final String releaseUrl;
    private final String internalSecret;

    public LearningClient(@Value("${app.learning.access-url}") String accessUrl,
                          @Value("${app.learning.sources-url}") String sourcesUrl,
                          @Value("${app.learning.prepare-url}") String prepareUrl,
                          @Value("${app.learning.commit-url}") String commitUrl,
                          @Value("${app.learning.release-url}") String releaseUrl,
                          @Value("${app.internal.secret}") String internalSecret) {
        this.accessUrl = accessUrl;
        this.sourcesUrl = sourcesUrl;
        this.prepareUrl = prepareUrl;
        this.commitUrl = commitUrl;
        this.releaseUrl = releaseUrl;
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

    public SourceSelectionResponse selectSources(Long userId, List<Long> courseIds) {
        String serviceToken = InternalTokenCodec.issue(
                internalSecret, "bootcamp-trade", "bootcamp-learning", "entitlements:select", 60);
        return post(sourcesUrl, serviceToken, new SourceSelectionRequest(userId, courseIds),
                SourceSelectionResponse.class);
    }

    public PurchasePrepareResponse preparePurchase(Long orderId, Long userId, List<Long> courseIds) {
        String serviceToken = InternalTokenCodec.issue(
                internalSecret, "bootcamp-trade", "bootcamp-learning", "purchases:prepare", 60);
        return post(prepareUrl, serviceToken, new PurchasePrepareRequest(orderId, userId, courseIds),
                PurchasePrepareResponse.class);
    }

    public void commitPurchase(Long orderId, Long userId) {
        String serviceToken = InternalTokenCodec.issue(
                internalSecret, "bootcamp-trade", "bootcamp-learning", "purchases:commit", 60);
        postVoid(commitUrl, serviceToken, new PurchaseActionRequest(orderId, userId));
    }

    public void releasePurchase(Long orderId, Long userId) {
        String serviceToken = InternalTokenCodec.issue(
                internalSecret, "bootcamp-trade", "bootcamp-learning", "purchases:release", 60);
        postVoid(releaseUrl, serviceToken, new PurchaseActionRequest(orderId, userId));
    }

    private <T> T post(String url, String serviceToken, Object body, Class<T> responseType) {
        try {
            T response = restClient.post()
                    .uri(url)
                    .header(SERVICE_TOKEN_HEADER, serviceToken)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(body)
                    .retrieve()
                    .body(responseType);
            if (response == null) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "学习服务未返回结果");
            }
            return response;
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "学习服务暂时不可用");
        }
    }

    private void postVoid(String url, String serviceToken, Object body) {
        try {
            restClient.post()
                    .uri(url)
                    .header(SERVICE_TOKEN_HEADER, serviceToken)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "学习服务暂时不可用");
        }
    }
}