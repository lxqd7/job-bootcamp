package com.jobbootcamp.course.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.ProductSnapshotResponse;
import com.jobbootcamp.course.domain.Course;
import com.jobbootcamp.course.domain.Lesson;
import com.jobbootcamp.course.domain.MediaAsset;
import com.jobbootcamp.course.infrastructure.CourseMapper;
import com.jobbootcamp.course.infrastructure.LessonMapper;
import com.jobbootcamp.course.infrastructure.MediaAssetMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ProductSnapshotService {

    private final CourseMapper courseMapper;
    private final LessonMapper lessonMapper;
    private final MediaAssetMapper mediaMapper;

    public ProductSnapshotService(CourseMapper courseMapper,
                                  LessonMapper lessonMapper,
                                  MediaAssetMapper mediaMapper) {
        this.courseMapper = courseMapper;
        this.lessonMapper = lessonMapper;
        this.mediaMapper = mediaMapper;
    }

    public ProductSnapshotResponse courseSnapshot(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        List<Lesson> lessons = lessonMapper.selectList(Wrappers.<Lesson>lambdaQuery()
                .eq(Lesson::getCourseId, courseId)
                .eq(Lesson::getDeleted, false)
                .orderByAsc(Lesson::getSort)
                .orderByAsc(Lesson::getId));
        List<Long> mediaIds = lessons.stream().map(Lesson::getMediaId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, MediaAsset> mediaById = new LinkedHashMap<>();
        if (!mediaIds.isEmpty()) {
            mediaMapper.selectBatchIds(mediaIds).forEach(media -> mediaById.put(media.getId(), media));
        }
        List<ProductSnapshotResponse.SnapshotLesson> snapshotLessons = lessons.stream()
                .map(lesson -> new ProductSnapshotResponse.SnapshotLesson(
                        lesson.getId(),
                        lesson.getChapterId(),
                        lesson.getTitle(),
                        lesson.getSort() == null ? 0 : lesson.getSort(),
                        lesson.getMediaId(),
                        duration(mediaById.get(lesson.getMediaId()))))
                .toList();
        return new ProductSnapshotResponse(
                "COURSE",
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getCatalogVersion() == null ? 0L : course.getCatalogVersion(),
                course.getPriceCent() == null ? 0L : course.getPriceCent(),
                Boolean.TRUE.equals(course.getIsFree()),
                course.getStatus(),
                course.getCategoryId(),
                snapshotLessons);
    }

    private Long duration(MediaAsset media) {
        return media == null ? null : media.getDurationMs();
    }
}