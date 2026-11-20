package com.jobbootcamp.course.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.LessonMetadataResponse;
import com.jobbootcamp.contracts.internal.PlayAuthorizationResponse;
import com.jobbootcamp.contracts.internal.PlaySessionCheckResponse;
import com.jobbootcamp.course.domain.Course;
import com.jobbootcamp.course.domain.Lesson;
import com.jobbootcamp.course.domain.MediaAsset;
import com.jobbootcamp.course.domain.PlaySession;
import com.jobbootcamp.course.infrastructure.CourseMapper;
import com.jobbootcamp.course.infrastructure.LessonMapper;
import com.jobbootcamp.course.infrastructure.MediaAssetMapper;
import com.jobbootcamp.course.infrastructure.PlaySessionMapper;
import com.jobbootcamp.course.media.MediaAuthorization;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class LessonPlayService {

    private final LessonMapper lessonMapper;
    private final CourseMapper courseMapper;
    private final MediaAssetMapper mediaMapper;
    private final PlaySessionMapper playSessionMapper;
    private final MediaService mediaService;

    public LessonPlayService(LessonMapper lessonMapper,
                             CourseMapper courseMapper,
                             MediaAssetMapper mediaMapper,
                             PlaySessionMapper playSessionMapper,
                             MediaService mediaService) {
        this.lessonMapper = lessonMapper;
        this.courseMapper = courseMapper;
        this.mediaMapper = mediaMapper;
        this.playSessionMapper = playSessionMapper;
        this.mediaService = mediaService;
    }

    public LessonMetadataResponse metadata(Long lessonId) {
        Lesson lesson = requireLesson(lessonId);
        Course course = courseMapper.selectById(lesson.getCourseId());
        if (course == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        MediaAsset media = lesson.getMediaId() == null ? null : mediaMapper.selectById(lesson.getMediaId());
        long totalCount = lessonMapper.selectCount(Wrappers.<Lesson>lambdaQuery()
                .eq(Lesson::getCourseId, course.getId())
                .eq(Lesson::getDeleted, false));
        return new LessonMetadataResponse(course.getId(), lesson.getId(), lesson.getMediaId(),
                media == null || media.getDurationMs() == null ? 0 : media.getDurationMs(),
                course.getCatalogVersion(), totalCount,
                Boolean.TRUE.equals(lesson.getDeleted()) ? "DELETED" : course.getStatus(), lesson.getTitle());
    }

    public PlayAuthorizationResponse issue(Long userId, Long authSessionId, Long lessonId) {
        LessonMetadataResponse metadata = metadata(lessonId);
        if (metadata.mediaId() == null || metadata.durationMs() == null || metadata.durationMs() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "该小节没有可播放的视频");
        }
        if ("DELETED".equals(metadata.status())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "小节已删除");
        }
        MediaAuthorization authorization = mediaService.authorizePlayback(metadata.courseId(), lessonId,
                metadata.mediaId(), metadata.catalogVersion(), userId, authSessionId);
        return new PlayAuthorizationResponse(authorization.provider(), authorization.playSessionId(),
                authorization.expiresAt(), authorization.appId(), authorization.fileId(),
                authorization.psign(), authorization.demoUrl(), authorization.durationMs(), metadata.courseId());
    }

    public PlaySessionCheckResponse check(Long userId, String playSessionId, Long lessonId) {
        PlaySession session = playSessionMapper.selectById(playSessionId);
        if (session == null
                || !session.getUserId().equals(userId)
                || !session.getLessonId().equals(lessonId)
                || !"LEARN".equals(session.getMode())
                || session.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            return new PlaySessionCheckResponse(false, null, lessonId, null, null, null);
        }
        MediaAsset media = mediaMapper.selectById(session.getMediaId());
        return new PlaySessionCheckResponse(true, session.getCourseId(), session.getLessonId(),
                session.getMediaId(), media == null ? null : media.getDurationMs(), session.getMode());
    }

    private Lesson requireLesson(Long lessonId) {
        Lesson lesson = lessonMapper.selectById(lessonId);
        if (lesson == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "小节不存在");
        }
        return lesson;
    }
}