package com.jobbootcamp.trade.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.InternalTokenCodec;
import com.jobbootcamp.contracts.internal.PricingCalculateRequest;
import com.jobbootcamp.contracts.internal.PricingCalculateResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

/**
 * 交易调用营销内部定价接口。营销在 500ms 内返回 CALCULATING，因此交易侧的超时必须更短，
 * 避免营销进程卡死时占满报价请求线程。
 */
@Component
public class PricingClient {

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final RestClient restClient;
    private final String calculateUrl;
    private final String statusUrl;
    private final String internalSecret;

    public PricingClient(@Value("${app.marketing.pricing-calculate-url}") String calculateUrl,
                         @Value("${app.marketing.pricing-status-url}") String statusUrl,
                         @Value("${app.internal.secret}") String internalSecret) {
        this.calculateUrl = calculateUrl;
        this.statusUrl = statusUrl;
        this.internalSecret = internalSecret;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public PricingCalculateResponse calculate(PricingCalculateRequest request) {
        String token = InternalTokenCodec.issue(
                internalSecret, "bootcamp-trade", "bootcamp-marketing", "pricing:calculate", 60);
        try {
            return restClient.post()
                    .uri(calculateUrl)
                    .header(SERVICE_TOKEN_HEADER, token)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(request)
                    .retrieve()
                    .body(PricingCalculateResponse.class);
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "营销定价服务暂时不可用");
        }
    }

    public PricingCalculateResponse status(String quoteId, Long userId) {
        String token = InternalTokenCodec.issue(
                internalSecret, "bootcamp-trade", "bootcamp-marketing", "pricing:status", 60);
        try {
            String url = UriComponentsBuilder.fromUriString(statusUrl)
                    .pathSegment(quoteId)
                    .queryParam("userId", userId)
                    .build()
                    .toUriString();
            return restClient.get()
                    .uri(url)
                    .header(SERVICE_TOKEN_HEADER, token)
                    .retrieve()
                    .body(PricingCalculateResponse.class);
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "营销定价服务暂时不可用");
        }
    }
}