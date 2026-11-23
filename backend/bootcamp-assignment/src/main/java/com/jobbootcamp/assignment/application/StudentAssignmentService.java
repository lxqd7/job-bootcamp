package com.jobbootcamp.assignment.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbootcamp.assignment.domain.Attempt;
import com.jobbootcamp.assignment.domain.AiTask;
import com.jobbootcamp.assignment.domain.Assignment;
import com.jobbootcamp.assignment.domain.AssignmentVersion;
import com.jobbootcamp.assignment.domain.AiUserSlot;
import com.jobbootcamp.assignment.domain.Submission;
import com.jobbootcamp.assignment.domain.dto.AssignmentProgressView;
import com.jobbootcamp.assignment.domain.dto.AttemptView;
import com.jobbootcamp.assignment.domain.dto.PageResponse;
import com.jobbootcamp.assignment.domain.dto.ReviewView;
import com.jobbootcamp.assignment.domain.dto.StudentAssignmentView;
import com.jobbootcamp.assignment.domain.dto.SubmissionSummary;
import com.jobbootcamp.assignment.domain.dto.SubmissionView;
import com.jobbootcamp.assignment.infrastructure.AiTaskMapper;
import com.jobbootcamp.assignment.infrastructure.AiUserSlotMapper;
import com.jobbootcamp.assignment.infrastructure.AssignmentMapper;
import com.jobbootcamp.assignment.infrastructure.AssignmentVersionMapper;
import com.jobbootcamp.assignment.infrastructure.AttemptMapper;
import com.jobbootcamp.assignment.infrastructure.SubmissionMapper;
import com.jobbootcamp.assignment.messaging.AiTaskQueuedEvent;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 学生作答：开始 attempt 时固定版本；draft 只保存输入；提交时只用 attempt.versionId 判分。
 * 代码题在同一个作业库事务中创建 submission、ai_task 和 user slot，保存草稿不占 AI 名额。
 */
@Service
public class StudentAssignmentService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> FORMAL_KINDS = Set.of("CHAPTER_CHOICE", "COURSE_CODE");

    private final AssignmentMapper assignmentMapper;
    private final AssignmentVersionMapper versionMapper;
    private final AttemptMapper attemptMapper;
    private final SubmissionMapper submissionMapper;
    private final AiTaskMapper taskMapper;
    private final AiUserSlotMapper slotMapper;
    private final SubmissionAssembler submissionAssembler;
    private final LearningAccessClient accessClient;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public StudentAssignmentService(AssignmentMapper assignmentMapper,
                                    AssignmentVersionMapper versionMapper,
                                    AttemptMapper attemptMapper,
                                    SubmissionMapper submissionMapper,
                                    AiTaskMapper taskMapper,
                                    AiUserSlotMapper slotMapper,
                                    SubmissionAssembler submissionAssembler,
                                    LearningAccessClient accessClient,
                                    ApplicationEventPublisher eventPublisher,
                                    PlatformTransactionManager transactionManager) {
        this.assignmentMapper = assignmentMapper;
        this.versionMapper = versionMapper;
        this.attemptMapper = attemptMapper;
        this.submissionMapper = submissionMapper;
        this.taskMapper = taskMapper;
        this.slotMapper = slotMapper;
        this.submissionAssembler = submissionAssembler;
        this.accessClient = accessClient;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public List<StudentAssignmentView> courseAssignments(Long userId, Long courseId) {
        requireAccess(userId, courseId);
        return assignmentMapper.selectList(Wrappers.<Assignment>lambdaQuery()
                        .eq(Assignment::getCourseId, courseId)
                        .in(Assignment::getKind, FORMAL_KINDS)
                        .orderByAsc(Assignment::getKind)
                        .orderByAsc(Assignment::getChapterId))
                .stream().map(assignment -> studentView(userId, assignment)).toList();
    }

    public StudentAssignmentView getAssignment(Long userId, Long assignmentId) {
        Assignment assignment = requireAssignment(assignmentId);
        requireFormal(assignment);
        requireAccess(userId, assignment.getCourseId());
        return studentView(userId, assignment);
    }

    /**
     * 当前版本最近/最高成绩只统计当前版本；提交历史仍保留所有版本。
     */
    public AssignmentProgressView progress(Long userId, Long assignmentId) {
        Assignment assignment = requireAssignment(assignmentId);
        requireFormal(assignment);
        requireAccess(userId, assignment.getCourseId());
        if (assignment.getCurrentVersionId() == null) {
            return new AssignmentProgressView(assignmentId, assignment.getStatus(),
                    null, null, false, null, null, null, null);
        }
        AssignmentVersion version = requireVersion(assignment.getCurrentVersionId());
        Submission recent = submissionMapper.selectRecentForVersion(userId, assignmentId, version.getId());
        Submission highest = submissionMapper.selectHighestForVersion(userId, assignmentId, version.getId());
        Attempt open = attemptMapper.selectLatestOpen(userId, assignmentId, version.getId());
        return new AssignmentProgressView(assignmentId, assignment.getStatus(), version.getId(),
                version.getVersionNo(), recent != null,
                open == null ? null : open.getId(), open == null ? null : open.getDraftRevision(),
                recent == null ? null : summaryOf(recent), highest == null ? null : summaryOf(highest));
    }

    public AttemptView startAttempt(Long userId, Long assignmentId) {
        Assignment assignment = requireAssignment(assignmentId);
        requireFormal(assignment);
        requireAccess(userId, assignment.getCourseId());
        if (!"ONLINE".equals(assignment.getStatus()) || assignment.getCurrentVersionId() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "作业尚未发布或已下架");
        }
        AssignmentVersion version = versionMapper.selectById(assignment.getCurrentVersionId());
        if (version == null) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "作业当前版本不存在");
        }
        // 同一版本已有未提交作答时继续使用，跨设备打开页面不会覆盖原草稿。
        Attempt existing = attemptMapper.selectLatestOpen(userId, assignmentId, version.getId());
        if (existing != null) {
            return attemptView(existing, version, readObjectOrNull(existing.getDraftJson()));
        }
        LocalDateTime now = now();
        Attempt attempt = new Attempt();
        attempt.setUserId(userId);
        attempt.setAssignmentId(assignmentId);
        attempt.setVersionId(version.getId());
        attempt.setStartedAt(now);
        attempt.setStatus("OPEN");
        attempt.setDraftRevision(0L);
        attempt.setCreatedAt(now);
        attempt.setUpdatedAt(now);
        attemptMapper.insert(attempt);
        return attemptView(attempt, version, null);
    }

    public AttemptView getAttempt(Long userId, Long attemptId) {
        Attempt attempt = requireOwnedAttempt(userId, attemptId);
        Assignment assignment = requireAssignment(attempt.getAssignmentId());
        requireAccess(userId, assignment.getCourseId());
        AssignmentVersion version = requireVersion(attempt.getVersionId());
        return attemptView(attempt, version, readObjectOrNull(attempt.getDraftJson()));
    }

    public AttemptView saveDraft(Long userId, Long attemptId, Object answers, Long expectedRevision) {
        Attempt attempt = requireOwnedAttempt(userId, attemptId);
        Assignment assignment = requireAssignment(attempt.getAssignmentId());
        requireAccess(userId, assignment.getCourseId());
        if (!"OPEN".equals(attempt.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已提交的 attempt 不能继续保存草稿");
        }
        if (expectedRevision == null || !expectedRevision.equals(attempt.getDraftRevision())) {
            throw new BusinessException(ErrorCode.CONFLICT, "草稿已在其他设备更新，请刷新后重试");
        }
        if (answers == null || !JSON.valueToTree(answers).isObject()) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "草稿答案必须是 JSON 对象");
        }
        attempt.setDraftJson(writeJson(answers));
        attempt.setDraftRevision(attempt.getDraftRevision() + 1);
        attempt.setUpdatedAt(now());
        attemptMapper.updateById(attempt);
        AssignmentVersion version = requireVersion(attempt.getVersionId());
        return attemptView(attempt, version, readObjectOrNull(attempt.getDraftJson()));
    }

    public SubmissionView submit(Long userId, Long attemptId, String requestKey, Object answers) {
        if (requestKey == null || requestKey.isBlank() || requestKey.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "提交必须携带有效 Idempotency-Key");
        }
        Attempt attempt = requireOwnedAttempt(userId, attemptId);
        Assignment assignment = requireAssignment(attempt.getAssignmentId());
        requireAccess(userId, assignment.getCourseId());
        String requestHash = requestHash(attemptId, answers);
        Submission existing = submissionMapper.selectByRequest(userId, attemptId, requestKey);
        if (existing != null) {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new BusinessException(ErrorCode.CONFLICT, "该幂等键已用于不同的提交内容");
            }
            return submissionAssembler.view(existing);
        }
        if (!"OPEN".equals(attempt.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该 attempt 已提交，请重新开始作答");
        }
        if (!"ONLINE".equals(assignment.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "作业已下架，不能提交");
        }
        JsonNode answerNode = JSON.valueToTree(answers == null ? Map.of() : answers);
        AssignmentVersion version = requireVersion(attempt.getVersionId());
        boolean code = "COURSE_CODE".equals(assignment.getKind());
        Grade grade = code ? validateCodeAnswers(version, answerNode) : gradeChoice(version, answerNode);
        try {
            return transactionTemplate.execute(status -> submitInTransaction(
                    attempt, assignment, version, requestKey, requestHash, answerNode, code, grade));
        } catch (DuplicateKeyException duplicate) {
            Submission raced = submissionMapper.selectByRequest(userId, attemptId, requestKey);
            if (raced != null && raced.getRequestHash().equals(requestHash)) {
                return submissionAssembler.view(raced);
            }
            throw new BusinessException(ErrorCode.CONFLICT, "该 attempt 已提交");
        }
    }

    public PageResponse<SubmissionSummary> listSubmissions(Long userId, Long assignmentId, int page, int pageSize) {
        Assignment assignment = requireAssignment(assignmentId);
        requireAccess(userId, assignment.getCourseId());
        long pageNumber = Math.max(1, page);
        long size = Math.min(100, Math.max(1, pageSize));
        IPage<Submission> result = submissionMapper.selectPage(new Page<>(pageNumber, size),
                Wrappers.<Submission>lambdaQuery()
                        .eq(Submission::getUserId, userId)
                        .eq(Submission::getAssignmentId, assignmentId)
                        .orderByDesc(Submission::getSubmittedAt)
                        .orderByDesc(Submission::getId));
        List<SubmissionSummary> items = result.getRecords().stream().map(submission -> {
            AssignmentVersion version = versionMapper.selectById(submission.getVersionId());
            return new SubmissionSummary(submission.getId(), version == null ? null : version.getVersionNo(),
                    submission.getScore(), submission.getMaxScore(), submission.getReviewState(),
                    submission.getSubmittedAt());
        }).toList();
        return new PageResponse<>(items, result.getTotal(), pageNumber, size);
    }

    public SubmissionView getSubmission(Long userId, Long submissionId) {
        Submission submission = requireOwnedSubmission(userId, submissionId);
        Assignment assignment = requireAssignment(submission.getAssignmentId());
        requireAccess(userId, assignment.getCourseId());
        return submissionAssembler.view(submission);
    }

    public ReviewView getReview(Long userId, Long submissionId) {
        Submission submission = requireOwnedSubmission(userId, submissionId);
        Assignment assignment = requireAssignment(submission.getAssignmentId());
        requireAccess(userId, assignment.getCourseId());
        return submissionAssembler.review(submissionId);
    }

    private SubmissionView submitInTransaction(Attempt attempt, Assignment assignment,
                                               AssignmentVersion version, String requestKey,
                                               String requestHash, JsonNode answers,
                                               boolean code, Grade grade) {
        Attempt locked = attemptMapper.selectForUpdate(attempt.getId());
        if (locked == null || !"OPEN".equals(locked.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该 attempt 已提交");
        }
        Submission existing = submissionMapper.selectByAttempt(locked.getId());
        if (existing != null) {
            if (existing.getRequestHash().equals(requestHash)) {
                return submissionAssembler.view(existing);
            }
            throw new BusinessException(ErrorCode.CONFLICT, "该 attempt 已提交");
        }
        LocalDateTime now = now();
        Submission submission = new Submission();
        submission.setUserId(attempt.getUserId());
        submission.setAssignmentId(assignment.getId());
        submission.setVersionId(version.getId());
        submission.setAttemptId(attempt.getId());
        submission.setAnswersJson(writeJson(answers));
        submission.setScore(code ? null : grade.score());
        submission.setMaxScore(code ? null : grade.maxScore());
        submission.setReviewState(code ? "PENDING_REVIEW" : "GRADED");
        submission.setRequestKey(requestKey);
        submission.setRequestHash(requestHash);
        submission.setSubmittedAt(now);
        submission.setCreatedAt(now);
        submissionMapper.insert(submission);

        if (code) {
            slotMapper.insertIfAbsent(attempt.getUserId(), now);
            AiUserSlot slot = slotMapper.selectForUpdate(attempt.getUserId());
            if (slot != null && slot.getActiveTaskId() != null) {
                throw new BusinessException(ErrorCode.AI_REVIEW_BUSY, "已有未完成的 AI 点评任务");
            }
            AiTask task = new AiTask();
            task.setSubmissionId(submission.getId());
            task.setUserId(attempt.getUserId());
            task.setAssignmentId(assignment.getId());
            task.setState("QUEUED");
            task.setRoundNo(0);
            task.setCallCount(0);
            task.setRepairUsed(false);
            task.setDeadlineAt(now.plusMinutes(10));
            task.setNextRetryAt(now);
            task.setPromptVersion("review-v1");
            task.setCreatedAt(now);
            task.setUpdatedAt(now);
            taskMapper.insert(task);
            slotMapper.occupy(attempt.getUserId(), task.getId(), now);
            eventPublisher.publishEvent(new AiTaskQueuedEvent(
                    task.getId(), submission.getId(), attempt.getUserId()));
        }

        locked.setStatus("SUBMITTED");
        locked.setUpdatedAt(now);
        attemptMapper.updateById(locked);
        return submissionAssembler.view(submission);
    }

    private SubmissionSummary summaryOf(Submission submission) {
        AssignmentVersion version = versionMapper.selectById(submission.getVersionId());
        return new SubmissionSummary(submission.getId(), version == null ? null : version.getVersionNo(),
                submission.getScore(), submission.getMaxScore(), submission.getReviewState(),
                submission.getSubmittedAt());
    }

    private Grade validateCodeAnswers(AssignmentVersion version, JsonNode answers) {
        JsonNode publicBody = readJson(version.getPublicBody());
        Set<String> slots = new HashSet<>();
        for (JsonNode segment : publicBody.path("segments")) {
            if (segment.has("slotId")) {
                slots.add(segment.path("slotId").asText());
            }
        }
        if (!answers.isObject()) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "代码答案必须是 slot 对象");
        }
        int total = 0;
        boolean hasCode = false;
        Iterator<String> names = answers.fieldNames();
        while (names.hasNext()) {
            String slot = names.next();
            if (!slots.contains(slot) || !answers.get(slot).isTextual()) {
                throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "提交包含未知 slot 或非文本代码");
            }
            String code = answers.get(slot).asText();
            total += code.length();
            hasCode = hasCode || !code.isBlank();
        }
        if (!hasCode) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "代码不能全部为空");
        }
        if (total > 30000) {
            throw new BusinessException(ErrorCode.INPUT_TOO_LARGE, "学生代码总长超限");
        }
        return new Grade(null, null);
    }

    private Grade gradeChoice(AssignmentVersion version, JsonNode answers) {
        JsonNode publicBody = readJson(version.getPublicBody());
        JsonNode answerBody = readJson(version.getAnswerBody());
        JsonNode questions = publicBody.path("questions");
        JsonNode correct = answerBody.path("answers");
        if (!answers.isObject() || !questions.isArray()) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "选择答案格式不合法");
        }
        Set<String> knownQuestions = new HashSet<>();
        for (JsonNode question : questions) {
            knownQuestions.add(question.path("id").asText());
        }
        Iterator<String> answerKeys = answers.fieldNames();
        while (answerKeys.hasNext()) {
            if (!knownQuestions.contains(answerKeys.next())) {
                throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "答案包含未知题目");
            }
        }
        int score = 0;
        int max = 0;
        for (JsonNode question : questions) {
            String id = question.path("id").asText();
            int points = question.path("score").asInt();
            max += points;
            JsonNode submitted = answers.get(id);
            if (submitted == null || !submitted.isArray() || submitted.isEmpty()) {
                continue;
            }
            Set<String> optionIds = new HashSet<>();
            for (JsonNode option : question.path("options")) {
                optionIds.add(option.path("id").asText());
            }
            Set<String> chosen = new HashSet<>();
            for (JsonNode value : submitted) {
                if (!value.isTextual() || !optionIds.contains(value.asText()) || !chosen.add(value.asText())) {
                    throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "答案选项非法");
                }
            }
            if ("SINGLE".equals(question.path("type").asText()) && chosen.size() != 1) {
                throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "单选题只能选择一个选项");
            }
            JsonNode correctNode = correct.get(id);
            Set<String> expected = new HashSet<>();
            if (correctNode != null && correctNode.isArray()) {
                for (JsonNode value : correctNode) {
                    expected.add(value.asText());
                }
            }
            if (chosen.equals(expected)) {
                score += points;
            }
        }
        return new Grade(score, max);
    }

    private StudentAssignmentView studentView(Long userId, Assignment assignment) {
        Integer versionNo = null;
        Object publicBody = null;
        boolean submitted = false;
        Long openAttemptId = null;
        if (assignment.getCurrentVersionId() != null) {
            AssignmentVersion version = versionMapper.selectById(assignment.getCurrentVersionId());
            if (version != null) {
                versionNo = version.getVersionNo();
                if (!"OFFLINE".equals(assignment.getStatus())) {
                    publicBody = readObject(version.getPublicBody());
                }
                submitted = submissionMapper.selectRecentForVersion(
                        userId, assignment.getId(), version.getId()) != null;
                Attempt open = attemptMapper.selectLatestOpen(userId, assignment.getId(), version.getId());
                openAttemptId = open == null ? null : open.getId();
            }
        }
        return new StudentAssignmentView(assignment.getId(), assignment.getKind(), assignment.getCourseId(),
                assignment.getChapterId(), assignment.getStatus(), assignment.getCurrentVersionId(),
                versionNo, publicBody, submitted, openAttemptId);
    }

    private AttemptView attemptView(Attempt attempt, AssignmentVersion version, Object draft) {
        return new AttemptView(attempt.getId(), attempt.getAssignmentId(), attempt.getVersionId(),
                attempt.getStatus(), readObject(version.getPublicBody()), draft,
                attempt.getDraftRevision(), attempt.getStartedAt());
    }

    private Object readObjectOrNull(String json) {
        return json == null || json.isBlank() ? null : readObject(json);
    }

    private Object readObject(String json) {
        try {
            return JSON.readValue(json, Object.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "作业 JSON 损坏");
        }
    }

    private JsonNode readJson(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "作业 JSON 损坏");
        }
    }

    private String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "作业 JSON 序列化失败");
        }
    }

    private String requestHash(Long attemptId, Object answers) {
        try {
            byte[] bytes = JSON.writeValueAsString(new Object[]{attemptId, answers}).getBytes(StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "提交哈希失败");
        }
    }

    private void requireAccess(Long userId, Long courseId) {
        if (!accessClient.allowed(userId, courseId)) {
            throw new BusinessException(ErrorCode.NO_COURSE_ACCESS, "没有该课程的有效学习权益");
        }
    }

    private Assignment requireAssignment(Long id) {
        Assignment assignment = assignmentMapper.selectById(id);
        if (assignment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        return assignment;
    }

    private void requireFormal(Assignment assignment) {
        if (!FORMAL_KINDS.contains(assignment.getKind())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作业不存在");
        }
    }

    private AssignmentVersion requireVersion(Long id) {
        AssignmentVersion version = versionMapper.selectById(id);
        if (version == null) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "作业版本不存在");
        }
        return version;
    }

    private Attempt requireOwnedAttempt(Long userId, Long attemptId) {
        Attempt attempt = attemptMapper.selectById(attemptId);
        if (attempt == null || !attempt.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作答不存在");
        }
        return attempt;
    }

    private Submission requireOwnedSubmission(Long userId, Long submissionId) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null || !submission.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提交记录不存在");
        }
        return submission;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private record Grade(Integer score, Integer maxScore) {
    }
}