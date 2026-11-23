package com.jobbootcamp.assignment.application;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 只接受严格 JSON；拒绝 Markdown 包裹、未知字段、非法 verdict、未知 slot 和数值评分。
 */
@Component
public class AiReviewResponseParser {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final Set<String> TOP_FIELDS = Set.of(
            "verdict", "summary", "issues", "suggestions", "limitations");
    private static final Set<String> ISSUE_FIELDS = Set.of(
            "slotId", "evidence", "reason", "suggestion");
    private static final Set<String> VERDICTS = Set.of("SATISFIED", "PARTIAL", "NEEDS_CHANGE");

    public AiReviewEngine.ReviewFeedback parse(String content, Set<String> slotIds) throws FormatException {
        if (content == null || content.isBlank()) {
            throw new FormatException("empty content");
        }
        String trimmed = content.trim();
        if (trimmed.contains("```") || !trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new FormatException("markdown or non-object content");
        }
        JsonNode root;
        try {
            root = JSON.readTree(trimmed);
        } catch (Exception e) {
            throw new FormatException("invalid json");
        }
        if (root == null || !root.isObject()) {
            throw new FormatException("root is not object");
        }
        Set<String> unknown = new LinkedHashSet<>();
        Iterator<String> fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            if (!TOP_FIELDS.contains(field)) {
                unknown.add(field);
            }
        }
        if (!unknown.isEmpty()) {
            throw new FormatException("unknown top-level fields");
        }
        String verdict = text(root, "verdict", 1, 16);
        if (!VERDICTS.contains(verdict)) {
            throw new FormatException("invalid verdict");
        }
        String summary = text(root, "summary", 1, 1000);
        List<Object> issues = issues(root.path("issues"), slotIds);
        List<String> suggestions = stringArray(root.path("suggestions"), 10, 1000, "suggestions");
        List<String> limitations = stringArray(root.path("limitations"), 10, 1000, "limitations");
        if (!"SATISFIED".equals(verdict) && issues.isEmpty()) {
            throw new FormatException("non-satisfied verdict requires an issue");
        }
        return new AiReviewEngine.ReviewFeedback(verdict, summary, issues, suggestions, limitations, false);
    }

    private List<Object> issues(JsonNode node, Set<String> slotIds) throws FormatException {
        if (node == null || !node.isArray()) {
            throw new FormatException("issues must be array");
        }
        if (node.size() > 20) {
            throw new FormatException("too many issues");
        }
        List<Object> result = new ArrayList<>();
        for (JsonNode issue : node) {
            if (!issue.isObject()) {
                throw new FormatException("issue must be object");
            }
            Set<String> fields = new LinkedHashSet<>();
            issue.fieldNames().forEachRemaining(fields::add);
            if (!fields.equals(ISSUE_FIELDS)) {
                throw new FormatException("issue fields are invalid");
            }
            String slotId = text(issue, "slotId", 1, 40);
            if (!slotIds.contains(slotId)) {
                throw new FormatException("unknown slotId");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("slotId", slotId);
            item.put("evidence", text(issue, "evidence", 1, 2000));
            item.put("reason", text(issue, "reason", 1, 2000));
            item.put("suggestion", text(issue, "suggestion", 1, 2000));
            result.add(item);
        }
        return result;
    }

    private List<String> stringArray(JsonNode node, int max, int maxLength, String name)
            throws FormatException {
        if (node == null || !node.isArray()) {
            throw new FormatException(name + " must be array");
        }
        if (node.size() > max) {
            throw new FormatException(name + " has too many items");
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw new FormatException(name + " item must be text");
            }
            String value = item.asText();
            if (value.length() > maxLength || value.isBlank()) {
                throw new FormatException(name + " item length is invalid");
            }
            result.add(value);
        }
        return result;
    }

    private String text(JsonNode parent, String field, int min, int max) throws FormatException {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual()) {
            throw new FormatException(field + " must be text");
        }
        String text = value.asText();
        if (text.length() < min || text.length() > max || text.isBlank()) {
            throw new FormatException(field + " length is invalid");
        }
        return text;
    }

    public static class FormatException extends Exception {

        public FormatException(String message) {
            super(message);
        }
    }
}