package com.jobbootcamp.interaction.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.AccessCheckRequest;
import com.jobbootcamp.contracts.internal.AccessCheckResponse;
import com.jobbootcamp.contracts.internal.InternalTokenCodec;
import com.jobbootcamp.contracts.internal.ProfileBatchItem;
import com.jobbootcamp.contracts.internal.ProfileBatchRequest;
import com.jobbootcamp.contracts.internal.ProfileBatchResponse;
import com.jobbootcamp.contracts.internal.TeacherScopeCheckRequest;
import com.jobbootcamp.contracts.internal.TeacherScopeCheckResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 互动服务分别校验学生学习权益、教师课程范围，并批量取昵称做安全投影。
 */
@Component
public class InteractionAccessClient {

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final RestClient restClient;
    private final String accessUrl;
    private final String teacherScopeUrl;
    private final String profileUrl;
    private final String internalSecret;

    public InteractionAccessClient(@Value("${app.learning.access-url}") String accessUrl,
                                   @Value("${app.course.teacher-scope-url}") String teacherScopeUrl,
                                   @Value("${app.user.profile-batch-url}") String profileUrl,
                                   @Value("${app.internal.secret}") String internalSecret) {
        this.accessUrl = accessUrl;
        this.teacherScopeUrl = teacherScopeUrl;
        this.profileUrl = profileUrl;
        this.internalSecret = internalSecret;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean studentAllowed(Long userId, Long courseId) {
        String token = InternalTokenCodec.issue(internalSecret, "bootcamp-interaction",
                "bootcamp-learning", "access:check", 60);
        try {
            AccessCheckResponse response = restClient.post()
                    .uri(accessUrl)
                    .header(SERVICE_TOKEN_HEADER, token)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(new AccessCheckRequest(userId, courseId))
                    .retrieve().body(AccessCheckResponse.class);
            return response != null && response.allowed();
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "学习权益校验失败");
        }
    }

    public boolean teacherAssigned(Long teacherId, Long courseId) {
        String token = InternalTokenCodec.issue(internalSecret, "bootcamp-interaction",
                "bootcamp-course", "teachers:scope", 60);
        try {
            TeacherScopeCheckResponse response = restClient.post()
                    .uri(teacherScopeUrl)
                    .header(SERVICE_TOKEN_HEADER, token)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(new TeacherScopeCheckRequest(teacherId, courseId))
                    .retrieve().body(TeacherScopeCheckResponse.class);
            return response != null && response.allowed();
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "教师范围校验失败");
        }
    }

    public Map<Long, String> nicknames(List<Long> userIds) {
        Map<Long, ProfileBatchItem> profiles = profiles(userIds);
        Map<Long, String> result = new HashMap<>();
        profiles.forEach((id, item) -> result.put(id, item.nickname()));
        return result;
    }

    public Map<Long, ProfileBatchItem> profiles(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        String token = InternalTokenCodec.issue(internalSecret, "bootcamp-interaction",
                "bootcamp-user", "profiles:batch", 60);
        try {
            ProfileBatchResponse response = restClient.post()
                    .uri(profileUrl)
                    .header(SERVICE_TOKEN_HEADER, token)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(new ProfileBatchRequest(userIds.stream().map(String::valueOf).toList()))
                    .retrieve().body(ProfileBatchResponse.class);
            Map<Long, ProfileBatchItem> result = new HashMap<>();
            if (response != null && response.items() != null) {
                for (ProfileBatchItem item : response.items()) {
                    result.put(Long.valueOf(item.id()), item);
                }
            }
            return result;
        } catch (RestClientResponseException | ResourceAccessException e) {
            return Map.of();
        }
    }
}