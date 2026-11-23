package com.jobbootcamp.assignment.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 正式作业与福利题共用的题面/答案校验；福利选择题严格只有一道单选或多选题。
 */
@Component
public class AssignmentContentValidator {

    public void validate(String kind, JsonNode publicBody, JsonNode answerBody) {
        if ("CHAPTER_CHOICE".equals(kind)) {
            validateChoice(publicBody, answerBody, 50);
        } else if ("BENEFIT_CHOICE".equals(kind)) {
            validateChoice(publicBody, answerBody, 1);
        } else {
            validateCode(publicBody, answerBody);
        }
    }

    private void validateChoice(JsonNode publicBody, JsonNode answerBody, int maxQuestions) {
        int minQuestions = 1;
        JsonNode questions = requireArray(publicBody, "questions", minQuestions, maxQuestions);
        JsonNode answers = requireObject(answerBody, "answers");
        JsonNode analysis = answerBody.path("analysis");
        Set<String> questionIds = new HashSet<>();
        Set<String> answerKeys = new HashSet<>();
        answers.fieldNames().forEachRemaining(answerKeys::add);
        for (JsonNode question : questions) {
            String id = text(question, "id", 1, 80);
            if (!questionIds.add(id)) {
                throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "题目 ID 重复：" + id);
            }
            String type = text(question, "type", 1, 20);
            if (!"SINGLE".equals(type) && !"MULTIPLE".equals(type)) {
                throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "题目类型只能是 SINGLE/MULTIPLE");
            }
            text(question, "body", 1, 5000);
            int score = integer(question, "score", 1, 100);
            JsonNode options = requireArray(question, "options", 2, 8);
            Set<String> optionIds = new HashSet<>();
            for (JsonNode option : options) {
                String optionId = text(option, "id", 1, 40);
                if (!optionIds.add(optionId)) {
                    throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "选项 ID 重复：" + optionId);
                }
                text(option, "text", 1, 2000);
            }
            JsonNode correct = answers.get(id);
            if (correct == null || !correct.isArray()) {
                throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "题目缺少标准答案：" + id);
            }
            Set<String> correctIds = new HashSet<>();
            for (JsonNode value : correct) {
                if (!value.isTextual() || !optionIds.contains(value.asText()) || !correctIds.add(value.asText())) {
                    throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "标准答案选项非法：" + id);
                }
            }
            int correctCount = correctIds.size();
            if ("SINGLE".equals(type) ? correctCount != 1 : correctCount < 2) {
                throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID,
                        "SINGLE 需 1 个答案，MULTIPLE 至少 2 个：" + id);
            }
            if (score <= 0) {
                throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "分值必须大于 0");
            }
            if (analysis.has(id)) {
                text(analysis, id, 0, 10000);
            }
        }
        if (!answerKeys.equals(questionIds)) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "标准答案题目集合与题干不一致");
        }
        if (analysis.isObject()) {
            analysis.fieldNames().forEachRemaining(key -> {
                if (!questionIds.contains(key)) {
                    throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "解析引用了未知题目：" + key);
                }
            });
        } else if (!analysis.isMissingNode() && !analysis.isNull()) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "解析必须是对象");
        }
    }

    private void validateCode(JsonNode publicBody, JsonNode answerBody) {
        text(publicBody, "scenario", 1, 20000);
        text(publicBody, "requirements", 1, 20000);
        JsonNode segments = requireArray(publicBody, "segments", 1, 40);
        Set<String> slots = new HashSet<>();
        int totalCode = 0;
        for (JsonNode segment : segments) {
            if (segment.has("slotId")) {
                String slotId = text(segment, "slotId", 1, 40);
                if (!slots.add(slotId)) {
                    throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "slotId 重复：" + slotId);
                }
                String initial = text(segment, "initialCode", 0, 10000);
                totalCode += initial.length();
            } else {
                text(segment, "text", 0, 10000);
            }
        }
        if (slots.isEmpty() || slots.size() > 10) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "代码题需 1～10 个 slot");
        }
        if (totalCode > 30000) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "学生初始代码总长超限");
        }
        JsonNode reference = requireObject(answerBody, "referenceBySlot");
        Set<String> referenceKeys = new HashSet<>();
        reference.fieldNames().forEachRemaining(referenceKeys::add);
        if (!referenceKeys.equals(slots)) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "参考实现 slot 集合与题目不一致");
        }
        int referenceLength = 0;
        for (String slot : slots) {
            referenceLength += text(reference, slot, 1, 30000).length();
        }
        if (referenceLength > 30000) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "参考实现总长超限");
        }
        text(answerBody, "rubric", 1, 10000);
    }

    private JsonNode requireArray(JsonNode parent, String field, int min, int max) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isArray() || value.size() < min || value.size() > max) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID,
                    field + " 必须是 " + min + "～" + max + " 项的数组");
        }
        return value;
    }

    private JsonNode requireObject(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isObject()) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, field + " 必须是对象");
        }
        return value;
    }

    private String text(JsonNode parent, String field, int min, int max) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual()) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, field + " 必须是文本");
        }
        String text = value.asText();
        if (text.length() < min || text.length() > max) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID,
                    field + " 长度必须为 " + min + "～" + max);
        }
        return text;
    }

    private int integer(JsonNode parent, String field, int min, int max) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isInt() || value.asInt() < min || value.asInt() > max) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID,
                    field + " 必须是 " + min + "～" + max + " 的整数");
        }
        return value.asInt();
    }
}