package com.jobbootcamp.assignment.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobbootcamp.assignment.domain.Assignment;
import com.jobbootcamp.assignment.domain.AssignmentVersion;
import com.jobbootcamp.assignment.domain.dto.AssignmentRequest;
import com.jobbootcamp.assignment.domain.dto.AssignmentSummary;
import com.jobbootcamp.assignment.domain.dto.AssignmentView;
import com.jobbootcamp.assignment.domain.dto.PageResponse;
import com.jobbootcamp.assignment.infrastructure.AssignmentMapper;
import com.jobbootcamp.assignment.infrastructure.AssignmentVersionMapper;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 正式作业草稿和不可变版本。发布只在 assignment 行锁内生成新的 assignment_version，
 * 历史版本和标准答案不随草稿修改；下架保留版本和后续历史作答引用。
 */
@Service
public class AdminAssignmentService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> KINDS = Set.of("CHAPTER_CHOICE", "COURSE_CODE");

    private final AssignmentMapper assignmentMapper;
    private final AssignmentVersionMapper versionMapper;

    public AdminAssignmentService(AssignmentMapper assignmentMapper,
                                  AssignmentVersionMapper versionMapper) {
        this.assignmentMapper = assignmentMapper;
        this.versionMapper = versionMapper;
    }

    public PageResponse<AssignmentSummary> list(Long courseId, String status, int page, int pageSize) {
        long pageNumber = Math.max(1, page);
        long size = Math.min(100, Math.max(1, pageSize));
        IPage<Assignment> result = assignmentMapper.selectPage(new Page<>(pageNumber, size),
                Wrappers.<Assignment>lambdaQuery()
                        .in(Assignment::getKind, KINDS)
                        .eq(courseId != null, Assignment::getCourseId, courseId)
                        .eq(status != null && !status.isBlank(), Assignment::getStatus, status)
                        .orderByDesc(Assignment::getUpdatedAt)
                        .orderByDesc(Assignment::getId));
        List<AssignmentSummary> items = result.getRecords().stream().map(this::summary).toList();
        return new PageResponse<>(items, result.getTotal(), pageNumber, size);
    }

    @Transactional
    public AssignmentView create(Long adminId, AssignmentRequest request) {
        String kind = normalizedKind(request);
        validateScope(request);
        String scopeKey = "CHAPTER_CHOICE".equals(kind)
                ? "CHAPTER:" + request.chapterId() : "COURSE:" + request.courseId();
        if (assignmentMapper.selectByScope(scopeKey) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "该章节/课程已存在正式作业");
        }
        Assignment assignment = new Assignment();
        assignment.setKind(kind);
        assignment.setCourseId(request.courseId());
        assignment.setChapterId(request.chapterId());
        assignment.setAuthorId(adminId);
        assignment.setStatus("DRAFT");
        assignment.setDraftJson(validateDraft(kind, request));
        assignment.setRevision(0L);
        assignment.setFormalScopeKey(scopeKey);
        assignment.setCreatedAt(now());
        assignment.setUpdatedAt(now());
        assignmentMapper.insert(assignment);
        return view(assignment);
    }

    @Transactional
    public AssignmentView update(Long id, AssignmentRequest request) {
        Assignment assignment = assignmentMapper.selectForUpdate(id);
        if (assignment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        requireFormal(assignment);
        requireRevision(assignment, request.revision());
        if (request.kind() != null && !normalizedKind(request).equals(assignment.getKind())
                || request.courseId() != null && !request.courseId().equals(assignment.getCourseId())
                || request.chapterId() != null && !request.chapterId().equals(assignment.getChapterId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已创建作业的课程/章节/类型不可更换");
        }
        assignment.setDraftJson(validateDraft(assignment.getKind(), request));
        assignment.setRevision(assignment.getRevision() + 1);
        assignment.setUpdatedAt(now());
        assignmentMapper.updateById(assignment);
        return view(assignment);
    }

    public AssignmentView get(Long id) {
        Assignment assignment = assignmentMapper.selectById(id);
        if (assignment == null || !KINDS.contains(assignment.getKind())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        requireFormal(assignment);
        return view(assignment);
    }

    @Transactional
    public AssignmentView publish(Long id, Long expectedRevision) {
        Assignment assignment = assignmentMapper.selectForUpdate(id);
        if (assignment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        requireFormal(assignment);
        requireRevision(assignment, expectedRevision);
        JsonNode draft = readJson(assignment.getDraftJson());
        int versionNo = versionMapper.maxVersionNo(id) + 1;
        AssignmentVersion version = new AssignmentVersion();
        version.setAssignmentId(id);
        version.setVersionNo(versionNo);
        version.setPublicBody(writeJson(draft.get("publicBody")));
        version.setAnswerBody(writeJson(draft.get("answerBody")));
        version.setPublishedAt(now());
        version.setPromptVersion("review-v1");
        versionMapper.insert(version);

        assignment.setCurrentVersionId(version.getId());
        assignment.setStatus("ONLINE");
        assignment.setRevision(assignment.getRevision() + 1);
        assignment.setUpdatedAt(now());
        assignmentMapper.updateById(assignment);
        return view(assignment);
    }

    @Transactional
    public AssignmentView offline(Long id, Long expectedRevision) {
        Assignment assignment = assignmentMapper.selectForUpdate(id);
        if (assignment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        requireFormal(assignment);
        requireRevision(assignment, expectedRevision);
        if (assignment.getCurrentVersionId() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "尚未发布的作业无需下架");
        }
        assignment.setStatus("OFFLINE");
        assignment.setRevision(assignment.getRevision() + 1);
        assignment.setUpdatedAt(now());
        assignmentMapper.updateById(assignment);
        return view(assignment);
    }

    @Transactional
    public void delete(Long id, Long expectedRevision) {
        Assignment assignment = assignmentMapper.selectForUpdate(id);
        if (assignment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        requireFormal(assignment);
        requireRevision(assignment, expectedRevision);
        if (assignment.getCurrentVersionId() != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "已发布作业不能删除，只能下架");
        }
        assignmentMapper.deleteById(id);
    }

    private AssignmentSummary summary(Assignment assignment) {
        Integer versionNo = null;
        if (assignment.getCurrentVersionId() != null) {
            AssignmentVersion version = versionMapper.selectById(assignment.getCurrentVersionId());
            versionNo = version == null ? null : version.getVersionNo();
        }
        return new AssignmentSummary(assignment.getId(), assignment.getKind(), assignment.getCourseId(),
                assignment.getChapterId(), assignment.getStatus(), versionNo,
                assignment.getRevision(), assignment.getUpdatedAt());
    }

    private AssignmentView view(Assignment assignment) {
        JsonNode draft = readJson(assignment.getDraftJson());
        Object currentPublic = null;
        Object currentAnswer = null;
        Integer versionNo = null;
        if (assignment.getCurrentVersionId() != null) {
            AssignmentVersion version = versionMapper.selectById(assignment.getCurrentVersionId());
            if (version != null) {
                versionNo = version.getVersionNo();
                currentPublic = readObject(version.getPublicBody());
                currentAnswer = readObject(version.getAnswerBody());
            }
        }
        return new AssignmentView(assignment.getId(), assignment.getKind(), assignment.getCourseId(),
                assignment.getChapterId(), assignment.getStatus(), versionNo,
                readObject(draft.get("publicBody")), readObject(draft.get("answerBody")),
                currentPublic, currentAnswer, assignment.getRevision(), assignment.getUpdatedAt());
    }

    private Object readObject(String value) {
        try {
            return JSON.readValue(value, Object.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "作业 JSON 损坏");
        }
    }

    private Object readObject(JsonNode value) {
        return JSON.convertValue(value, Object.class);
    }

    private String validateDraft(String kind, AssignmentRequest request) {
        if (request.publicBody() == null || request.answerBody() == null) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "题目和答案内容不能为空");
        }
        JsonNode publicBody = JSON.valueToTree(request.publicBody());
        JsonNode answerBody = JSON.valueToTree(request.answerBody());
        if (!publicBody.isObject() || !answerBody.isObject()) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "题目和答案必须是 JSON 对象");
        }
        if ("CHAPTER_CHOICE".equals(kind)) {
            validateChoice(publicBody, answerBody);
        } else {
            validateCode(publicBody, answerBody);
        }
        ObjectNode root = JSON.createObjectNode();
        root.set("publicBody", publicBody);
        root.set("answerBody", answerBody);
        return writeJson(root);
    }

    private void validateChoice(JsonNode publicBody, JsonNode answerBody) {
        JsonNode questions = requireArray(publicBody, "questions", 1, 50);
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

    private String normalizedKind(AssignmentRequest request) {
        if (request == null || request.kind() == null) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "kind 不能为空");
        }
        String kind = request.kind().trim().toUpperCase();
        if (!KINDS.contains(kind)) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "不支持的作业类型");
        }
        return kind;
    }

    private void validateScope(AssignmentRequest request) {
        if (request.courseId() == null || request.courseId() <= 0) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "courseId 不合法");
        }
        String kind = normalizedKind(request);
        if ("CHAPTER_CHOICE".equals(kind) && (request.chapterId() == null || request.chapterId() <= 0)) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "章节选择题必须指定 chapterId");
        }
        if ("COURSE_CODE".equals(kind) && request.chapterId() != null) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "课程代码题不能指定 chapterId");
        }
    }

    private void requireRevision(Assignment assignment, Long expected) {
        if (expected == null || !expected.equals(assignment.getRevision())) {
            throw new BusinessException(ErrorCode.CONFLICT, "作业草稿已在其他位置更新，请刷新后重试");
        }
    }

    private void requireFormal(Assignment assignment) {
        if (!KINDS.contains(assignment.getKind())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作业不存在");
        }
    }

    private JsonNode readJson(String value) {
        try {
            return value == null ? JSON.createObjectNode() : JSON.readTree(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "作业 JSON 损坏");
        }
    }

    private String writeJson(JsonNode value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "作业 JSON 序列化失败");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}