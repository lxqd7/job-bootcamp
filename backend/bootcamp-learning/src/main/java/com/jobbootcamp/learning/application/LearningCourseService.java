package com.jobbootcamp.learning.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.CourseCatalogResponse;
import com.jobbootcamp.learning.domain.LessonProgress;
import com.jobbootcamp.contracts.internal.AccessCheckResponse;
import com.jobbootcamp.learning.domain.dto.LearningCourseResponse;
import com.jobbootcamp.learning.infrastructure.LessonProgressMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LearningCourseService {

    private final CourseClient courseClient;
    private final EntitlementService entitlementService;
    private final LessonProgressMapper progressMapper;

    public LearningCourseService(CourseClient courseClient,
                                 EntitlementService entitlementService,
                                 LessonProgressMapper progressMapper) {
        this.courseClient = courseClient;
        this.entitlementService = entitlementService;
        this.progressMapper = progressMapper;
    }

    public LearningCourseResponse course(long userId, long courseId) {
        AccessCheckResponse access = entitlementService.checkAccess(userId, courseId);
        if (!access.allowed()) {
            throw new BusinessException(ErrorCode.NO_COURSE_ACCESS, "没有该课程的有效学习权益");
        }
        CourseCatalogResponse catalog = courseClient.catalog(courseId);
        Map<Long, LessonProgress> progressByLesson = new LinkedHashMap<>();
        for (LessonProgress progress : progressMapper.selectList(Wrappers.<LessonProgress>lambdaQuery()
                .eq(LessonProgress::getUserId, userId)
                .eq(LessonProgress::getCourseId, courseId))) {
            progressByLesson.put(progress.getLessonId(), progress);
        }
        long total = 0;
        long completed = 0;
        List<LearningCourseResponse.LearningChapter> chapters = catalog.chapters().stream()
                .map(chapter -> new LearningCourseResponse.LearningChapter(chapter.id(), chapter.title(),
                        chapter.sort(), chapter.lessons().stream().map(lesson -> {
                            LessonProgress progress = progressByLesson.get(lesson.id());
                            return new LearningCourseResponse.LearningLesson(lesson.id(), lesson.title(),
                                    lesson.sort(), lesson.mediaId(), lesson.durationMs(),
                                    progress == null || progress.getPositionMs() == null ? 0 : progress.getPositionMs(),
                                    progress == null || progress.getMaxPositionMs() == null ? 0 : progress.getMaxPositionMs(),
                                    progress != null && Boolean.TRUE.equals(progress.getCompleted()),
                                    progress == null ? null : progress.getCompletedAt());
                        }).toList()))
                .toList();
        for (LearningCourseResponse.LearningChapter chapter : chapters) {
            total += chapter.lessons().size();
            completed += chapter.lessons().stream().filter(LearningCourseResponse.LearningLesson::completed).count();
        }
        return new LearningCourseResponse(String.valueOf(catalog.courseId()), catalog.title(),
                catalog.description(), catalog.catalogVersion() == null ? 0 : catalog.catalogVersion(),
                total, completed, chapters);
    }
}