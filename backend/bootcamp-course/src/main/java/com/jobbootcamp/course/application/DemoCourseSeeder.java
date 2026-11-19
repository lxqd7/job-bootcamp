package com.jobbootcamp.course.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.course.domain.Category;
import com.jobbootcamp.course.domain.Course;
import com.jobbootcamp.course.domain.dto.AdminChapterView;
import com.jobbootcamp.course.domain.dto.AdminCourseItem;
import com.jobbootcamp.course.domain.dto.CategoryCreateRequest;
import com.jobbootcamp.course.domain.dto.CategoryItem;
import com.jobbootcamp.course.domain.dto.ChapterRequest;
import com.jobbootcamp.course.domain.dto.CourseCreateRequest;
import com.jobbootcamp.course.domain.dto.LessonRequest;
import com.jobbootcamp.course.infrastructure.CategoryMapper;
import com.jobbootcamp.course.infrastructure.CourseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.demo.seed-enabled", havingValue = "true", matchIfMissing = true)
public class DemoCourseSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoCourseSeeder.class);

    private final CategoryMapper categoryMapper;
    private final CategoryService categoryService;
    private final CourseMapper courseMapper;
    private final CourseService courseService;

    public DemoCourseSeeder(CategoryMapper categoryMapper,
                            CategoryService categoryService,
                            CourseMapper courseMapper,
                            CourseService courseService) {
        this.categoryMapper = categoryMapper;
        this.categoryService = categoryService;
        this.courseMapper = courseMapper;
        this.courseService = courseService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long javaCategory = ensureCategory("Java", 0L, 1);
        Long basics = ensureCategory("基础语法", javaCategory, 1);
        Long concurrency = ensureCategory("并发编程", javaCategory, 2);

        Long courseCount = courseMapper.selectCount(Wrappers.<Course>lambdaQuery());
        if (courseCount != null && courseCount > 0) {
            return;
        }

        AdminCourseItem freeCourse = courseService.create(new CourseCreateRequest(
                "Java 入门体验",
                "免费体验课程，用于验证目录、课程详情与学习入口。",
                basics,
                0L,
                true,
                null));
        AdminChapterView chapter1 = courseService.createChapter(freeCourse.id() == null ? 0 : Long.parseLong(freeCourse.id()),
                new ChapterRequest("开始学习", 1, null));
        courseService.createLesson(Long.parseLong(chapter1.id()),
                new LessonRequest("课程介绍", 1, null, null));
        courseService.createLesson(Long.parseLong(chapter1.id()),
                new LessonRequest("环境准备", 2, null, null));
        AdminChapterView chapter2 = courseService.createChapter(Long.parseLong(freeCourse.id()),
                new ChapterRequest("第一个程序", 2, null));
        courseService.createLesson(Long.parseLong(chapter2.id()),
                new LessonRequest("Hello World", 1, null, null));
        long freeRevision = courseService.adminDetail(Long.parseLong(freeCourse.id())).revision();
        courseService.publish(Long.parseLong(freeCourse.id()), freeRevision);

        AdminCourseItem paidCourse = courseService.create(new CourseCreateRequest(
                "Java 并发实战",
                "付费课程示例，发布前需要至少一个有效视频小节。",
                concurrency,
                19900L,
                false,
                null));
        AdminChapterView paidChapter = courseService.createChapter(Long.parseLong(paidCourse.id()),
                new ChapterRequest("线程基础", 1, null));
        courseService.createLesson(Long.parseLong(paidChapter.id()),
                new LessonRequest("线程与进程", 1, null, null));

        log.info("Demo courses ready: free course {} (ONLINE), paid course {} (DRAFT)",
                freeCourse.id(), paidCourse.id());
    }

    private Long ensureCategory(String name, Long parentId, int sort) {
        Category existing = categoryMapper.selectOne(Wrappers.<Category>lambdaQuery()
                .eq(Category::getName, name)
                .eq(Category::getParentId, parentId)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing.getId();
        }
        CategoryItem item = categoryService.create(new CategoryCreateRequest(parentId, name, sort, true));
        return Long.parseLong(item.id());
    }
}