package com.jobbootcamp.interaction.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.ProfileBatchItem;
import com.jobbootcamp.interaction.domain.Answer;
import com.jobbootcamp.interaction.domain.Comment;
import com.jobbootcamp.interaction.domain.ModerationAction;
import com.jobbootcamp.interaction.domain.Question;
import com.jobbootcamp.interaction.domain.dto.AnswerView;
import com.jobbootcamp.interaction.domain.dto.BodyRequest;
import com.jobbootcamp.interaction.domain.dto.CommentView;
import com.jobbootcamp.interaction.domain.dto.PageResponse;
import com.jobbootcamp.interaction.domain.dto.QuestionRequest;
import com.jobbootcamp.interaction.domain.dto.QuestionSummary;
import com.jobbootcamp.interaction.domain.dto.QuestionView;
import com.jobbootcamp.interaction.domain.dto.ThreadView;
import com.jobbootcamp.interaction.domain.dto.TraceAuthorView;
import com.jobbootcamp.interaction.infrastructure.AnswerMapper;
import com.jobbootcamp.interaction.infrastructure.CommentMapper;
import com.jobbootcamp.interaction.infrastructure.LikeRecordMapper;
import com.jobbootcamp.interaction.infrastructure.ModerationActionMapper;
import com.jobbootcamp.interaction.infrastructure.QuestionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 问答线程：匿名问题不返回作者 ID；父问题删除/隐藏后整线程不可公开访问；
 * 教师只能操作负责课程，管理员隐藏和匿名追溯分别走独立权限与审计。
 */
@Service
public class QuestionService {

    private final QuestionMapper questionMapper;
    private final AnswerMapper answerMapper;
    private final CommentMapper commentMapper;
    private final ModerationActionMapper moderationMapper;
    private final LikeRecordMapper likeMapper;
    private final InteractionAccessClient accessClient;

    public QuestionService(QuestionMapper questionMapper,
                           AnswerMapper answerMapper,
                           CommentMapper commentMapper,
                           ModerationActionMapper moderationMapper,
                           LikeRecordMapper likeMapper,
                           InteractionAccessClient accessClient) {
        this.questionMapper = questionMapper;
        this.answerMapper = answerMapper;
        this.commentMapper = commentMapper;
        this.moderationMapper = moderationMapper;
        this.likeMapper = likeMapper;
        this.accessClient = accessClient;
    }

    public PageResponse<QuestionSummary> listQuestions(Long userId, String role, Long courseId,
                                                       int page, int pageSize) {
        requireRead(userId, role, courseId);
        long pageNumber = Math.max(1, page);
        long size = Math.min(100, Math.max(1, pageSize));
        IPage<Question> result = questionMapper.selectPage(new Page<>(pageNumber, size),
                Wrappers.<Question>lambdaQuery()
                        .eq(Question::getCourseId, courseId)
                        .eq(Question::getState, "VISIBLE")
                        .orderByDesc(Question::getCreatedAt)
                        .orderByDesc(Question::getId));
        Map<Long, ProfileBatchItem> profiles = profiles(result.getRecords().stream()
                .filter(question -> !Boolean.TRUE.equals(question.getAnonymous()))
                .map(Question::getAuthorId).distinct().toList());
        List<QuestionSummary> items = result.getRecords().stream().map(question -> {
            boolean anonymous = Boolean.TRUE.equals(question.getAnonymous());
            String display = anonymous ? "匿名学员"
                    : displayName(question.getAuthorId(), "学员", profiles);
            return new QuestionSummary(question.getId(), question.getCourseId(), question.getLessonId(),
                    question.getTitle(), display, "STUDENT", anonymous,
                    question.getAuthorId().equals(userId), answerMapper.countVisible(question.getId()),
                    question.getCreatedAt(), question.getUpdatedAt());
        }).toList();
        return new PageResponse<>(items, result.getTotal(), pageNumber, size);
    }

    @Transactional
    public QuestionView createQuestion(Long userId, String role, Long courseId, QuestionRequest request) {
        if (!"STUDENT".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有学生可以提问");
        }
        requireRead(userId, role, courseId);
        String title = text(request == null ? null : request.title(), "标题", 1, 100);
        String body = text(request == null ? null : request.body(), "正文", 1, 10000);
        if (request != null && request.lessonId() != null && request.lessonId() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "lessonId 不合法");
        }
        LocalDateTime now = now();
        Question question = new Question();
        question.setCourseId(courseId);
        question.setLessonId(request == null ? null : request.lessonId());
        question.setAuthorId(userId);
        question.setTitle(title);
        question.setBody(body);
        question.setAnonymous(Boolean.TRUE.equals(request == null ? null : request.anonymous()));
        question.setState("VISIBLE");
        question.setRevision(0L);
        question.setCreatedAt(now);
        question.setUpdatedAt(now);
        questionMapper.insert(question);
        return questionView(question, userId);
    }

    public ThreadView getQuestion(Long userId, String role, Long id, int page, int pageSize) {
        Question question = requireVisibleQuestion(id);
        requireRead(userId, role, question.getCourseId());
        long pageNumber = Math.max(1, page);
        long size = Math.min(100, Math.max(1, pageSize));
        IPage<Answer> result = answerMapper.selectPage(new Page<>(pageNumber, size),
                Wrappers.<Answer>lambdaQuery()
                        .eq(Answer::getQuestionId, id)
                        .eq(Answer::getState, "VISIBLE")
                        .orderByAsc(Answer::getCreatedAt)
                        .orderByAsc(Answer::getId));
        Map<Long, ProfileBatchItem> profiles = profiles(answerAuthorIds(question, result.getRecords()));
        List<AnswerView> answers = result.getRecords().stream()
                .map(answer -> answerView(answer, userId, profiles)).toList();
        return new ThreadView(questionView(question, userId, profiles), answers,
                result.getTotal(), pageNumber, size);
    }

    @Transactional
    public QuestionView updateQuestion(Long userId, String role, Long id, QuestionRequest request) {
        Question question = questionMapper.selectForUpdate(id);
        requireExisting(question);
        requireRead(userId, role, question.getCourseId());
        requireOwner(question.getAuthorId(), userId);
        if (!"VISIBLE".equals(question.getState())) {
            throw new BusinessException(ErrorCode.CONFLICT, "问题已删除或隐藏");
        }
        requireRevision(question.getRevision(), request == null ? null : request.revision());
        question.setTitle(text(request == null ? null : request.title(), "标题", 1, 100));
        question.setBody(text(request == null ? null : request.body(), "正文", 1, 10000));
        if (request != null && request.anonymous() != null) {
            question.setAnonymous(request.anonymous());
        }
        question.setRevision(question.getRevision() + 1);
        question.setUpdatedAt(now());
        questionMapper.updateById(question);
        return questionView(question, userId);
    }

    @Transactional
    public void deleteQuestion(Long userId, String role, Long id) {
        Question question = questionMapper.selectForUpdate(id);
        requireExisting(question);
        requireRead(userId, role, question.getCourseId());
        requireOwner(question.getAuthorId(), userId);
        if ("DELETED".equals(question.getState())) {
            return;
        }
        question.setState("DELETED");
        question.setRevision(question.getRevision() + 1);
        question.setUpdatedAt(now());
        questionMapper.updateById(question);
    }

    public PageResponse<AnswerView> listAnswers(Long userId, String role, Long questionId,
                                                int page, int pageSize) {
        Question question = requireVisibleQuestion(questionId);
        requireRead(userId, role, question.getCourseId());
        long pageNumber = Math.max(1, page);
        long size = Math.min(100, Math.max(1, pageSize));
        IPage<Answer> result = answerMapper.selectPage(new Page<>(pageNumber, size),
                Wrappers.<Answer>lambdaQuery()
                        .eq(Answer::getQuestionId, questionId)
                        .eq(Answer::getState, "VISIBLE")
                        .orderByAsc(Answer::getCreatedAt)
                        .orderByAsc(Answer::getId));
        Map<Long, ProfileBatchItem> profiles = profiles(result.getRecords().stream()
                .map(Answer::getAuthorId).distinct().toList());
        return new PageResponse<>(result.getRecords().stream()
                .map(answer -> answerView(answer, userId, profiles)).toList(),
                result.getTotal(), pageNumber, size);
    }

    @Transactional
    public AnswerView createAnswer(Long userId, String role, Long questionId, BodyRequest request) {
        Question question = questionMapper.selectForUpdate(questionId);
        requireVisibleQuestion(question);
        requireRead(userId, role, question.getCourseId());
        if (!"STUDENT".equals(role) && !"TEACHER".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前角色不能回答");
        }
        LocalDateTime now = now();
        Answer answer = new Answer();
        answer.setQuestionId(questionId);
        answer.setAuthorId(userId);
        answer.setAuthorRole(role);
        answer.setBody(text(request == null ? null : request.body(), "回答", 1, 10000));
        answer.setState("VISIBLE");
        answer.setLikeCount(0);
        answer.setRevision(0L);
        answer.setCreatedAt(now);
        answer.setUpdatedAt(now);
        answerMapper.insert(answer);
        question.setLastAnswerId(answer.getId());
        question.setUpdatedAt(now);
        questionMapper.updateById(question);
        return answerView(answer, userId, profiles(List.of(userId)));
    }

    @Transactional
    public AnswerView updateAnswer(Long userId, String role, Long id, BodyRequest request) {
        Answer answer = answerMapper.selectForUpdate(id);
        if (answer == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "回答不存在");
        }
        Question question = requireVisibleQuestion(answer.getQuestionId());
        requireRead(userId, role, question.getCourseId());
        requireOwner(answer.getAuthorId(), userId);
        if (!"VISIBLE".equals(answer.getState())) {
            throw new BusinessException(ErrorCode.CONFLICT, "回答已删除或隐藏");
        }
        requireRevision(answer.getRevision(), request == null ? null : request.revision());
        answer.setBody(text(request == null ? null : request.body(), "回答", 1, 10000));
        answer.setRevision(answer.getRevision() + 1);
        answer.setUpdatedAt(now());
        answerMapper.updateById(answer);
        return answerView(answer, userId, profiles(List.of(answer.getAuthorId())));
    }

    @Transactional
    public void deleteAnswer(Long userId, String role, Long id) {
        Answer answer = answerMapper.selectForUpdate(id);
        if (answer == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "回答不存在");
        }
        Question question = requireVisibleQuestion(answer.getQuestionId());
        requireRead(userId, role, question.getCourseId());
        requireOwner(answer.getAuthorId(), userId);
        if ("DELETED".equals(answer.getState())) {
            return;
        }
        answer.setState("DELETED");
        answer.setRevision(answer.getRevision() + 1);
        answer.setUpdatedAt(now());
        answerMapper.updateById(answer);
    }

    public PageResponse<CommentView> listComments(Long userId, String role, Long answerId,
                                                  int page, int pageSize) {
        Answer answer = requireVisibleAnswer(answerId);
        Question question = requireVisibleQuestion(answer.getQuestionId());
        requireRead(userId, role, question.getCourseId());
        long pageNumber = Math.max(1, page);
        long size = Math.min(100, Math.max(1, pageSize));
        IPage<Comment> result = commentMapper.selectPage(new Page<>(pageNumber, size),
                Wrappers.<Comment>lambdaQuery()
                        .eq(Comment::getAnswerId, answerId)
                        .eq(Comment::getState, "VISIBLE")
                        .orderByAsc(Comment::getCreatedAt)
                        .orderByAsc(Comment::getId));
        Map<Long, ProfileBatchItem> profiles = profiles(result.getRecords().stream()
                .map(Comment::getAuthorId).distinct().toList());
        return new PageResponse<>(result.getRecords().stream()
                .map(comment -> commentView(comment, userId, profiles)).toList(),
                result.getTotal(), pageNumber, size);
    }

    @Transactional
    public CommentView createComment(Long userId, String role, Long answerId, BodyRequest request) {
        Answer answer = requireVisibleAnswer(answerId);
        Question question = requireVisibleQuestion(answer.getQuestionId());
        requireRead(userId, role, question.getCourseId());
        LocalDateTime now = now();
        Comment comment = new Comment();
        comment.setAnswerId(answerId);
        comment.setAuthorId(userId);
        comment.setBody(text(request == null ? null : request.body(), "评论", 1, 10000));
        comment.setState("VISIBLE");
        comment.setLikeCount(0);
        comment.setRevision(0L);
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);
        commentMapper.insert(comment);
        return commentView(comment, userId, profiles(List.of(userId)));
    }

    @Transactional
    public CommentView updateComment(Long userId, String role, Long id, BodyRequest request) {
        Comment comment = commentMapper.selectForUpdate(id);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        Answer answer = requireVisibleAnswer(comment.getAnswerId());
        Question question = requireVisibleQuestion(answer.getQuestionId());
        requireRead(userId, role, question.getCourseId());
        requireOwner(comment.getAuthorId(), userId);
        if (!"VISIBLE".equals(comment.getState())) {
            throw new BusinessException(ErrorCode.CONFLICT, "评论已删除或隐藏");
        }
        requireRevision(comment.getRevision(), request == null ? null : request.revision());
        comment.setBody(text(request == null ? null : request.body(), "评论", 1, 10000));
        comment.setRevision(comment.getRevision() + 1);
        comment.setUpdatedAt(now());
        commentMapper.updateById(comment);
        return commentView(comment, userId, profiles(List.of(comment.getAuthorId())));
    }

    @Transactional
    public void deleteComment(Long userId, String role, Long id) {
        Comment comment = commentMapper.selectForUpdate(id);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        Answer answer = requireVisibleAnswer(comment.getAnswerId());
        Question question = requireVisibleQuestion(answer.getQuestionId());
        requireRead(userId, role, question.getCourseId());
        requireOwner(comment.getAuthorId(), userId);
        if ("DELETED".equals(comment.getState())) {
            return;
        }
        comment.setState("DELETED");
        comment.setRevision(comment.getRevision() + 1);
        comment.setUpdatedAt(now());
        commentMapper.updateById(comment);
    }

    @Transactional
    public void hide(Long adminId, String type, Long id, String reason) {
        String normalized = type == null ? "" : type.trim().toUpperCase();
        String checkedReason = text(reason, "隐藏原因", 1, 1000);
        if ("QUESTION".equals(normalized)) {
            Question question = questionMapper.selectForUpdate(id);
            requireExisting(question);
            question.setState("HIDDEN");
            question.setRevision(question.getRevision() + 1);
            question.setUpdatedAt(now());
            questionMapper.updateById(question);
        } else if ("ANSWER".equals(normalized)) {
            Answer answer = answerMapper.selectForUpdate(id);
            if (answer == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "回答不存在");
            }
            answer.setState("HIDDEN");
            answer.setRevision(answer.getRevision() + 1);
            answer.setUpdatedAt(now());
            answerMapper.updateById(answer);
        } else if ("COMMENT".equals(normalized)) {
            Comment comment = commentMapper.selectForUpdate(id);
            if (comment == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
            }
            comment.setState("HIDDEN");
            comment.setRevision(comment.getRevision() + 1);
            comment.setUpdatedAt(now());
            commentMapper.updateById(comment);
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "不支持的内容类型");
        }
        ModerationAction action = new ModerationAction();
        action.setTargetType(normalized);
        action.setTargetId(id);
        action.setAdminId(adminId);
        action.setAction("HIDE");
        action.setReason(checkedReason);
        action.setCreatedAt(now());
        moderationMapper.insert(action);
    }

    @Transactional
    public TraceAuthorView traceQuestionAuthor(Long adminId, Long questionId, String reason) {
        Question question = requireExisting(questionMapper.selectById(questionId));
        String checkedReason = text(reason, "追溯原因", 1, 1000);
        Map<Long, ProfileBatchItem> profiles = profiles(List.of(question.getAuthorId()));
        ProfileBatchItem profile = profiles.get(question.getAuthorId());
        ModerationAction action = new ModerationAction();
        action.setTargetType("QUESTION");
        action.setTargetId(questionId);
        action.setAdminId(adminId);
        action.setAction("TRACE_AUTHOR");
        action.setReason(checkedReason);
        action.setCreatedAt(now());
        moderationMapper.insert(action);
        return new TraceAuthorView(questionId, question.getAuthorId(),
                profile == null ? "学员 #" + question.getAuthorId() : profile.nickname());
    }

    private void requireRead(Long userId, String role, Long courseId) {
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
        throw new BusinessException(ErrorCode.FORBIDDEN, "当前角色不能访问问答");
    }

    private Question requireVisibleQuestion(Long id) {
        Question question = questionMapper.selectById(id);
        return requireVisibleQuestion(question);
    }

    private Question requireVisibleQuestion(Question question) {
        requireExisting(question);
        if (!"VISIBLE".equals(question.getState())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "问题不存在、已删除或已隐藏");
        }
        return question;
    }

    private Question requireExisting(Question question) {
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "问题不存在");
        }
        return question;
    }

    private Answer requireVisibleAnswer(Long id) {
        Answer answer = answerMapper.selectById(id);
        if (answer == null || !"VISIBLE".equals(answer.getState())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "回答不存在、已删除或已隐藏");
        }
        return answer;
    }

    private void requireOwner(Long authorId, Long userId) {
        if (!authorId.equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能修改或删除自己的内容");
        }
    }

    private void requireRevision(Long current, Long expected) {
        if (expected == null || !expected.equals(current)) {
            throw new BusinessException(ErrorCode.CONFLICT, "内容已在其他位置更新，请刷新后重试");
        }
    }

    private QuestionView questionView(Question question, Long requesterId) {
        return questionView(question, requesterId,
                profiles(Boolean.TRUE.equals(question.getAnonymous())
                        ? List.of() : List.of(question.getAuthorId())));
    }

    private QuestionView questionView(Question question, Long requesterId,
                                      Map<Long, ProfileBatchItem> profiles) {
        boolean anonymous = Boolean.TRUE.equals(question.getAnonymous());
        boolean mine = question.getAuthorId().equals(requesterId);
        String display = anonymous ? "匿名学员" : displayName(question.getAuthorId(), "学员", profiles);
        return new QuestionView(question.getId(), question.getCourseId(), question.getLessonId(),
                question.getTitle(), question.getBody(), anonymous ? null : question.getAuthorId(),
                display, "STUDENT", anonymous, mine, question.getState(), question.getRevision(),
                question.getCreatedAt(), question.getUpdatedAt());
    }

    private AnswerView answerView(Answer answer, Long requesterId, Map<Long, ProfileBatchItem> profiles) {
        String prefix = "TEACHER".equals(answer.getAuthorRole()) ? "教师" : "学员";
        return new AnswerView(answer.getId(), answer.getQuestionId(), answer.getBody(),
                displayName(answer.getAuthorId(), prefix, profiles), answer.getAuthorRole(),
                answer.getAuthorId().equals(requesterId), commentMapper.countVisible(answer.getId()),
                answer.getLikeCount() == null ? 0 : answer.getLikeCount(),
                likeMapper.active(requesterId, "ANSWER", answer.getId()) > 0,
                answer.getRevision(), answer.getCreatedAt(), answer.getUpdatedAt());
    }

    private CommentView commentView(Comment comment, Long requesterId, Map<Long, ProfileBatchItem> profiles) {
        ProfileBatchItem profile = profiles.get(comment.getAuthorId());
        String role = profile == null ? "STUDENT" : profile.role();
        String prefix = "TEACHER".equals(role) ? "教师" : "学员";
        return new CommentView(comment.getId(), comment.getAnswerId(), comment.getBody(),
                displayName(comment.getAuthorId(), prefix, profiles), role,
                comment.getAuthorId().equals(requesterId),
                comment.getLikeCount() == null ? 0 : comment.getLikeCount(),
                likeMapper.active(requesterId, "COMMENT", comment.getId()) > 0,
                comment.getRevision(), comment.getCreatedAt());
    }

    private String displayName(Long userId, String prefix, Map<Long, ProfileBatchItem> profiles) {
        ProfileBatchItem profile = profiles.get(userId);
        return profile == null || profile.nickname() == null || profile.nickname().isBlank()
                ? prefix + " #" + userId : profile.nickname();
    }

    private Map<Long, ProfileBatchItem> profiles(List<Long> userIds) {
        return accessClient.profiles(userIds.stream().distinct().toList());
    }

    private List<Long> answerAuthorIds(Question question, List<Answer> answers) {
        List<Long> ids = new ArrayList<>();
        if (!Boolean.TRUE.equals(question.getAnonymous())) {
            ids.add(question.getAuthorId());
        }
        answers.forEach(answer -> ids.add(answer.getAuthorId()));
        return ids;
    }

    private String text(String value, String label, int min, int max) {
        if (value == null || value.isBlank() || value.length() < min || value.length() > max) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    label + "长度必须为 " + min + "～" + max);
        }
        return value;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}