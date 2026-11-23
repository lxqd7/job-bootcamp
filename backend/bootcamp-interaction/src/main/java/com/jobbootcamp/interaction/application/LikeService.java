package com.jobbootcamp.interaction.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.interaction.domain.Answer;
import com.jobbootcamp.interaction.domain.Comment;
import com.jobbootcamp.interaction.domain.LikeRecord;
import com.jobbootcamp.interaction.domain.Question;
import com.jobbootcamp.interaction.domain.dto.LikeResult;
import com.jobbootcamp.interaction.infrastructure.AnswerMapper;
import com.jobbootcamp.interaction.infrastructure.CommentMapper;
import com.jobbootcamp.interaction.infrastructure.LikeRecordMapper;
import com.jobbootcamp.interaction.infrastructure.QuestionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 点赞只作用于回答/评论；每人每条最多一个有效记录，取消幂等，不能自赞。
 * 计数与记录在同一事务内按目标行锁更新，不奖励积分。
 */
@Service
public class LikeService {

    private final LikeRecordMapper likeMapper;
    private final QuestionMapper questionMapper;
    private final AnswerMapper answerMapper;
    private final CommentMapper commentMapper;
    private final InteractionAccessClient accessClient;

    public LikeService(LikeRecordMapper likeMapper,
                       QuestionMapper questionMapper,
                       AnswerMapper answerMapper,
                       CommentMapper commentMapper,
                       InteractionAccessClient accessClient) {
        this.likeMapper = likeMapper;
        this.questionMapper = questionMapper;
        this.answerMapper = answerMapper;
        this.commentMapper = commentMapper;
        this.accessClient = accessClient;
    }

    @Transactional
    public LikeResult like(Long userId, String role, String type, Long targetId) {
        Target target = lockTarget(userId, role, type, targetId);
        if (target.ownerId().equals(userId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "不能点赞自己的内容");
        }
        LocalDateTime now = now();
        LikeRecord record = likeMapper.selectForUpdate(userId, target.type(), targetId);
        int count = target.likeCount();
        if (record == null) {
            record = new LikeRecord();
            record.setUserId(userId);
            record.setTargetType(target.type());
            record.setTargetId(targetId);
            record.setActive(true);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            likeMapper.insert(record);
            count += 1;
        } else if (!Boolean.TRUE.equals(record.getActive())) {
            likeMapper.updateActive(userId, target.type(), targetId, true, now);
            count += 1;
        }
        updateCount(target, count, now);
        return new LikeResult(true, count);
    }

    @Transactional
    public LikeResult unlike(Long userId, String role, String type, Long targetId) {
        Target target = lockTarget(userId, role, type, targetId);
        LikeRecord record = likeMapper.selectForUpdate(userId, target.type(), targetId);
        int count = target.likeCount();
        if (record != null && Boolean.TRUE.equals(record.getActive())) {
            likeMapper.updateActive(userId, target.type(), targetId, false, now());
            count = Math.max(0, count - 1);
            updateCount(target, count, now());
        }
        return new LikeResult(false, count);
    }

    private Target lockTarget(Long userId, String role, String type, Long targetId) {
        String normalized = type == null ? "" : type.trim().toUpperCase();
        if ("ANSWER".equals(normalized)) {
            Answer answer = answerMapper.selectForUpdate(targetId);
            if (answer == null || !"VISIBLE".equals(answer.getState())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "回答不存在或不可点赞");
            }
            Question question = requireVisibleQuestion(answer.getQuestionId());
            requireAccess(userId, role, question.getCourseId());
            return new Target("ANSWER", answer.getId(), answer.getAuthorId(), question.getCourseId(),
                    answer.getLikeCount() == null ? 0 : answer.getLikeCount());
        }
        if ("COMMENT".equals(normalized)) {
            Comment comment = commentMapper.selectForUpdate(targetId);
            if (comment == null || !"VISIBLE".equals(comment.getState())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在或不可点赞");
            }
            Answer answer = answerMapper.selectById(comment.getAnswerId());
            if (answer == null || !"VISIBLE".equals(answer.getState())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "评论不可点赞");
            }
            Question question = requireVisibleQuestion(answer.getQuestionId());
            requireAccess(userId, role, question.getCourseId());
            return new Target("COMMENT", comment.getId(), comment.getAuthorId(), question.getCourseId(),
                    comment.getLikeCount() == null ? 0 : comment.getLikeCount());
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "只支持 ANSWER/COMMENT 点赞");
    }

    private void updateCount(Target target, int count, LocalDateTime now) {
        if ("ANSWER".equals(target.type())) {
            Answer answer = answerMapper.selectById(target.id());
            answer.setLikeCount(count);
            answer.setUpdatedAt(now);
            answerMapper.updateById(answer);
        } else {
            Comment comment = commentMapper.selectById(target.id());
            comment.setLikeCount(count);
            comment.setUpdatedAt(now);
            commentMapper.updateById(comment);
        }
    }

    private void requireAccess(Long userId, String role, Long courseId) {
        if ("STUDENT".equals(role)) {
            if (!accessClient.studentAllowed(userId, courseId)) {
                throw new BusinessException(ErrorCode.NO_COURSE_ACCESS, "没有该课程的有效学习权益");
            }
            return;
        }
        if ("TEACHER".equals(role)) {
            if (!accessClient.teacherAssigned(userId, courseId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "不在负责课程范围");
            }
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "当前角色不能点赞");
    }

    private Question requireVisibleQuestion(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null || !"VISIBLE".equals(question.getState())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "问题不存在或已隐藏");
        }
        return question;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private record Target(String type, Long id, Long ownerId, Long courseId, int likeCount) {
    }
}