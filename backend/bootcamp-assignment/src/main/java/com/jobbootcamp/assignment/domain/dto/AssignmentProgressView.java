package com.jobbootcamp.assignment.domain.dto;

/**
 * 当前作业页面只统计当前版本的最近/最高成绩；旧版本成绩仍在提交历史中。
 */
public record AssignmentProgressView(
        Long assignmentId,
        String status,
        Long currentVersionId,
        Integer currentVersionNo,
        boolean currentVersionSubmitted,
        Long openAttemptId,
        Long openAttemptRevision,
        SubmissionSummary recent,
        SubmissionSummary highest
) {
}