package com.jobbootcamp.trade.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.InternalTokenCodec;
import com.jobbootcamp.contracts.internal.ProductSnapshotRequest;
import com.jobbootcamp.contracts.internal.ProductSnapshotResponse;
import com.jobbootcamp.contracts.internal.RouteSnapshotRequest;
import com.jobbootcamp.contracts.internal.RouteSnapshotResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class CourseClient {

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final RestClient restClient = RestClient.create();
    private final String snapshotUrl;
    private final String routeSnapshotUrl;
    private final String internalSecret;

    public CourseClient(@Value("${app.course.snapshot-url}") String snapshotUrl,
                        @Value("${app.course.route-snapshot-url}") String routeSnapshotUrl,
                        @Value("${app.internal.secret}") String internalSecret) {
        this.snapshotUrl = snapshotUrl;
        this.routeSnapshotUrl = routeSnapshotUrl;
        this.internalSecret = internalSecret;
    }

    public ProductSnapshotResponse courseSnapshot(Long courseId) {
        String serviceToken = InternalTokenCodec.issue(
                internalSecret, "bootcamp-trade", "bootcamp-course", "products:snapshot", 60);
        return post(snapshotUrl, serviceToken, new ProductSnapshotRequest("COURSE", courseId),
                ProductSnapshotResponse.class);
    }

    public RouteSnapshotResponse routeSnapshot(Long routeId) {
        String serviceToken = InternalTokenCodec.issue(
                internalSecret, "bootcamp-trade", "bootcamp-course", "routes:snapshot", 60);
        return post(routeSnapshotUrl, serviceToken, new RouteSnapshotRequest(routeId),
                RouteSnapshotResponse.class);
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
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "课程服务未返回商品快照");
            }
            return response;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
            }
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "课程服务暂时不可用");
        } catch (ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "课程服务暂时不可用");
        }
    }
}