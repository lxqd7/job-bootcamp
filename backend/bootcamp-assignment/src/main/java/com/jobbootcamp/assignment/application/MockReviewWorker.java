package com.jobbootcamp.assignment.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobbootcamp.assignment.domain.AiFeedback;
import com.jobbootcamp.assignment.domain.AiTask;
import com.jobbootcamp.assignment.domain.AssignmentVersion;
import com.jobbootcamp.assignment.domain.Submission;
import com.jobbootcamp.assignment.infrastructure.AiFeedbackMapper;
import com.jobbootcamp.assignment.infrastructure.AiTaskMapper;
import com.jobbootcamp.assignment.infrastructure.AiUserSlotMapper;
import com.jobbootcamp.assignment.infrastructure.AssignmentVersionMapper;
import com.jobbootcamp.assignment.infrastructure.SubmissionMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * S10-b 的显式 mock 点评处理器：复用正式 ai_task/ai_user_slot/ai_feedback 表，
 * 只模拟异步结果，不执行学生代码。S11 用真实 worker/租约替换本处理器，不改变提交事实表。
 */
@Component
@ConditionalOnProperty(name = "app.ai.mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockReviewWorker {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AiTaskMapper taskMapper;
    private final AiFeedbackMapper feedbackMapper;
    private final SubmissionMapper submissionMapper;
    private final AssignmentVersionMapper versionMapper;
    private final AiUserSlotMapper slotMapper;
    private final TransactionTemplate transactionTemplate;

    public MockReviewWorker(AiTaskMapper taskMapper,
                            AiFeedbackMapper feedbackMapper,
                            SubmissionMapper submissionMapper,
                            AssignmentVersionMapper versionMapper,
                            AiUserSlotMapper slotMapper,
                            PlatformTransactionManager transactionManager) {
        this.taskMapper = taskMapper;
        this.feedbackMapper = feedbackMapper;
        this.submissionMapper = submissionMapper;
        this.versionMapper = versionMapper;
        this.slotMapper = slotMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.ai.mock-poll-ms:800}")
    public void poll() {
        for (AiTask queued : taskMapper.selectQueued(20)) {
            try {
                transactionTemplate.executeWithoutResult(status -> process(queued.getId()));
            } catch (RuntimeException ignored) {
                // 单条 mock 任务失败不阻塞其他任务；S11 会以正式失败状态和恢复机制替代。
            }
        }
    }

    private void process(Long taskId) {
        AiTask task = taskMapper.selectForUpdate(taskId);
        if (task == null || !"QUEUED".equals(task.getState())) {
            return;
        }
        LocalDateTime now = now();
        task.setState("RUNNING");
        task.setRoundNo(1);
        task.setCallCount(1);
        task.setUpdatedAt(now);
        taskMapper.updateById(task);

        Submission submission = submissionMapper.selectById(task.getSubmissionId());
        if (submission == null) {
            fail(task, "SUBMISSION_MISSING", now);
            return;
        }
        AssignmentVersion version = versionMapper.selectById(submission.getVersionId());
        if (version == null) {
            fail(task, "VERSION_MISSING", now);
            return;
        }
        Feedback feedback = mockFeedback(version, submission);
        AiFeedback entity = new AiFeedback();
        entity.setTaskId(task.getId());
        entity.setVerdict(feedback.verdict());
        entity.setSummary(feedback.summary());
        entity.setIssues(write(feedback.issues()));
        entity.setSuggestions(write(feedback.suggestions()));
        entity.setLimitations(write(feedback.limitations()));
        entity.setIsMock(true);
        entity.setCreatedAt(now);
        feedbackMapper.insert(entity);

        submission.setReviewState("GRADED");
        submissionMapper.updateById(submission);
        task.setState("SUCCEEDED");
        task.setUpdatedAt(now);
        taskMapper.updateById(task);
        slotMapper.release(task.getUserId(), task.getId(), now);
    }

    private Feedback mockFeedback(AssignmentVersion version, Submission submission) {
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
                ObjectNode issue = JSON.createObjectNode();
                issue.put("slotId", slot);
                issue.put("evidence", "该 slot 为空");
                issue.put("reason", "缺少补全代码");
                issue.put("suggestion", "补充该 slot 的实现并重新提交");
                issues.add(issue);
            }
        }
        return new Feedback(verdict, summary, issues, suggestions, limitations);
    }

    private void fail(AiTask task, String code, LocalDateTime now) {
        task.setState("FAILED");
        task.setSafeErrorCode(code);
        task.setUpdatedAt(now);
        taskMapper.updateById(task);
        Submission submission = submissionMapper.selectById(task.getSubmissionId());
        if (submission != null) {
            submission.setReviewState("FAILED");
            submissionMapper.updateById(submission);
        }
        slotMapper.release(task.getUserId(), task.getId(), now);
    }

    private JsonNode read(String value) {
        try {
            return JSON.readTree(value);
        } catch (Exception e) {
            return JSON.createObjectNode();
        }
    }

    private String write(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("mock feedback serialization failed", e);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private record Feedback(String verdict, String summary, Object issues,
                            Object suggestions, Object limitations) {
    }
}