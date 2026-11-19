package com.jobbootcamp.course.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.course.domain.Chapter;
import com.jobbootcamp.course.domain.Course;
import com.jobbootcamp.course.domain.CourseVersion;
import com.jobbootcamp.course.domain.Lesson;
import com.jobbootcamp.course.infrastructure.CategoryClosureMapper;
import com.jobbootcamp.course.infrastructure.ChapterMapper;
import com.jobbootcamp.course.infrastructure.CourseVersionMapper;
import com.jobbootcamp.course.infrastructure.LessonMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CourseVersionService {

    private final ChapterMapper chapterMapper;
    private final LessonMapper lessonMapper;
    private final CourseVersionMapper courseVersionMapper;
    private final CategoryClosureMapper categoryClosureMapper;

    public CourseVersionService(ChapterMapper chapterMapper,
                                LessonMapper lessonMapper,
                                CourseVersionMapper courseVersionMapper,
                                CategoryClosureMapper categoryClosureMapper) {
        this.chapterMapper = chapterMapper;
        this.lessonMapper = lessonMapper;
        this.courseVersionMapper = courseVersionMapper;
        this.categoryClosureMapper = categoryClosureMapper;
    }

    @Transactional
    public long publish(Course course) {
        long versionNo = latestVersionNo(course.getId()) + 1;
        List<Chapter> chapters = chapterMapper.selectList(Wrappers.<Chapter>lambdaQuery()
                .eq(Chapter::getCourseId, course.getId())
                .eq(Chapter::getDeleted, false)
                .orderByAsc(Chapter::getSort)
                .orderByAsc(Chapter::getId));
        List<Lesson> lessons = lessonMapper.selectList(Wrappers.<Lesson>lambdaQuery()
                .eq(Lesson::getCourseId, course.getId())
                .eq(Lesson::getDeleted, false)
                .orderByAsc(Lesson::getSort)
                .orderByAsc(Lesson::getId));
        Map<Long, List<Lesson>> byChapter = new LinkedHashMap<>();
        for (Lesson lesson : lessons) {
            byChapter.computeIfAbsent(lesson.getChapterId(), key -> new ArrayList<>()).add(lesson);
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("courseId", String.valueOf(course.getId()));
        snapshot.put("versionNo", versionNo);
        snapshot.put("title", course.getTitle());
        snapshot.put("priceCent", course.getPriceCent());
        snapshot.put("isFree", Boolean.TRUE.equals(course.getIsFree()));
        snapshot.put("categoryAncestorIds", categoryClosureMapper.selectAncestorIds(course.getCategoryId())
                .stream().map(String::valueOf).toList());

        List<Map<String, Object>> chapterSnapshots = new ArrayList<>();
        for (Chapter chapter : chapters) {
            Map<String, Object> chapterSnapshot = new LinkedHashMap<>();
            chapterSnapshot.put("chapterId", String.valueOf(chapter.getId()));
            chapterSnapshot.put("title", chapter.getTitle());
            chapterSnapshot.put("sort", chapter.getSort());
            List<Map<String, Object>> lessonSnapshots = new ArrayList<>();
            for (Lesson lesson : byChapter.getOrDefault(chapter.getId(), List.of())) {
                Map<String, Object> lessonSnapshot = new LinkedHashMap<>();
                lessonSnapshot.put("lessonId", String.valueOf(lesson.getId()));
                lessonSnapshot.put("title", lesson.getTitle());
                lessonSnapshot.put("sort", lesson.getSort());
                lessonSnapshot.put("mediaId", lesson.getMediaId() == null ? null : String.valueOf(lesson.getMediaId()));
                lessonSnapshot.put("durationMs", null);
                lessonSnapshots.add(lessonSnapshot);
            }
            chapterSnapshot.put("lessons", lessonSnapshots);
            chapterSnapshots.add(chapterSnapshot);
        }
        snapshot.put("chapters", chapterSnapshots);

        List<Map<String, Object>> videoLessons = new ArrayList<>();
        for (Lesson lesson : lessons) {
            if (lesson.getMediaId() != null) {
                Map<String, Object> video = new LinkedHashMap<>();
                video.put("lessonId", String.valueOf(lesson.getId()));
                video.put("mediaId", String.valueOf(lesson.getMediaId()));
                video.put("durationMs", null);
                videoLessons.add(video);
            }
        }
        snapshot.put("videoLessons", videoLessons);

        CourseVersion version = new CourseVersion();
        version.setCourseId(course.getId());
        version.setVersionNo(versionNo);
        version.setPublishedAt(LocalDateTime.now(ZoneOffset.UTC));
        version.setSnapshot(SimpleJson.write(snapshot));
        courseVersionMapper.insert(version);
        return versionNo;
    }

    public long latestVersionNo(Long courseId) {
        CourseVersion latest = courseVersionMapper.selectOne(Wrappers.<CourseVersion>lambdaQuery()
                .eq(CourseVersion::getCourseId, courseId)
                .orderByDesc(CourseVersion::getVersionNo)
                .last("LIMIT 1"));
        return latest == null ? 0 : latest.getVersionNo();
    }
}