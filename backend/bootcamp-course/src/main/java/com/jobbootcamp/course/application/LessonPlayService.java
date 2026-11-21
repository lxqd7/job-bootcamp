package com.jobbootcamp.course.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.CourseCatalogResponse;
import com.jobbootcamp.contracts.internal.LessonMetadataResponse;
import com.jobbootcamp.contracts.internal.PlayAuthorizationResponse;
import com.jobbootcamp.contracts.internal.PlaySessionCheckResponse;
import com.jobbootcamp.course.domain.Chapter;
import com.jobbootcamp.course.domain.Course;
import com.jobbootcamp.course.domain.Lesson;
import com.jobbootcamp.course.domain.MediaAsset;
import com.jobbootcamp.course.domain.PlaySession;
import com.jobbootcamp.course.infrastructure.ChapterMapper;
import com.jobbootcamp.course.infrastructure.CourseMapper;
import com.jobbootcamp.course.infrastructure.LessonMapper;
import com.jobbootcamp.course.infrastructure.MediaAssetMapper;
import com.jobbootcamp.course.infrastructure.PlaySessionMapper;
import com.jobbootcamp.course.media.MediaAuthorization;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课程目录/小节元数据与播放会话。元数据只返回当前有效小节和媒体时长，
 * 播放会话由 MediaService 固化时长/目录版本，进度侧不能信任客户端时长。
 */
@Service
public class LessonPlayService {

    private final LessonMapper lessonMapper;
    private final ChapterMapper chapterMapper;
    private final CourseMapper courseMapper;
    private final MediaAssetMapper mediaMapper;
    private final PlaySessionMapper playSessionMapper;
    private final MediaService mediaService;

    public LessonPlayService(LessonMapper lessonMapper,
                             ChapterMapper chapterMapper,
                             CourseMapper courseMapper,
                             MediaAssetMapper mediaMapper,
                             PlaySessionMapper playSessionMapper,
                             MediaService mediaService) {
        this.lessonMapper = lessonMapper;
        this.chapterMapper = chapterMapper;
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
        List<Long> activeLessonIds = lessonMapper.selectList(Wrappers.<Lesson>lambdaQuery()
                        .eq(Lesson::getCourseId, course.getId())
                        .eq(Lesson::getDeleted, false))
                .stream().map(Lesson::getId).toList();
        long totalCount = activeLessonIds.size();
        return new LessonMetadataResponse(course.getId(), lesson.getId(), lesson.getMediaId(),
                media == null || media.getDurationMs() == null ? 0 : media.getDurationMs(),
                course.getCatalogVersion(), totalCount,
                Boolean.TRUE.equals(lesson.getDeleted()) ? "DELETED" : course.getStatus(), lesson.getTitle(),
                activeLessonIds);
    }

    public CourseCatalogResponse catalog(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        List<Chapter> chapters = chapterMapper.selectList(Wrappers.<Chapter>lambdaQuery()
                .eq(Chapter::getCourseId, courseId)
                .eq(Chapter::getDeleted, false)
                .orderByAsc(Chapter::getSort)
                .orderByAsc(Chapter::getId));
        List<Lesson> lessons = lessonMapper.selectList(Wrappers.<Lesson>lambdaQuery()
                .eq(Lesson::getCourseId, courseId)
                .eq(Lesson::getDeleted, false)
                .orderByAsc(Lesson::getSort)
                .orderByAsc(Lesson::getId));
        List<Long> mediaIds = lessons.stream().map(Lesson::getMediaId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, MediaAsset> mediaById = new LinkedHashMap<>();
        if (!mediaIds.isEmpty()) {
            mediaMapper.selectBatchIds(mediaIds).forEach(media -> mediaById.put(media.getId(), media));
        }
        Map<Long, List<Lesson>> lessonsByChapter = new LinkedHashMap<>();
        for (Lesson lesson : lessons) {
            lessonsByChapter.computeIfAbsent(lesson.getChapterId(), ignored -> new ArrayList<>()).add(lesson);
        }
        List<CourseCatalogResponse.CatalogChapter> catalogChapters = chapters.stream()
                .map(chapter -> new CourseCatalogResponse.CatalogChapter(chapter.getId(), chapter.getTitle(),
                        chapter.getSort() == null ? 0 : chapter.getSort(),
                        lessonsByChapter.getOrDefault(chapter.getId(), List.of()).stream()
                                .map(lesson -> new CourseCatalogResponse.CatalogLesson(lesson.getId(),
                                        lesson.getChapterId(), lesson.getTitle(),
                                        lesson.getSort() == null ? 0 : lesson.getSort(), lesson.getMediaId(),
                                        duration(mediaById.get(lesson.getMediaId()))))
                                .toList()))
                .toList();
        return new CourseCatalogResponse(course.getId(), course.getTitle(), course.getDescription(),
                course.getCatalogVersion(), catalogChapters);
    }

    private Long duration(MediaAsset media) {
        return media == null ? null : media.getDurationMs();
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
        // 只认签发时属于该用户、该小节的 LEARN 会话；过期或换视频后旧会话直接失效。
        PlaySession session = playSessionMapper.selectById(playSessionId);
        if (session == null
                || !session.getUserId().equals(userId)
                || !session.getLessonId().equals(lessonId)
                || !"LEARN".equals(session.getMode())
                || session.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            return new PlaySessionCheckResponse(false, null, lessonId, null, null, null, null);
        }
        MediaAsset media = mediaMapper.selectById(session.getMediaId());
        Long duration = session.getDurationMs() != null
                ? session.getDurationMs()
                : media == null ? null : media.getDurationMs();
        return new PlaySessionCheckResponse(true, session.getCourseId(), session.getLessonId(),
                session.getMediaId(), duration, session.getCatalogVersion(), session.getMode());
    }

    private Lesson requireLesson(Long lessonId) {
        Lesson lesson = lessonMapper.selectById(lessonId);
        if (lesson == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "小节不存在");
        }
        return lesson;
    }
}