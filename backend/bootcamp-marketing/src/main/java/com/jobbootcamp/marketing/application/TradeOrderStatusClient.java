package com.jobbootcamp.marketing.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.InternalTokenCodec;
import com.jobbootcamp.contracts.internal.OrderStatusBatchRequest;
import com.jobbootcamp.contracts.internal.OrderStatusBatchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;

/**
 * 营销对账时只读取交易订单状态，不修改交易数据；用内部 HMAC 证明来源。
 */
@Component
public class TradeOrderStatusClient {

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final RestClient restClient;
    private final String statusUrl;
    private final String internalSecret;

    public TradeOrderStatusClient(@Value("${app.trade.order-status-url}") String statusUrl,
                                  @Value("${app.internal.secret}") String internalSecret) {
        this.statusUrl = statusUrl;
        this.internalSecret = internalSecret;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public OrderStatusBatchResponse statuses(List<Long> orderIds) {
        String token = InternalTokenCodec.issue(internalSecret, "bootcamp-marketing",
                "bootcamp-trade", "orders:status", 60);
        try {
            return restClient.post()
                    .uri(statusUrl)
                    .header(SERVICE_TOKEN_HEADER, token)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(new OrderStatusBatchRequest(orderIds))
                    .retrieve()
                    .body(OrderStatusBatchResponse.class);
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "交易订单状态暂时不可用");
        }
    }
}