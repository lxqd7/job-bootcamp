package com.jobbootcamp.gateway.filter;

import com.jobbootcamp.contracts.internal.InternalTokenCodec;
import com.jobbootcamp.contracts.internal.SessionCheckResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthWebFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/v1/auth/",
            "/api/v1/categories",
            "/api/v1/courses",
            "/api/v1/routes",
            "/actuator/");

    private final WebClient webClient;
    private final String sessionCheckUrl;
    private final long timeoutMs;
    private final String internalSecret;
    private final String issuer;
    private final String audience;
    private final String operation;

    public AuthWebFilter(
            @Value("${app.session-check.url}") String sessionCheckUrl,
            @Value("${app.session-check.timeout-ms}") long timeoutMs,
            @Value("${app.internal.secret}") String internalSecret,
            @Value("${app.internal.issuer}") String issuer,
            @Value("${app.internal.audience}") String audience,
            @Value("${app.internal.operation}") String operation) {
        this.webClient = WebClient.builder().build();
        this.sessionCheckUrl = sessionCheckUrl;
        this.timeoutMs = timeoutMs;
        this.internalSecret = internalSecret;
        this.issuer = issuer;
        this.audience = audience;
        this.operation = operation;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String traceId = resolveTraceId(exchange);
        exchange.getResponse().getHeaders().set("X-Trace-Id", traceId);

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequest().getMethod().name())) {
            return chain.filter(exchange);
        }

        if (path.startsWith("/int/v1/")) {
            return writeError(exchange, HttpStatus.NOT_FOUND, "NOT_FOUND", "资源不存在", traceId);
        }

        ServerHttpRequest cleaned = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Role");
                    headers.remove("X-User-Name");
                    headers.remove("X-Session-Id");
                    headers.remove(SERVICE_TOKEN_HEADER);
                })
                .build();
        ServerWebExchange cleanedExchange = exchange.mutate().request(cleaned).build();

        if (!path.startsWith("/api/v1/")) {
            return chain.filter(cleanedExchange);
        }
        if (isPublic(path)) {
            return chain.filter(cleanedExchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登录已失效，请重新登录", traceId);
        }

        String serviceToken = InternalTokenCodec.issue(internalSecret, issuer, audience, operation, 60);
        return webClient.post()
                .uri(sessionCheckUrl)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header(SERVICE_TOKEN_HEADER, serviceToken)
                .retrieve()
                .bodyToMono(SessionCheckResponse.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .map(Optional::of)
                .onErrorResume(error -> {
                    log.warn("session check failed: {}", error.getMessage());
                    return Mono.just(Optional.empty());
                })
                .defaultIfEmpty(Optional.empty())
                .flatMap(result -> {
                    if (result.isEmpty()) {
                        return writeError(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                                "SERVICE_UNAVAILABLE", "认证服务不可用，请稍后重试", traceId);
                    }
                    SessionCheckResponse response = result.get();
                    if (response.active()) {
                        return chain.filter(cleanedExchange);
                    }
                    return writeError(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登录已失效，请重新登录", traceId);
                });
    }

    private boolean isPublic(String path) {
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String resolveTraceId(ServerWebExchange exchange) {
        String incoming = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        if (incoming != null && incoming.matches("[0-9a-f]{32}")) {
            return incoming;
        }
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status,
                                  String code, String message, String traceId) {
        byte[] body = ("{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"data\":null,\"traceId\":\"" + traceId + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("X-Trace-Id", traceId);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}