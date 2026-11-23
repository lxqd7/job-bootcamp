package com.jobbootcamp.assignment.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.AccessCheckRequest;
import com.jobbootcamp.contracts.internal.AccessCheckResponse;
import com.jobbootcamp.contracts.internal.BenefitAccessCheckRequest;
import com.jobbootcamp.contracts.internal.BenefitAccessCheckResponse;
import com.jobbootcamp.contracts.internal.InternalTokenCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

@Component
public class LearningAccessClient {

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final RestClient restClient;
    private final String accessUrl;
    private final String benefitAccessUrl;
    private final String internalSecret;

    public LearningAccessClient(@Value("${app.learning.access-url}") String accessUrl,
                                @Value("${app.learning.benefit-access-url}") String benefitAccessUrl,
                                @Value("${app.internal.secret}") String internalSecret) {
        this.accessUrl = accessUrl;
        this.benefitAccessUrl = benefitAccessUrl;
        this.internalSecret = internalSecret;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean allowed(Long userId, Long courseId) {
        String token = InternalTokenCodec.issue(internalSecret, "bootcamp-assignment",
                "bootcamp-learning", "access:check", 60);
        try {
            AccessCheckResponse response = restClient.post()
                    .uri(accessUrl)
                    .header(SERVICE_TOKEN_HEADER, token)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(new AccessCheckRequest(userId, courseId))
                    .retrieve().body(AccessCheckResponse.class);
            return response != null && response.allowed();
        } catch (RestClientResponseException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "学习权益校验失败");
        } catch (ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "学习服务暂时不可用");
        }
    }

    public BenefitAccessCheckResponse benefitAccess(long userId, long courseId,
                                                    java.util.List<BenefitAccessCheckRequest.RouteEpoch> routes) {
        String token = InternalTokenCodec.issue(internalSecret, "bootcamp-assignment",
                "bootcamp-learning", "access:benefit", 60);
        try {
            BenefitAccessCheckResponse response = restClient.post()
                    .uri(benefitAccessUrl)
                    .header(SERVICE_TOKEN_HEADER, token)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(new BenefitAccessCheckRequest(userId, courseId, routes))
                    .retrieve().body(BenefitAccessCheckResponse.class);
            if (response == null) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "福利权益校验失败");
            }
            return response;
        } catch (RestClientResponseException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "福利权益校验失败");
        } catch (ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "学习服务暂时不可用");
        }
    }
}