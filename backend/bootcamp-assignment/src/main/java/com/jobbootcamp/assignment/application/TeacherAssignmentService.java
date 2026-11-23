package com.jobbootcamp.assignment.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobbootcamp.assignment.domain.Assignment;
import com.jobbootcamp.assignment.domain.AssignmentVersion;
import com.jobbootcamp.assignment.domain.Submission;
import com.jobbootcamp.assignment.domain.dto.PageResponse;
import com.jobbootcamp.assignment.domain.dto.SubmissionView;
import com.jobbootcamp.assignment.domain.dto.TeacherAssignmentItem;
import com.jobbootcamp.assignment.domain.dto.TeacherSubmissionSummary;
import com.jobbootcamp.assignment.domain.dto.TeacherSubmissionView;
import com.jobbootcamp.assignment.infrastructure.AssignmentMapper;
import com.jobbootcamp.assignment.infrastructure.AssignmentVersionMapper;
import com.jobbootcamp.assignment.infrastructure.SubmissionMapper;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.ProfileBatchItem;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 教师只读当前负责课程的学生作答、成绩和 AI 反馈；每次读取都按课程服务当前分配验权。
 * 这里不提供写入接口，也不允许管理员默认查看全部学生作答。
 */
@Service
public class TeacherAssignmentService {

    private final AssignmentMapper assignmentMapper;
    private final AssignmentVersionMapper versionMapper;
    private final SubmissionMapper submissionMapper;
    private final SubmissionAssembler submissionAssembler;
    private final CourseScopeClient courseScopeClient;
    private final ProfileClient profileClient;

    public TeacherAssignmentService(AssignmentMapper assignmentMapper,
                                    AssignmentVersionMapper versionMapper,
                                    SubmissionMapper submissionMapper,
                                    SubmissionAssembler submissionAssembler,
                                    CourseScopeClient courseScopeClient,
                                    ProfileClient profileClient) {
        this.assignmentMapper = assignmentMapper;
        this.versionMapper = versionMapper;
        this.submissionMapper = submissionMapper;
        this.submissionAssembler = submissionAssembler;
        this.courseScopeClient = courseScopeClient;
        this.profileClient = profileClient;
    }

    public List<TeacherAssignmentItem> assignments(Long teacherId, Long courseId) {
        requireScope(teacherId, courseId);
        return courseAssignments(courseId).stream()
                .map(assignment -> new TeacherAssignmentItem(assignment.getId(), assignment.getKind(),
                        assignment.getChapterId(), assignment.getStatus(), versionNo(assignment)))
                .toList();
    }

    public PageResponse<TeacherSubmissionSummary> submissions(Long teacherId, Long courseId,
                                                              Long assignmentId, int page, int pageSize) {
        requireScope(teacherId, courseId);
        List<Assignment> courseAssignments = courseAssignments(courseId);
        if (assignmentId != null && courseAssignments.stream()
                .noneMatch(assignment -> assignment.getId().equals(assignmentId))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该课程下没有此作业");
        }
        long pageNumber = Math.max(1, page);
        long size = Math.min(100, Math.max(1, pageSize));
        List<Long> assignmentIds = courseAssignments.stream().map(Assignment::getId).toList();
        if (assignmentIds.isEmpty()) {
            return new PageResponse<>(List.of(), 0, pageNumber, size);
        }
        IPage<Submission> result = submissionMapper.selectPage(new Page<>(pageNumber, size),
                Wrappers.<Submission>lambdaQuery()
                        .in(Submission::getAssignmentId, assignmentIds)
                        .eq(assignmentId != null, Submission::getAssignmentId, assignmentId)
                        .orderByDesc(Submission::getSubmittedAt)
                        .orderByDesc(Submission::getId));
        Map<Long, Assignment> assignmentsById = courseAssignments.stream()
                .collect(Collectors.toMap(Assignment::getId, Function.identity()));
        Map<Long, String> nicknames = nicknames(result.getRecords().stream()
                .map(Submission::getUserId).distinct().toList());
        List<TeacherSubmissionSummary> items = result.getRecords().stream().map(submission -> {
            Assignment assignment = assignmentsById.get(submission.getAssignmentId());
            AssignmentVersion version = versionMapper.selectById(submission.getVersionId());
            return new TeacherSubmissionSummary(submission.getId(), submission.getAssignmentId(),
                    assignment == null ? null : assignment.getKind(),
                    assignment == null ? null : assignment.getChapterId(),
                    submission.getUserId(), nicknames.get(submission.getUserId()),
                    version == null ? null : version.getVersionNo(),
                    submission.getScore(), submission.getMaxScore(), submission.getReviewState(),
                    submission.getSubmittedAt());
        }).toList();
        return new PageResponse<>(items, result.getTotal(), pageNumber, size);
    }

    public TeacherSubmissionView submission(Long teacherId, Long submissionId) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提交记录不存在");
        }
        Assignment assignment = assignmentMapper.selectById(submission.getAssignmentId());
        if (assignment == null) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "作业不存在");
        }
        requireScope(teacherId, assignment.getCourseId());
        SubmissionView view = submissionAssembler.view(submission);
        Map<Long, String> nicknames = nicknames(List.of(submission.getUserId()));
        return new TeacherSubmissionView(submission.getId(), submission.getAssignmentId(),
                assignment.getKind(), submission.getUserId(), nicknames.get(submission.getUserId()),
                view, submissionAssembler.review(submissionId));
    }

    private List<Assignment> courseAssignments(Long courseId) {
        return assignmentMapper.selectList(Wrappers.<Assignment>lambdaQuery()
                .eq(Assignment::getCourseId, courseId)
                .orderByAsc(Assignment::getKind)
                .orderByAsc(Assignment::getChapterId));
    }

    private Integer versionNo(Assignment assignment) {
        if (assignment.getCurrentVersionId() == null) {
            return null;
        }
        AssignmentVersion version = versionMapper.selectById(assignment.getCurrentVersionId());
        return version == null ? null : version.getVersionNo();
    }

    private Map<Long, String> nicknames(List<Long> userIds) {
        Map<Long, String> result = new HashMap<>();
        try {
            for (ProfileBatchItem item : profileClient.fetch(userIds)) {
                result.put(Long.valueOf(item.id()), item.nickname());
            }
        } catch (BusinessException ignored) {
            // 昵称只用于展示；用户服务短暂不可用时不阻断教师查看作答事实。
        }
        return result;
    }

    private void requireScope(Long teacherId, Long courseId) {
        if (!courseScopeClient.allowed(teacherId, courseId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该课程的学生作答");
        }
    }
}