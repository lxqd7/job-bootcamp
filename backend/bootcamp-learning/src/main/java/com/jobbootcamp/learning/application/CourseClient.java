package com.jobbootcamp.learning.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.CourseCatalogRequest;
import com.jobbootcamp.contracts.internal.CourseCatalogResponse;
import com.jobbootcamp.contracts.internal.InternalTokenCodec;
import com.jobbootcamp.contracts.internal.LessonMetadataRequest;
import com.jobbootcamp.contracts.internal.LessonMetadataResponse;
import com.jobbootcamp.contracts.internal.PlayAuthorizationResponse;
import com.jobbootcamp.contracts.internal.PlaySessionCheckRequest;
import com.jobbootcamp.contracts.internal.PlaySessionCheckResponse;
import com.jobbootcamp.contracts.internal.PlaySessionIssueRequest;
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
    private final String metadataUrl;
    private final String issueUrl;
    private final String checkUrl;
    private final String catalogUrl;
    private final String internalSecret;

    public CourseClient(@Value("${app.course.metadata-url}") String metadataUrl,
                        @Value("${app.course.play-issue-url}") String issueUrl,
                        @Value("${app.course.play-check-url}") String checkUrl,
                        @Value("${app.course.catalog-url}") String catalogUrl,
                        @Value("${app.internal.secret}") String internalSecret) {
        this.metadataUrl = metadataUrl;
        this.issueUrl = issueUrl;
        this.checkUrl = checkUrl;
        this.catalogUrl = catalogUrl;
        this.internalSecret = internalSecret;
    }

    public CourseCatalogResponse catalog(Long courseId) {
        return post(catalogUrl, "courses:catalog", new CourseCatalogRequest(courseId),
                CourseCatalogResponse.class);
    }

    public LessonMetadataResponse metadata(Long lessonId) {
        return post(metadataUrl, "lessons:metadata", new LessonMetadataRequest(lessonId), LessonMetadataResponse.class);
    }

    public PlayAuthorizationResponse issue(Long userId, Long authSessionId, Long lessonId) {
        return post(issueUrl, "play-sessions:issue",
                new PlaySessionIssueRequest(userId, authSessionId, lessonId), PlayAuthorizationResponse.class);
    }

    public PlaySessionCheckResponse check(Long userId, String playSessionId, Long lessonId) {
        return post(checkUrl, "play-sessions:check",
                new PlaySessionCheckRequest(userId, playSessionId, lessonId), PlaySessionCheckResponse.class);
    }

    private <T> T post(String url, String operation, Object body, Class<T> responseType) {
        String serviceToken = InternalTokenCodec.issue(
                internalSecret, "bootcamp-learning", "bootcamp-course", operation, 60);
        try {
            T result = restClient.post()
                    .uri(url)
                    .header(SERVICE_TOKEN_HEADER, serviceToken)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(body)
                    .retrieve()
                    .body(responseType);
            if (result == null) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "课程服务未返回结果");
            }
            return result;
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "课程服务暂时不可用");
        }
    }
}