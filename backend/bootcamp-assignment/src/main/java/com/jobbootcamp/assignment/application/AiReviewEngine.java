package com.jobbootcamp.assignment.application;

import com.jobbootcamp.assignment.domain.AiTask;
import com.jobbootcamp.assignment.domain.AssignmentVersion;
import com.jobbootcamp.assignment.domain.Submission;

import java.util.List;

/**
 * 点评引擎只负责根据固定版本生成结构化结果；领取、租约、超时和落库由 worker 统一处理。
 */
public interface AiReviewEngine {

    ReviewFeedback generate(AiTask task, Submission submission, AssignmentVersion version) throws Exception;

    record ReviewFeedback(
            String verdict,
            String summary,
            List<Object> issues,
            List<String> suggestions,
            List<String> limitations,
            boolean mock
    ) {
    }
}