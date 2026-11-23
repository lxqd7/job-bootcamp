package com.jobbootcamp.assignment.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbootcamp.assignment.domain.AiAttempt;
import com.jobbootcamp.assignment.domain.AiTask;
import com.jobbootcamp.assignment.domain.AssignmentVersion;
import com.jobbootcamp.assignment.domain.Submission;
import com.jobbootcamp.assignment.infrastructure.AiAttemptMapper;
import com.jobbootcamp.assignment.infrastructure.AiTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * review-v1 的 HTTP 引擎：Qwen 为主，只有 Qwen 不可请求时才在本轮尝试 DeepSeek。
 * 每次出站调用先写 ai_attempt PLANNED 并占预算；格式错误只做同供应商一次修复，
 * 不会切换供应商或开启新轮次。调用总数硬上限 6 次。
 */
@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "http")
public class HttpReviewEngine implements AiReviewEngine {

    private static final Logger log = LoggerFactory.getLogger(HttpReviewEngine.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_INPUT_BYTES = 192 * 1024;

    private final Provider qwen;
    private final Provider deepseek;
    private final AiReviewPrompt prompt;
    private final AiReviewResponseParser parser;
    private final AiTaskMapper taskMapper;
    private final AiAttemptMapper attemptMapper;
    private final int maxCalls;
    private final long[] retryDelaysMs;

    public HttpReviewEngine(AiReviewPrompt prompt,
                            AiReviewResponseParser parser,
                            AiTaskMapper taskMapper,
                            AiAttemptMapper attemptMapper,
                            @Value("${app.ai.http.connect-timeout-ms:5000}") long connectTimeoutMs,
                            @Value("${app.ai.http.read-timeout-ms:45000}") long readTimeoutMs,
                            @Value("${app.ai.http.retry-delays-ms:5000,20000}") String retryDelays,
                            @Value("${app.ai.max-calls:6}") int maxCalls,
                            @Value("${app.ai.http.qwen.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String qwenBase,
                            @Value("${app.ai.http.qwen.model:qwen-plus}") String qwenModel,
                            @Value("${app.ai.http.qwen.api-key:}") String qwenKey,
                            @Value("${app.ai.http.deepseek.base-url:https://api.deepseek.com}") String deepseekBase,
                            @Value("${app.ai.http.deepseek.model:deepseek-chat}") String deepseekModel,
                            @Value("${app.ai.http.deepseek.api-key:}") String deepseekKey) {
        this.prompt = prompt;
        this.parser = parser;
        this.taskMapper = taskMapper;
        this.attemptMapper = attemptMapper;
        this.maxCalls = Math.max(1, maxCalls);
        this.retryDelaysMs = parseDelays(retryDelays);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.max(100, connectTimeoutMs)));
        factory.setReadTimeout(Duration.ofMillis(Math.max(100, readTimeoutMs)));
        this.qwen = new Provider("QWEN", qwenBase, qwenModel, qwenKey, factory);
        this.deepseek = new Provider("DEEPSEEK", deepseekBase, deepseekModel, deepseekKey, factory);
        if (!qwen.configured() && !deepseek.configured()) {
            throw new IllegalStateException("AI_MODE=http requires QWEN_API_KEY or DEEPSEEK_API_KEY");
        }
        log.info("AI HTTP engine ready: qwen={}, deepseek={}, maxCalls={}",
                qwen.configured() ? "configured" : "disabled",
                deepseek.configured() ? "configured" : "disabled", this.maxCalls);
    }

    @Override
    public ReviewFeedback generate(AiTask task, Submission submission, AssignmentVersion version) {
        if (!"review-v1".equals(task.getPromptVersion())) {
            throw new AiEngineException("AI_UNAVAILABLE", "unsupported prompt version");
        }
        Map<String, Object> materials = prompt.materials(version, submission);
        String userContent = prompt.userContent(materials, false);
        if (userContent.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
            throw new AiEngineException("AI_INVALID_RESPONSE", "review input exceeds 192KiB");
        }
        Set<String> slots = prompt.slotIds(version);
        for (int round = 1; round <= 3; round += 1) {
            checkDeadline(task);
            CallOutcome qwenResult = call(qwen, task, userContent, slots, round, false);
            if (qwenResult.success()) {
                return qwenResult.feedback();
            }
            if (qwenResult.formatError()) {
                return repair(qwen, task, materials, slots, round);
            }
            if (qwenResult.inputError()) {
                throw new AiEngineException("AI_INVALID_RESPONSE", "provider rejected review input");
            }
            CallOutcome deepseekResult = call(deepseek, task, userContent, slots, round, false);
            if (deepseekResult.success()) {
                return deepseekResult.feedback();
            }
            if (deepseekResult.formatError()) {
                return repair(deepseek, task, materials, slots, round);
            }
            if (deepseekResult.inputError()) {
                throw new AiEngineException("AI_INVALID_RESPONSE", "provider rejected review input");
            }
            boolean anyTemporary = qwenResult.temporary() || deepseekResult.temporary();
            if (!anyTemporary) {
                throw new AiEngineException("AI_UNAVAILABLE", "both providers unavailable");
            }
            if (round < 3) {
                sleep(retryDelay(round));
                checkDeadline(task);
            }
        }
        throw new AiEngineException("AI_UNAVAILABLE", "provider retries exhausted");
    }

    private ReviewFeedback repair(Provider provider, AiTask task, Map<String, Object> materials,
                                  Set<String> slots, int round) {
        CallOutcome repair;
        try {
            repair = call(provider, task, prompt.userContent(materials, true), slots, round, true);
        } catch (AiEngineException e) {
            if ("AI_UNAVAILABLE".equals(e.safeCode()) && e.getMessage().contains("budget")) {
                throw new AiEngineException("AI_INVALID_RESPONSE", "format repair has no call budget");
            }
            throw e;
        }
        if (repair.success()) {
            return repair.feedback();
        }
        if (repair.formatError() || repair.inputError()) {
            throw new AiEngineException("AI_INVALID_RESPONSE", "format repair failed");
        }
        throw new AiEngineException("AI_UNAVAILABLE", "format repair provider unavailable");
    }

    private CallOutcome call(Provider provider, AiTask task, String userContent,
                             Set<String> slots, int round, boolean repair) {
        if (!provider.configured()) {
            return new CallOutcome(Outcome.CONFIG, null, null);
        }
        if (attemptMapper.countByTask(task.getId()) >= maxCalls) {
            throw new AiEngineException("AI_UNAVAILABLE", "call budget exhausted");
        }
        int lastIndex = attemptMapper.maxCallIndex(task.getId());
        if (Math.max(lastIndex, attemptMapper.countByTask(task.getId())) >= maxCalls) {
            throw new AiEngineException("AI_UNAVAILABLE", "call budget exhausted");
        }
        int callIndex = lastIndex + 1;
        LocalDateTime now = now();
        AiAttempt attempt = new AiAttempt();
        attempt.setTaskId(task.getId());
        attempt.setCallIndex(callIndex);
        attempt.setProvider(provider.name());
        attempt.setModel(provider.model());
        attempt.setState("PLANNED");
        attempt.setStartedAt(now);
        attempt.setCreatedAt(now);
        attemptMapper.insert(attempt);
        if (taskMapper.updateProgress(task.getId(), task.getLeaseToken(), callIndex, round, repair, now) == 0) {
            finish(attempt.getId(), "FAILED", null, "LEASE_LOST", null);
            throw new AiLeaseLostException(task.getId());
        }
        if (!now.isBefore(task.getDeadlineAt())) {
            finish(attempt.getId(), "TIMEOUT", null, "TIMEOUT", null);
            throw new AiEngineException("AI_TIMEOUT", "deadline exceeded before provider call");
        }
        try {
            String body = provider.client().post()
                    .uri(provider.endpoint())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.apiKey())
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(request(provider, userContent))
                    .retrieve()
                    .body(String.class);
            String content;
            try {
                content = extractContent(body);
            } catch (AiReviewResponseParser.FormatException e) {
                finish(attempt.getId(), "FAILED", 200, "FORMAT", hash(body));
                return new CallOutcome(Outcome.FORMAT, null, 200);
            }
            try {
                ReviewFeedback feedback = parser.parse(content, slots);
                finish(attempt.getId(), "SUCCESS", 200, null, hash(content));
                return new CallOutcome(Outcome.SUCCESS, feedback, 200);
            } catch (AiReviewResponseParser.FormatException e) {
                finish(attempt.getId(), "FAILED", 200, "FORMAT", hash(content));
                return new CallOutcome(Outcome.FORMAT, null, 200);
            }
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 400 || status == 413 || status == 422) {
                finish(attempt.getId(), "FAILED", status, "INPUT", null);
                return new CallOutcome(Outcome.INPUT, null, status);
            }
            if (status == 401 || status == 403 || status == 404) {
                finish(attempt.getId(), "FAILED", status, "CONFIG", null);
                return new CallOutcome(Outcome.CONFIG, null, status);
            }
            if (status == 408 || status == 429 || status >= 500) {
                finish(attempt.getId(), "FAILED", status, "TEMPORARY", null);
                return new CallOutcome(Outcome.TEMPORARY, null, status);
            }
            finish(attempt.getId(), "FAILED", status, "INPUT", null);
            return new CallOutcome(Outcome.INPUT, null, status);
        } catch (RestClientException e) {
            boolean timeout = isTimeout(e);
            finish(attempt.getId(), timeout ? "TIMEOUT" : "FAILED", null,
                    timeout ? "TIMEOUT" : "TEMPORARY", null);
            log.warn("AI provider transport failed: provider={}, task={}, timeout={}",
                    provider.name(), task.getId(), timeout);
            return new CallOutcome(Outcome.TEMPORARY, null, null);
        } catch (RuntimeException e) {
            finish(attempt.getId(), "FAILED", null, "INTERNAL", null);
            log.warn("AI provider call failed: provider={}, task={}, error={}",
                    provider.name(), task.getId(), e.getClass().getSimpleName());
            return new CallOutcome(Outcome.TEMPORARY, null, null);
        }
    }

    private Map<String, Object> request(Provider provider, String userContent) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", provider.model());
        body.put("messages", List.of(
                Map.of("role", "system", "content", prompt.systemPrompt()),
                Map.of("role", "user", "content", userContent)));
        body.put("temperature", 0);
        body.put("stream", false);
        body.put("max_tokens", 2000);
        return body;
    }

    private String extractContent(String body) throws AiReviewResponseParser.FormatException {
        if (body == null || body.isBlank()) {
            throw new AiReviewResponseParser.FormatException("empty provider response");
        }
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (Exception e) {
            throw new AiReviewResponseParser.FormatException("provider response is not json");
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new AiReviewResponseParser.FormatException("choices missing");
        }
        JsonNode first = choices.get(0);
        if ("length".equals(first.path("finish_reason").asText(""))) {
            throw new AiReviewResponseParser.FormatException("output truncated");
        }
        JsonNode message = first.path("message");
        if (message.path("refusal").isTextual() && !message.path("refusal").asText().isBlank()) {
            throw new AiReviewResponseParser.FormatException("provider refused");
        }
        JsonNode content = message.path("content");
        if (!content.isTextual() || content.asText().isBlank()) {
            throw new AiReviewResponseParser.FormatException("content missing");
        }
        return content.asText();
    }

    private void finish(Long attemptId, String state, Integer httpStatus, String errorClass, String hash) {
        attemptMapper.finish(attemptId, state, httpStatus, errorClass, hash, now());
    }

    private void checkDeadline(AiTask task) {
        if (!now().isBefore(task.getDeadlineAt())) {
            throw new AiEngineException("AI_TIMEOUT", "task deadline exceeded");
        }
    }

    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiEngineException("AI_UNAVAILABLE", "review interrupted");
        }
    }

    private long retryDelay(int round) {
        return retryDelaysMs[Math.min(round - 1, retryDelaysMs.length - 1)];
    }

    private boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String name = current.getClass().getSimpleName();
            if (name.contains("Timeout") || current instanceof java.net.SocketTimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String hash(String value) {
        if (value == null) {
            return null;
        }
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("response hash failed", e);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static long[] parseDelays(String value) {
        String[] parts = value.split(",");
        long[] delays = new long[Math.max(1, parts.length)];
        for (int i = 0; i < parts.length; i += 1) {
            try {
                delays[i] = Math.max(0, Long.parseLong(parts[i].trim()));
            } catch (NumberFormatException e) {
                delays[i] = 5000;
            }
        }
        return delays;
    }

    private enum Outcome { SUCCESS, FORMAT, INPUT, TEMPORARY, CONFIG }

    private record CallOutcome(Outcome outcome, ReviewFeedback feedback, Integer httpStatus) {

        private boolean success() { return outcome == Outcome.SUCCESS; }
        private boolean formatError() { return outcome == Outcome.FORMAT; }
        private boolean inputError() { return outcome == Outcome.INPUT; }
        private boolean temporary() { return outcome == Outcome.TEMPORARY; }
    }

    private record Provider(String name, String baseUrl, String model, String apiKey, RestClient client) {

        private Provider(String name, String baseUrl, String model, String apiKey,
                         SimpleClientHttpRequestFactory factory) {
            this(name, strip(baseUrl), model, apiKey,
                    RestClient.builder().requestFactory(factory).build());
        }

        private String endpoint() {
            return baseUrl + "/chat/completions";
        }

        private boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }

        private static String strip(String value) {
            return value == null ? "" : value.replaceAll("/+$", "");
        }
    }
}