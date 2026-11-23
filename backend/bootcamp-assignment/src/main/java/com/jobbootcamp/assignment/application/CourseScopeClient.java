package com.jobbootcamp.assignment.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.InternalTokenCodec;
import com.jobbootcamp.contracts.internal.TeacherBenefitScopeRequest;
import com.jobbootcamp.contracts.internal.TeacherBenefitScopeResponse;
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
import java.util.List;

/**
 * 教师数据范围不能缓存在作业库，每次读取学生作答前向课程服务确认当前分配。
 */
@Component
public class CourseScopeClient {

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final RestClient restClient;
    private final String scopeUrl;
    private final String benefitScopeUrl;
    private final String internalSecret;

    public CourseScopeClient(@Value("${app.course.scope-url}") String scopeUrl,
                             @Value("${app.course.benefit-scope-url}") String benefitScopeUrl,
                             @Value("${app.internal.secret}") String internalSecret) {
        this.scopeUrl = scopeUrl;
        this.benefitScopeUrl = benefitScopeUrl;
        this.internalSecret = internalSecret;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean allowed(Long teacherId, Long courseId) {
        String token = InternalTokenCodec.issue(internalSecret, "bootcamp-assignment",
                "bootcamp-course", "teachers:scope", 60);
        try {
            TeacherScopeCheckResponse response = restClient.post()
                    .uri(scopeUrl)
                    .header(SERVICE_TOKEN_HEADER, token)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(new TeacherScopeCheckRequest(teacherId, courseId))
                    .retrieve().body(TeacherScopeCheckResponse.class);
            return response != null && response.allowed();
        } catch (RestClientResponseException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "教师范围校验失败");
        } catch (ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "课程服务暂时不可用");
        }
    }

    public TeacherBenefitScopeResponse benefitScope(Long teacherId, Long courseId, List<Long> routeIds) {
        String token = InternalTokenCodec.issue(internalSecret, "bootcamp-assignment",
                "bootcamp-course", "teachers:benefit-scope", 60);
        try {
            TeacherBenefitScopeResponse response = restClient.post()
                    .uri(benefitScopeUrl)
                    .header(SERVICE_TOKEN_HEADER, token)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(new TeacherBenefitScopeRequest(teacherId, courseId, routeIds))
                    .retrieve().body(TeacherBenefitScopeResponse.class);
            if (response == null) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "教师福利范围校验失败");
            }
            return response;
        } catch (RestClientResponseException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "教师福利范围校验失败");
        } catch (ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "课程服务暂时不可用");
        }
    }
}