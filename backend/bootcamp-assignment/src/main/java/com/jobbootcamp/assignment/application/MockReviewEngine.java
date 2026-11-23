package com.jobbootcamp.assignment.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbootcamp.assignment.domain.AiTask;
import com.jobbootcamp.assignment.domain.AssignmentVersion;
import com.jobbootcamp.assignment.domain.Submission;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可复现的模拟点评：不访问外部服务，结果带 isMock，供无密钥环境完整验收任务状态。
 */
@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "mock", matchIfMissing = true)
public class MockReviewEngine implements AiReviewEngine {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final long delayMs;

    public MockReviewEngine(@Value("${app.ai.mock-delay-ms:0}") long delayMs) {
        this.delayMs = Math.max(0, delayMs);
    }

    @Override
    public ReviewFeedback generate(AiTask task, Submission submission, AssignmentVersion version) {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("mock review interrupted", e);
            }
        }
        JsonNode publicBody = read(version.getPublicBody());
        JsonNode answers = read(submission.getAnswersJson());
        String verdict = "SATISFIED";
        String summary = "Mock 点评：补全内容已填写，请以真实 AI 点评为准。";
        List<Object> issues = new ArrayList<>();
        List<String> suggestions = List.of("检查边界条件", "确认代码符合题目要求");
        List<String> limitations = List.of("Mock 模式未执行代码，不代表真实编译或测试结果");
        for (JsonNode segment : publicBody.path("segments")) {
            if (!segment.has("slotId")) {
                continue;
            }
            String slot = segment.path("slotId").asText();
            String code = answers.path(slot).asText("");
            if (code.isBlank()) {
                verdict = "PARTIAL";
                summary = "Mock 点评：存在未填写的 slot。";
                Map<String, Object> issue = new LinkedHashMap<>();
                issue.put("slotId", slot);
                issue.put("evidence", "该 slot 为空");
                issue.put("reason", "缺少补全代码");
                issue.put("suggestion", "补充该 slot 的实现并重新提交");
                issues.add(issue);
            }
        }
        return new ReviewFeedback(verdict, summary, issues, suggestions, limitations, true);
    }

    private JsonNode read(String value) {
        try {
            return JSON.readTree(value);
        } catch (Exception e) {
            return JSON.createObjectNode();
        }
    }
}