package com.jobbootcamp.learning.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.InternalTokenCodec;
import com.jobbootcamp.contracts.internal.RefundBasisResponse;
import com.jobbootcamp.contracts.internal.RefundQualificationRequest;
import com.jobbootcamp.contracts.internal.RefundQualificationResponse;
import com.jobbootcamp.contracts.internal.RefundStartResponse;
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
 * 学习向交易读取退款基准并提交资格预检；学习只相信交易返回的支付事实和订单快照。
 */
@Component
public class TradeRefundClient {

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final RestClient restClient;
    private final String basisBaseUrl;
    private final String qualifyUrl;
    private final String startUrl;
    private final String internalSecret;

    public TradeRefundClient(@Value("${app.trade.refund-basis-url}") String basisBaseUrl,
                             @Value("${app.trade.refund-qualify-url}") String qualifyUrl,
                             @Value("${app.trade.refund-start-url}") String startUrl,
                             @Value("${app.internal.secret}") String internalSecret) {
        this.basisBaseUrl = basisBaseUrl;
        this.qualifyUrl = qualifyUrl;
        this.startUrl = startUrl;
        this.internalSecret = internalSecret;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public RefundBasisResponse basis(Long orderId, Long userId) {
        String token = token("refunds:basis");
        String url = UriComponentsBuilder.fromUriString(basisBaseUrl)
                .pathSegment(String.valueOf(orderId), "refund-basis")
                .queryParam("userId", userId)
                .build()
                .toUriString();
        try {
            return restClient.get().uri(url)
                    .header(SERVICE_TOKEN_HEADER, token)
                    .retrieve().body(RefundBasisResponse.class);
        } catch (RestClientResponseException e) {
            throw mapFailure(e);
        } catch (ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "交易退款基准服务暂时不可用");
        }
    }

    public RefundQualificationResponse qualify(RefundQualificationRequest request) {
        String token = token("refunds:qualify");
        try {
            return restClient.post().uri(qualifyUrl)
                    .header(SERVICE_TOKEN_HEADER, token)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(request)
                    .retrieve().body(RefundQualificationResponse.class);
        } catch (RestClientResponseException e) {
            throw mapFailure(e);
        } catch (ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "交易退款资格服务暂时不可用");
        }
    }

    public RefundStartResponse start(RefundQualificationRequest request) {
        String token = token("refunds:start");
        try {
            return restClient.post().uri(startUrl)
                    .header(SERVICE_TOKEN_HEADER, token)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(request)
                    .retrieve().body(RefundStartResponse.class);
        } catch (RestClientResponseException e) {
            throw mapFailure(e);
        } catch (ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "交易退款启动服务暂时不可用");
        }
    }

    private String token(String operation) {
        return InternalTokenCodec.issue(internalSecret, "bootcamp-learning",
                "bootcamp-trade", operation, 60);
    }

    private BusinessException mapFailure(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body.contains("ORDER_SYNCING")) {
            return new BusinessException(ErrorCode.ORDER_SYNCING, "订单支付投影尚未就绪");
        }
        if (body.contains("CONFLICT")) {
            return new BusinessException(ErrorCode.CONFLICT, "订单当前不能申请退款");
        }
        return new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "交易退款服务暂时不可用");
    }
}