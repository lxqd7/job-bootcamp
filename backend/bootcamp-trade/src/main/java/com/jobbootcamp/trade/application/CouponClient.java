package com.jobbootcamp.trade.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.CouponLockPrepareRequest;
import com.jobbootcamp.contracts.internal.CouponLockResponse;
import com.jobbootcamp.contracts.internal.CouponLockStateRequest;
import com.jobbootcamp.contracts.internal.InternalTokenCodec;
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
 * 交易调用营销锁券接口。HTTP 失败必须显式反馈给下单/支付流程，不能把“券没锁上”当成成功。
 */
@Component
public class CouponClient {

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final RestClient restClient;
    private final String prepareUrl;
    private final String useUrl;
    private final String releaseUrl;
    private final String internalSecret;

    public CouponClient(@Value("${app.marketing.coupon-prepare-url}") String prepareUrl,
                        @Value("${app.marketing.coupon-use-url}") String useUrl,
                        @Value("${app.marketing.coupon-release-url}") String releaseUrl,
                        @Value("${app.internal.secret}") String internalSecret) {
        this.prepareUrl = prepareUrl;
        this.useUrl = useUrl;
        this.releaseUrl = releaseUrl;
        this.internalSecret = internalSecret;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public CouponLockResponse prepare(Long orderId, Long userId, String quoteId,
                                      String pricingHash, List<Long> couponIds) {
        return post(prepareUrl, "coupon:lock",
                new CouponLockPrepareRequest(orderId, userId, quoteId, pricingHash, couponIds));
    }

    public CouponLockResponse use(Long orderId, Long userId, String pricingHash) {
        return post(useUrl, "coupon:use",
                new CouponLockStateRequest(orderId, userId, pricingHash, "PAID"));
    }

    public CouponLockResponse release(Long orderId, Long userId, String pricingHash, String orderState) {
        return post(releaseUrl, "coupon:release",
                new CouponLockStateRequest(orderId, userId, pricingHash, orderState));
    }

    private CouponLockResponse post(String url, String operation, Object body) {
        String token = InternalTokenCodec.issue(internalSecret, "bootcamp-trade",
                "bootcamp-marketing", operation, 60);
        try {
            return restClient.post()
                    .uri(url)
                    .header(SERVICE_TOKEN_HEADER, token)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(body)
                    .retrieve()
                    .body(CouponLockResponse.class);
        } catch (RestClientResponseException e) {
            throw mapFailure(e);
        } catch (ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "营销锁券服务暂时不可用");
        }
    }

    private BusinessException mapFailure(RestClientResponseException error) {
        String body = error.getResponseBodyAsString();
        if (body.contains("COUPON_COMBINATION_INVALID")) {
            return new BusinessException(ErrorCode.COUPON_COMBINATION_INVALID, "所选优惠券组合已不可行");
        }
        if (body.contains("COUPON_UNAVAILABLE")) {
            return new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "优惠券已被占用、过期或不可用");
        }
        if (body.contains("QUOTE_CHANGED")) {
            return new BusinessException(ErrorCode.QUOTE_CHANGED, "报价或优惠方案已变化");
        }
        return new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "营销锁券服务暂时不可用");
    }
}