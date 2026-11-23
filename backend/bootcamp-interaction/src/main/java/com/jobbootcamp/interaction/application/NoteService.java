package com.jobbootcamp.interaction.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.ProfileBatchItem;
import com.jobbootcamp.interaction.domain.ExcellentReward;
import com.jobbootcamp.interaction.domain.ModerationAction;
import com.jobbootcamp.interaction.domain.Note;
import com.jobbootcamp.interaction.domain.NoteVersion;
import com.jobbootcamp.interaction.domain.OutboxEvent;
import com.jobbootcamp.interaction.domain.dto.NoteRequest;
import com.jobbootcamp.interaction.domain.dto.NoteSummary;
import com.jobbootcamp.interaction.domain.dto.NoteView;
import com.jobbootcamp.interaction.domain.dto.PageResponse;
import com.jobbootcamp.interaction.infrastructure.ExcellentRewardMapper;
import com.jobbootcamp.interaction.infrastructure.ModerationActionMapper;
import com.jobbootcamp.interaction.infrastructure.NoteMapper;
import com.jobbootcamp.interaction.infrastructure.NoteVersionMapper;
import com.jobbootcamp.interaction.infrastructure.OutboxEventMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 笔记：原创可公开/私密；采集副本始终私人且每人每原文唯一；原文更新/删除不影响副本。
 * 所有者的“我的笔记”读取/修改/删除不要求当前课程权益，满足退款后的管理例外。
 */
@Service
public class NoteService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PRIVATE = "PRIVATE";
    private static final String PUBLIC = "COURSE_PUBLIC";
    private static final String EXCELLENT_EVENT = "interaction.note-excellent.v1";

    private final NoteMapper noteMapper;
    private final NoteVersionMapper versionMapper;
    private final ModerationActionMapper moderationMapper;
    private final ExcellentRewardMapper rewardMapper;
    private final OutboxEventMapper outboxMapper;
    private final InteractionAccessClient accessClient;

    public NoteService(NoteMapper noteMapper,
                       NoteVersionMapper versionMapper,
                       ModerationActionMapper moderationMapper,
                       ExcellentRewardMapper rewardMapper,
                       OutboxEventMapper outboxMapper,
                       InteractionAccessClient accessClient) {
        this.noteMapper = noteMapper;
        this.versionMapper = versionMapper;
        this.moderationMapper = moderationMapper;
        this.rewardMapper = rewardMapper;
        this.outboxMapper = outboxMapper;
        this.accessClient = accessClient;
    }

    public PageResponse<NoteSummary> myNotes(Long userId, String role, String type, int page, int pageSize) {
        if (!"STUDENT".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有学生可以管理个人笔记");
        }
        String normalizedType = type == null || type.isBlank() ? null : type.trim().toUpperCase();
        long pageNumber = Math.max(1, page);
        long size = Math.min(100, Math.max(1, pageSize));
        IPage<Note> result = noteMapper.selectPage(new Page<>(pageNumber, size),
                Wrappers.<Note>lambdaQuery()
                        .eq(Note::getOwnerId, userId)
                        .eq(normalizedType != null, Note::getType, normalizedType)
                        .orderByDesc(Note::getUpdatedAt)
                        .orderByDesc(Note::getId));
        Map<Long, ProfileBatchItem> profiles = accessClient.profiles(List.of(userId));
        return new PageResponse<>(result.getRecords().stream()
                .map(note -> summary(note, userId, profiles)).toList(),
                result.getTotal(), pageNumber, size);
    }

    public PageResponse<NoteSummary> courseNotes(Long userId, String role, Long courseId,
                                                 int page, int pageSize) {
        requireCourseRead(userId, role, courseId);
        long pageNumber = Math.max(1, page);
        long size = Math.min(100, Math.max(1, pageSize));
        IPage<Note> result = noteMapper.selectPage(new Page<>(pageNumber, size),
                Wrappers.<Note>lambdaQuery()
                        .eq(Note::getCourseId, courseId)
                        .eq(Note::getVisibility, PUBLIC)
                        .eq(Note::getState, "VISIBLE")
                        .eq(Note::getType, "ORIGINAL")
                        .orderByDesc(Note::getUpdatedAt)
                        .orderByDesc(Note::getId));
        Map<Long, ProfileBatchItem> profiles = accessClient.profiles(result.getRecords().stream()
                .map(Note::getOwnerId).distinct().toList());
        return new PageResponse<>(result.getRecords().stream()
                .map(note -> summary(note, userId, profiles)).toList(),
                result.getTotal(), pageNumber, size);
    }

    public NoteView getNote(Long userId, String role, Long id) {
        Note note = noteMapper.selectById(id);
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "笔记不存在");
        }
        if (note.getOwnerId().equals(userId)) {
            return view(note, userId);
        }
        if (!"VISIBLE".equals(note.getState()) || !PUBLIC.equals(note.getVisibility())
                || !"ORIGINAL".equals(note.getType())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "笔记不存在或不可公开访问");
        }
        requireCourseRead(userId, role, note.getCourseId());
        return view(note, userId);
    }

    @Transactional
    public NoteView createNote(Long userId, String role, Long courseId, NoteRequest request) {
        if (!"STUDENT".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有学生可以创建笔记");
        }
        requireCourseRead(userId, role, courseId);
        String body = text(request == null ? null : request.body(), "笔记正文", 1, 20000);
        String visibility = visibility(request == null ? null : request.visibility(), PRIVATE);
        validatePosition(request);
        LocalDateTime now = now();
        Note note = new Note();
        note.setOwnerId(userId);
        note.setCourseId(courseId);
        note.setLessonId(request == null ? null : request.lessonId());
        note.setPositionMs(request == null ? null : request.positionMs());
        note.setType("ORIGINAL");
        note.setBody(body);
        note.setVisibility(visibility);
        note.setState("VISIBLE");
        note.setContentRevision(0L);
        note.setExcellent(false);
        note.setCreatedAt(now);
        note.setUpdatedAt(now);
        noteMapper.insert(note);
        version(note, now);
        return view(note, userId);
    }

    @Transactional
    public NoteView updateNote(Long userId, String role, Long id, NoteRequest request) {
        Note note = noteMapper.selectForUpdate(id);
        requireExisting(note);
        requireOwner(note, userId);
        if (!"VISIBLE".equals(note.getState())) {
            throw new BusinessException(ErrorCode.CONFLICT, "笔记已删除或隐藏");
        }
        requireRevision(note.getContentRevision(), request == null ? null : request.revision());
        boolean changed = false;
        if (request.body() != null) {
            note.setBody(text(request.body(), "笔记正文", 1, 20000));
            changed = true;
        }
        if (request.visibility() != null) {
            String visibility = visibility(request.visibility(), note.getVisibility());
            if ("COLLECTED".equals(note.getType()) && PUBLIC.equals(visibility)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "采集副本不能公开");
            }
            note.setVisibility(visibility);
            changed = true;
        }
        if (request.lessonId() != null) {
            note.setLessonId(request.lessonId());
            changed = true;
        }
        if (request.positionMs() != null) {
            note.setPositionMs(request.positionMs());
            changed = true;
        }
        if (changed) {
            // 作者修改内容或可见性后取消优秀展示，但已发奖励与版本记录保留。
            note.setExcellent(false);
        }
        validatePosition(new NoteRequest(note.getLessonId(), note.getPositionMs(),
                note.getBody(), note.getVisibility(), note.getContentRevision()));
        note.setContentRevision(note.getContentRevision() + 1);
        note.setUpdatedAt(now());
        noteMapper.updateById(note);
        version(note, note.getUpdatedAt());
        return view(note, userId);
    }

    @Transactional
    public void deleteNote(Long userId, String role, Long id) {
        Note note = noteMapper.selectForUpdate(id);
        requireExisting(note);
        requireOwner(note, userId);
        if ("DELETED".equals(note.getState())) {
            return;
        }
        note.setState("DELETED");
        note.setExcellent(false);
        note.setContentRevision(note.getContentRevision() + 1);
        note.setUpdatedAt(now());
        noteMapper.updateById(note);
    }

    @Transactional
    public NoteView collect(Long userId, String role, Long sourceId) {
        if (!"STUDENT".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有学生可以采集笔记");
        }
        Note source = noteMapper.selectById(sourceId);
        requireExisting(source);
        if (source.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "不能采集自己的笔记");
        }
        if (!"VISIBLE".equals(source.getState()) || !PUBLIC.equals(source.getVisibility())
                || !"ORIGINAL".equals(source.getType())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "笔记不存在或不可采集");
        }
        requireCourseRead(userId, role, source.getCourseId());
        Note existing = noteMapper.selectByOwnerAndSource(userId, sourceId);
        if (existing != null) {
            return view(existing, userId);
        }
        Map<Long, ProfileBatchItem> profiles = accessClient.profiles(List.of(source.getOwnerId()));
        ProfileBatchItem author = profiles.get(source.getOwnerId());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("authorId", source.getOwnerId());
        snapshot.put("nickname", author == null ? "学员 #" + source.getOwnerId() : author.nickname());
        snapshot.put("role", author == null ? "STUDENT" : author.role());

        LocalDateTime now = now();
        Note note = new Note();
        note.setOwnerId(userId);
        note.setCourseId(source.getCourseId());
        note.setLessonId(source.getLessonId());
        note.setPositionMs(source.getPositionMs());
        note.setType("COLLECTED");
        note.setSourceNoteId(sourceId);
        note.setSourceAuthorSnapshot(write(snapshot));
        note.setBody(source.getBody());
        note.setVisibility(PRIVATE);
        note.setState("VISIBLE");
        note.setContentRevision(0L);
        note.setExcellent(false);
        note.setCreatedAt(now);
        note.setUpdatedAt(now);
        try {
            noteMapper.insert(note);
        } catch (DuplicateKeyException duplicate) {
            Note raced = noteMapper.selectByOwnerAndSource(userId, sourceId);
            if (raced != null) {
                return view(raced, userId);
            }
            throw new BusinessException(ErrorCode.CONFLICT, "该笔记已采集");
        }
        version(note, now);
        return view(note, userId);
    }

    @Transactional
    public void hideNote(Long adminId, Long id, String reason) {
        Note note = noteMapper.selectForUpdate(id);
        requireExisting(note);
        String checkedReason = text(reason, "隐藏原因", 1, 1000);
        note.setState("HIDDEN");
        note.setExcellent(false);
        note.setContentRevision(note.getContentRevision() + 1);
        note.setUpdatedAt(now());
        noteMapper.updateById(note);
        ModerationAction action = new ModerationAction();
        action.setTargetType("NOTE");
        action.setTargetId(id);
        action.setAdminId(adminId);
        action.setAction("HIDE");
        action.setReason(checkedReason);
        action.setCreatedAt(now());
        moderationMapper.insert(action);
    }

    /**
     * 教师评优：锁笔记行并固定当前内容版本；同一笔记只生成一条奖励事实和一次奖励事件。
     * 作者修改取消展示后再次评优只恢复展示，不再发奖。
     */
    @Transactional
    public NoteView evaluate(Long teacherId, String role, Long noteId, Long contentRevision) {
        Note note = lockTeacherNote(teacherId, role, noteId);
        if (!"ORIGINAL".equals(note.getType()) || !PUBLIC.equals(note.getVisibility())
                || !"VISIBLE".equals(note.getState())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "只有公开且未隐藏的原创笔记可以评优");
        }
        if (contentRevision == null || !contentRevision.equals(note.getContentRevision())) {
            throw new BusinessException(ErrorCode.CONFLICT, "笔记内容已变化，请刷新后重新评优");
        }
        ExcellentReward reward = rewardMapper.selectById(noteId);
        if (reward != null && Boolean.TRUE.equals(reward.getRevoked())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该笔记奖励已撤回，不能重新评优");
        }
        if (Boolean.TRUE.equals(note.getExcellent()) && reward != null) {
            return view(note, teacherId);
        }
        LocalDateTime now = now();
        if (reward == null) {
            reward = createReward(note, teacherId, contentRevision, now);
            writeExcellentEvent(note, teacherId, reward, now);
        }
        note.setExcellent(true);
        note.setUpdatedAt(now);
        noteMapper.updateById(note);
        return view(note, teacherId);
    }

    /**
     * 普通取消只撤下优秀展示，不扣分、不删除奖励事实；重复取消幂等。
     */
    @Transactional
    public NoteView cancelEvaluated(Long teacherId, String role, Long noteId) {
        Note note = lockTeacherNote(teacherId, role, noteId);
        if (!"ORIGINAL".equals(note.getType())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "只有原创笔记存在优秀评选");
        }
        if (Boolean.TRUE.equals(note.getExcellent())) {
            note.setExcellent(false);
            note.setUpdatedAt(now());
            noteMapper.updateById(note);
        }
        return view(note, teacherId);
    }

    private Note lockTeacherNote(Long teacherId, String role, Long noteId) {
        if (!"TEACHER".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有教师可以评优");
        }
        Note note = noteMapper.selectForUpdate(noteId);
        requireExisting(note);
        if (!accessClient.teacherAssigned(teacherId, note.getCourseId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不在负责课程范围");
        }
        return note;
    }

    private ExcellentReward createReward(Note note, Long teacherId, Long contentRevision, LocalDateTime now) {
        ExcellentReward reward = new ExcellentReward();
        reward.setNoteId(note.getId());
        reward.setAuthorId(note.getOwnerId());
        reward.setTeacherId(teacherId);
        reward.setContentRevision(contentRevision);
        reward.setAwardedAt(now);
        reward.setRewardEventId(UUID.randomUUID().toString());
        reward.setRewardVersion(1L);
        reward.setRevoked(false);
        rewardMapper.insert(reward);
        return reward;
    }

    private void writeExcellentEvent(Note note, Long teacherId, ExcellentReward reward, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("noteId", String.valueOf(note.getId()));
        payload.put("authorId", String.valueOf(note.getOwnerId()));
        payload.put("teacherId", String.valueOf(teacherId));
        payload.put("noteRevision", String.valueOf(reward.getContentRevision()));
        payload.put("rewardVersion", String.valueOf(reward.getRewardVersion()));
        payload.put("occurredAt", now.toInstant(ZoneOffset.UTC).toString());

        OutboxEvent event = new OutboxEvent();
        event.setEventId(reward.getRewardEventId());
        event.setType(EXCELLENT_EVENT);
        event.setAggregateId(String.valueOf(note.getId()));
        event.setAggregateVersion(reward.getRewardVersion());
        event.setOccurredAt(now);
        event.setPayload(write(payload));
        event.setStatus("NEW");
        event.setAttempt(0);
        event.setNextRetryAt(now);
        outboxMapper.insert(event);
    }

    private void requireCourseRead(Long userId, String role, Long courseId) {
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
        throw new BusinessException(ErrorCode.FORBIDDEN, "当前角色不能访问笔记");
    }

    private NoteSummary summary(Note note, Long requesterId, Map<Long, ProfileBatchItem> profiles) {
        String display = displayName(note, profiles);
        String excerpt = note.getBody().length() > 200 ? note.getBody().substring(0, 200) : note.getBody();
        return new NoteSummary(note.getId(), note.getCourseId(), note.getLessonId(), note.getPositionMs(),
                note.getType(), note.getVisibility(), note.getState(), excerpt, display,
                note.getContentRevision(), note.getCreatedAt(), note.getUpdatedAt(),
                Boolean.TRUE.equals(note.getExcellent()), note.getOwnerId().equals(requesterId));
    }

    private NoteView view(Note note, Long requesterId) {
        Map<Long, ProfileBatchItem> profiles = accessClient.profiles(List.of(note.getOwnerId()));
        Object snapshot = note.getSourceAuthorSnapshot() == null ? null
                : readObject(note.getSourceAuthorSnapshot());
        ExcellentReward reward = rewardMapper.selectById(note.getId());
        return new NoteView(note.getId(), note.getCourseId(), note.getLessonId(), note.getPositionMs(),
                note.getType(), note.getVisibility(), note.getState(), note.getBody(),
                note.getOwnerId(), displayName(note, profiles), note.getSourceNoteId(), snapshot,
                note.getContentRevision(), note.getCreatedAt(), note.getUpdatedAt(),
                Boolean.TRUE.equals(note.getExcellent()),
                reward == null ? null : reward.getContentRevision(),
                note.getOwnerId().equals(requesterId));
    }

    private String displayName(Note note, Map<Long, ProfileBatchItem> profiles) {
        ProfileBatchItem profile = profiles.get(note.getOwnerId());
        return profile == null || profile.nickname() == null || profile.nickname().isBlank()
                ? "学员 #" + note.getOwnerId() : profile.nickname();
    }

    private void version(Note note, LocalDateTime now) {
        NoteVersion version = new NoteVersion();
        version.setNoteId(note.getId());
        version.setContentRevision(note.getContentRevision());
        version.setBody(note.getBody());
        version.setVisibility(note.getVisibility());
        version.setCreatedAt(now);
        versionMapper.insert(version);
    }

    private String visibility(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase();
        if (!PRIVATE.equals(normalized) && !PUBLIC.equals(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "visibility 不合法");
        }
        return normalized;
    }

    private void validatePosition(NoteRequest request) {
        if (request == null) {
            return;
        }
        if (request.lessonId() != null && request.lessonId() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "lessonId 不合法");
        }
        if (request.positionMs() != null && request.positionMs() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "positionMs 不合法");
        }
    }

    private Note requireExisting(Note note) {
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "笔记不存在");
        }
        return note;
    }

    private void requireOwner(Note note, Long userId) {
        if (!note.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能修改或删除自己的笔记");
        }
    }

    private void requireRevision(Long current, Long expected) {
        if (expected == null || !expected.equals(current)) {
            throw new BusinessException(ErrorCode.CONFLICT, "笔记已在其他位置更新，请刷新后重试");
        }
    }

    private String text(String value, String label, int min, int max) {
        if (value == null || value.isBlank() || value.length() < min || value.length() > max) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    label + "长度必须为 " + min + "～" + max);
        }
        return value;
    }

    private Object readObject(String value) {
        try {
            return JSON.readValue(value, Object.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "笔记作者快照损坏");
        }
    }

    private String write(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "笔记作者快照序列化失败");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}