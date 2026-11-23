package com.jobbootcamp.assignment.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobbootcamp.assignment.domain.AiFeedback;
import com.jobbootcamp.assignment.domain.AiTask;
import com.jobbootcamp.assignment.domain.Assignment;
import com.jobbootcamp.assignment.domain.AssignmentVersion;
import com.jobbootcamp.assignment.domain.Submission;
import com.jobbootcamp.assignment.domain.dto.ReviewView;
import com.jobbootcamp.assignment.domain.dto.SubmissionView;
import com.jobbootcamp.assignment.infrastructure.AiFeedbackMapper;
import com.jobbootcamp.assignment.infrastructure.AiTaskMapper;
import com.jobbootcamp.assignment.infrastructure.AssignmentMapper;
import com.jobbootcamp.assignment.infrastructure.AssignmentVersionMapper;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 提交详情只从固定版本读取题面、标准答案和解析；学生和教师共用同一业务投影。
 */
@Component
public class SubmissionAssembler {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AssignmentMapper assignmentMapper;
    private final AssignmentVersionMapper versionMapper;
    private final AiTaskMapper taskMapper;
    private final AiFeedbackMapper feedbackMapper;

    public SubmissionAssembler(AssignmentMapper assignmentMapper,
                               AssignmentVersionMapper versionMapper,
                               AiTaskMapper taskMapper,
                               AiFeedbackMapper feedbackMapper) {
        this.assignmentMapper = assignmentMapper;
        this.versionMapper = versionMapper;
        this.taskMapper = taskMapper;
        this.feedbackMapper = feedbackMapper;
    }

    public SubmissionView view(Submission submission) {
        AssignmentVersion version = versionMapper.selectById(submission.getVersionId());
        if (version == null) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "作业版本不存在");
        }
        Assignment assignment = assignmentMapper.selectById(submission.getAssignmentId());
        if (assignment == null) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "作业不存在");
        }
        JsonNode answerBody = readJson(version.getAnswerBody());
        Object referenceBody = null;
        Object correctAnswers = null;
        Object analysis = null;
        if ("COURSE_CODE".equals(assignment.getKind())) {
            referenceBody = JSON.convertValue(answerBody.path("referenceBySlot"), Object.class);
        } else {
            correctAnswers = JSON.convertValue(answerBody.path("answers"), Object.class);
            JsonNode analysisNode = answerBody.path("analysis");
            analysis = analysisNode.isObject() ? JSON.convertValue(analysisNode, Object.class) : null;
        }
        return new SubmissionView(submission.getId(), submission.getAssignmentId(), submission.getVersionId(),
                version.getVersionNo(), readObject(version.getPublicBody()),
                readObject(submission.getAnswersJson()), submission.getScore(), submission.getMaxScore(),
                submission.getReviewState(), submission.getSubmittedAt(), referenceBody,
                assignment.getKind(), correctAnswers, analysis);
    }

    /**
     * 代码题点评状态；选择题没有任务，返回 NOT_APPLICABLE。
     */
    public ReviewView review(Long submissionId) {
        AiTask task = taskMapper.selectBySubmission(submissionId);
        if (task == null) {
            return new ReviewView("NOT_APPLICABLE", null, null, null);
        }
        Object feedback = null;
        AiFeedback value = feedbackMapper.selectById(task.getId());
        if (value != null) {
            feedback = feedbackObject(value);
        }
        boolean mock = value != null && Boolean.TRUE.equals(value.getIsMock());
        return new ReviewView(task.getState(), feedback, task.getSafeErrorCode(), mock);
    }

    private Object feedbackObject(AiFeedback feedback) {
        ObjectNode root = JSON.createObjectNode();
        root.put("verdict", feedback.getVerdict());
        root.put("summary", feedback.getSummary());
        root.set("issues", readJson(feedback.getIssues()));
        root.set("suggestions", readJson(feedback.getSuggestions()));
        root.set("limitations", readJson(feedback.getLimitations()));
        root.put("isMock", Boolean.TRUE.equals(feedback.getIsMock()));
        // Boot 4 的 HTTP 层使用 Jackson 3；Jackson 2 的 ObjectNode 会被当成普通 POJO 序列化。
        return JSON.convertValue(root, Object.class);
    }

    private Object readObject(String json) {
        try {
            return json == null || json.isBlank() ? null : JSON.readValue(json, Object.class);
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
}