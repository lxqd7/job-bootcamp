package com.jobbootcamp.marketing.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.InternalTokenCodec;
import com.jobbootcamp.contracts.internal.ProfileBatchItem;
import com.jobbootcamp.contracts.internal.ProfileBatchRequest;
import com.jobbootcamp.contracts.internal.ProfileBatchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
public class ProfileClient {

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final RestClient restClient = RestClient.create();
    private final String batchUrl;
    private final String internalSecret;

    public ProfileClient(@Value("${app.user.profile-batch-url}") String batchUrl,
                         @Value("${app.internal.secret}") String internalSecret) {
        this.batchUrl = batchUrl;
        this.internalSecret = internalSecret;
    }

    public List<ProfileBatchItem> fetch(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        String serviceToken = InternalTokenCodec.issue(
                internalSecret, "bootcamp-marketing", "bootcamp-user", "profiles:batch", 60);
        List<String> ids = userIds.stream().map(String::valueOf).toList();
        try {
            ProfileBatchResponse response = restClient.post()
                    .uri(batchUrl)
                    .header(SERVICE_TOKEN_HEADER, serviceToken)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(new ProfileBatchRequest(ids))
                    .retrieve()
                    .body(ProfileBatchResponse.class);
            return response == null || response.items() == null ? List.of() : response.items();
        } catch (RestClientResponseException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "用户资料服务不可用");
        } catch (ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "用户资料服务不可用");
        }
    }
}