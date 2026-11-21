package com.jobbootcamp.course.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ErrorData;
import com.jobbootcamp.course.domain.Chapter;
import com.jobbootcamp.course.domain.Course;
import com.jobbootcamp.course.domain.Lesson;
import com.jobbootcamp.course.domain.dto.AdminChapterView;
import com.jobbootcamp.course.domain.dto.AdminCourseDetail;
import com.jobbootcamp.course.domain.dto.AdminCourseItem;
import com.jobbootcamp.course.domain.dto.AdminLessonView;
import com.jobbootcamp.course.domain.dto.ChapterRequest;
import com.jobbootcamp.course.domain.dto.ChapterView;
import com.jobbootcamp.course.domain.dto.CourseCreateRequest;
import com.jobbootcamp.course.domain.dto.CourseDetail;
import com.jobbootcamp.course.domain.dto.CourseSummary;
import com.jobbootcamp.course.domain.dto.CourseUpdateRequest;
import com.jobbootcamp.course.domain.dto.LessonRequest;
import com.jobbootcamp.course.domain.dto.LessonView;
import com.jobbootcamp.course.domain.dto.PageResponse;
import com.jobbootcamp.course.infrastructure.ChapterMapper;
import com.jobbootcamp.course.infrastructure.CourseMapper;
import com.jobbootcamp.course.infrastructure.LessonMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课程/章节/小节的写模型。上架课程的任何有效目录变更都会递增 catalog_version
 * 并发布不可变版本；删除只做逻辑删除，历史订单和学习记录仍可引用旧小节。
 */
@Service
public class CourseService {

    private static final String DRAFT = "DRAFT";
    private static final String ONLINE = "ONLINE";
    private static final String OFFLINE = "OFFLINE";

    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final LessonMapper lessonMapper;
    private final CategoryService categoryService;
    private final CourseVersionService courseVersionService;
    private final RouteService routeService;
    private final MediaService mediaService;

    public CourseService(CourseMapper courseMapper,
                         ChapterMapper chapterMapper,
                         LessonMapper lessonMapper,
                         CategoryService categoryService,
                         CourseVersionService courseVersionService,
                         RouteService routeService,
                         MediaService mediaService) {
        this.courseMapper = courseMapper;
        this.chapterMapper = chapterMapper;
        this.lessonMapper = lessonMapper;
        this.categoryService = categoryService;
        this.courseVersionService = courseVersionService;
        this.routeService = routeService;
        this.mediaService = mediaService;
    }

    public PageResponse<CourseSummary> publicList(String keyword, Long categoryId, int pageNumber, int pageSize) {
        LambdaQueryWrapper<Course> query = Wrappers.<Course>lambdaQuery()
                .eq(Course::getStatus, ONLINE);
        applyKeyword(query, keyword);
        applyCategory(query, categoryId);
        if (categoryId != null && categoryService.descendantIds(categoryId).isEmpty()) {
            return new PageResponse<>(List.of(), 0, 1, pageSize);
        }
        query.orderByDesc(Course::getId);
        Page<Course> result = courseMapper.selectPage(page(pageNumber, pageSize), query);
        return new PageResponse<>(result.getRecords().stream().map(this::toSummary).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    public PageResponse<AdminCourseItem> adminList(String status, String keyword, int pageNumber, int pageSize) {
        LambdaQueryWrapper<Course> query = Wrappers.<Course>lambdaQuery();
        if (status != null && !status.isBlank()) {
            query.eq(Course::getStatus, status);
        }
        applyKeyword(query, keyword);
        query.orderByDesc(Course::getId);
        Page<Course> result = courseMapper.selectPage(page(pageNumber, pageSize), query);
        return new PageResponse<>(result.getRecords().stream().map(this::toAdminItem).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    public CourseDetail publicDetail(Long id) {
        Course course = requireCourse(id);
        if (!ONLINE.equals(course.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在或未上架");
        }
        List<Chapter> chapters = chapterMapper.selectList(Wrappers.<Chapter>lambdaQuery()
                .eq(Chapter::getCourseId, id)
                .eq(Chapter::getDeleted, false)
                .orderByAsc(Chapter::getSort)
                .orderByAsc(Chapter::getId));
        List<Lesson> lessons = lessonMapper.selectList(Wrappers.<Lesson>lambdaQuery()
                .eq(Lesson::getCourseId, id)
                .eq(Lesson::getDeleted, false)
                .orderByAsc(Lesson::getSort)
                .orderByAsc(Lesson::getId));
        Map<Long, List<Lesson>> byChapter = new LinkedHashMap<>();
        for (Lesson lesson : lessons) {
            byChapter.computeIfAbsent(lesson.getChapterId(), key -> new ArrayList<>()).add(lesson);
        }
        List<ChapterView> chapterViews = chapters.stream()
                .map(chapter -> new ChapterView(
                        String.valueOf(chapter.getId()),
                        chapter.getTitle(),
                        chapter.getSort(),
                        byChapter.getOrDefault(chapter.getId(), List.of()).stream()
                                .map(lesson -> new LessonView(
                                        String.valueOf(lesson.getId()),
                                        lesson.getTitle(),
                                        lesson.getSort(),
                                        lesson.getMediaId() != null))
                                .toList()))
                .toList();
        return new CourseDetail(
                String.valueOf(course.getId()),
                course.getTitle(),
                course.getDescription(),
                course.getPriceCent(),
                Boolean.TRUE.equals(course.getIsFree()),
                course.getStatus(),
                String.valueOf(course.getCategoryId()),
                course.getCoverUrl(),
                course.getCatalogVersion(),
                false,
                chapterViews);
    }

    public AdminCourseDetail adminDetail(Long id) {
        Course course = requireCourse(id);
        List<Chapter> chapters = chapterMapper.selectList(Wrappers.<Chapter>lambdaQuery()
                .eq(Chapter::getCourseId, id)
                .eq(Chapter::getDeleted, false)
                .orderByAsc(Chapter::getSort)
                .orderByAsc(Chapter::getId));
        List<Lesson> lessons = lessonMapper.selectList(Wrappers.<Lesson>lambdaQuery()
                .eq(Lesson::getCourseId, id)
                .eq(Lesson::getDeleted, false)
                .orderByAsc(Lesson::getSort)
                .orderByAsc(Lesson::getId));
        Map<Long, List<Lesson>> byChapter = new LinkedHashMap<>();
        for (Lesson lesson : lessons) {
            byChapter.computeIfAbsent(lesson.getChapterId(), key -> new ArrayList<>()).add(lesson);
        }
        List<AdminChapterView> chapterViews = chapters.stream()
                .map(chapter -> new AdminChapterView(
                        String.valueOf(chapter.getId()),
                        chapter.getTitle(),
                        chapter.getSort(),
                        chapter.getRevision(),
                        byChapter.getOrDefault(chapter.getId(), List.of()).stream()
                                .map(lesson -> new AdminLessonView(
                                        String.valueOf(lesson.getId()),
                                        lesson.getTitle(),
                                        lesson.getSort(),
                                        lesson.getMediaId() == null ? null : String.valueOf(lesson.getMediaId()),
                                        lesson.getRevision()))
                                .toList()))
                .toList();
        return new AdminCourseDetail(
                String.valueOf(course.getId()),
                course.getTitle(),
                course.getDescription(),
                course.getPriceCent(),
                Boolean.TRUE.equals(course.getIsFree()),
                course.getStatus(),
                String.valueOf(course.getCategoryId()),
                course.getCoverUrl(),
                course.getCatalogVersion(),
                course.getRevision(),
                chapterViews);
    }

    @Transactional
    public AdminCourseItem create(CourseCreateRequest request) {
        validateCourseInput(request.categoryId(), request.priceCent(), request.isFree());
        LocalDateTime now = utcNow();
        Course course = new Course();
        course.setTitle(request.title().trim());
        course.setDescription(request.description() == null ? "" : request.description());
        course.setCategoryId(request.categoryId());
        course.setIsFree(request.isFree());
        course.setPriceCent(request.isFree() ? 0L : request.priceCent());
        course.setStatus(DRAFT);
        course.setCoverUrl(request.coverUrl());
        course.setCatalogVersion(0L);
        course.setCreatedAt(now);
        course.setUpdatedAt(now);
        course.setRevision(0L);
        courseMapper.insert(course);
        return toAdminItem(course);
    }

    @Transactional
    public AdminCourseItem update(Long id, CourseUpdateRequest request) {
        Course current = requireCourse(id);
        validateCourseInput(request.categoryId(), request.priceCent(), request.isFree());
        if (ONLINE.equals(current.getStatus())) {
            routeService.validateCoursePriceChange(id,
                    Boolean.TRUE.equals(request.isFree()) ? 0 : request.priceCent(),
                    Boolean.TRUE.equals(request.isFree()));
        }
        LocalDateTime now = utcNow();
        int updated = courseMapper.update(null, Wrappers.<Course>lambdaUpdate()
                .set(Course::getTitle, request.title().trim())
                .set(Course::getDescription, request.description() == null ? "" : request.description())
                .set(Course::getCategoryId, request.categoryId())
                .set(Course::getIsFree, request.isFree())
                .set(Course::getPriceCent, request.isFree() ? 0L : request.priceCent())
                .set(Course::getCoverUrl, request.coverUrl())
                .set(Course::getUpdatedAt, now)
                .setSql("revision = revision + 1")
                .eq(Course::getId, id)
                .eq(Course::getRevision, request.revision()));
        if (updated == 0) {
            Long currentRevision = courseMapper.selectById(id).getRevision();
            throw new BusinessException(ErrorCode.CONFLICT, "课程已在其他位置更新，请刷新后重试",
                    new ErrorData(null, currentRevision, null));
        }
        Course saved = courseMapper.selectById(id);
        if (ONLINE.equals(saved.getStatus())) {
            // 已上架课程的改价/改目录立即生成新版本，并触发关联路线重算校验。
            courseVersionService.publish(saved);
            routeService.refreshRoutesForCourse(id);
        }
        return toAdminItem(courseMapper.selectById(id));
    }

    @Transactional
    public AdminCourseItem publish(Long id, Long revision) {
        Course course = requireCourse(id);
        // 付费课发布必须有可播放媒体；免费课允许先发布再补视频。
        if (Boolean.FALSE.equals(course.getIsFree())) {
            List<Long> mediaIds = lessonMapper.selectList(Wrappers.<Lesson>lambdaQuery()
                            .eq(Lesson::getCourseId, id)
                            .eq(Lesson::getDeleted, false)
                            .isNotNull(Lesson::getMediaId))
                    .stream().map(Lesson::getMediaId).distinct().toList();
            if (mediaService.availableCount(mediaIds) == 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "付费课程发布前需要至少一个有效且可播放的视频小节");
            }
        }
        updateStatus(id, revision, ONLINE);
        Course online = courseMapper.selectById(id);
        courseVersionService.publish(online);
        return toAdminItem(courseMapper.selectById(id));
    }

    @Transactional
    public AdminCourseItem offline(Long id, Long revision) {
        requireCourse(id);
        updateStatus(id, revision, OFFLINE);
        return toAdminItem(courseMapper.selectById(id));
    }

    @Transactional
    public AdminChapterView createChapter(Long courseId, ChapterRequest request) {
        requireCourse(courseId);
        LocalDateTime now = utcNow();
        Chapter chapter = new Chapter();
        chapter.setCourseId(courseId);
        chapter.setTitle(request.title().trim());
        chapter.setSort(request.sort() == null ? 0 : request.sort());
        chapter.setDeleted(false);
        chapter.setCreatedAt(now);
        chapter.setUpdatedAt(now);
        chapter.setRevision(0L);
        chapterMapper.insert(chapter);
        touchCatalog(courseId);
        return new AdminChapterView(String.valueOf(chapter.getId()), chapter.getTitle(), chapter.getSort(),
                chapter.getRevision(), List.of());
    }

    @Transactional
    public AdminChapterView updateChapter(Long courseId, Long chapterId, ChapterRequest request) {
        Chapter chapter = requireChapter(courseId, chapterId);
        var update = Wrappers.<Chapter>lambdaUpdate()
                .set(Chapter::getTitle, request.title().trim())
                .set(Chapter::getSort, request.sort() == null ? chapter.getSort() : request.sort())
                .set(Chapter::getUpdatedAt, utcNow())
                .setSql("revision = revision + 1")
                .eq(Chapter::getId, chapterId)
                .eq(Chapter::getDeleted, false);
        if (request.revision() != null) {
            update.eq(Chapter::getRevision, request.revision());
        }
        int updated = chapterMapper.update(null, update);
        if (updated == 0) {
            throw conflict("章节", chapterMapper.selectById(chapterId));
        }
        touchCatalog(courseId);
        Chapter updatedChapter = chapterMapper.selectById(chapterId);
        return new AdminChapterView(String.valueOf(updatedChapter.getId()), updatedChapter.getTitle(),
                updatedChapter.getSort(), updatedChapter.getRevision(), List.of());
    }

    @Transactional
    public void deleteChapter(Long courseId, Long chapterId, Long revision) {
        requireChapter(courseId, chapterId);
        var update = Wrappers.<Chapter>lambdaUpdate()
                .set(Chapter::getDeleted, true)
                .set(Chapter::getUpdatedAt, utcNow())
                .setSql("revision = revision + 1")
                .eq(Chapter::getId, chapterId);
        if (revision != null) {
            update.eq(Chapter::getRevision, revision);
        }
        if (chapterMapper.update(null, update) == 0) {
            throw conflict("章节", chapterMapper.selectById(chapterId));
        }
        touchCatalog(courseId);
    }

    @Transactional
    public AdminLessonView createLesson(Long courseId, Long chapterId, LessonRequest request) {
        requireChapter(courseId, chapterId);
        LocalDateTime now = utcNow();
        Lesson lesson = new Lesson();
        lesson.setCourseId(courseId);
        lesson.setChapterId(chapterId);
        lesson.setTitle(request.title().trim());
        lesson.setSort(request.sort() == null ? 0 : request.sort());
        lesson.setMediaId(request.mediaId());
        lesson.setType("VIDEO");
        lesson.setDeleted(false);
        lesson.setCreatedAt(now);
        lesson.setUpdatedAt(now);
        lesson.setRevision(0L);
        lessonMapper.insert(lesson);
        touchCatalog(courseId);
        return toAdminLesson(lesson);
    }

    @Transactional
    public AdminLessonView updateLesson(Long courseId, Long chapterId, Long lessonId, LessonRequest request) {
        Lesson lesson = requireLesson(courseId, chapterId, lessonId);
        var update = Wrappers.<Lesson>lambdaUpdate()
                .set(Lesson::getTitle, request.title().trim())
                .set(Lesson::getSort, request.sort() == null ? lesson.getSort() : request.sort())
                .set(Lesson::getMediaId, request.mediaId())
                .set(Lesson::getUpdatedAt, utcNow())
                .setSql("revision = revision + 1")
                .eq(Lesson::getId, lessonId)
                .eq(Lesson::getDeleted, false);
        if (request.revision() != null) {
            update.eq(Lesson::getRevision, request.revision());
        }
        if (lessonMapper.update(null, update) == 0) {
            throw conflict("小节", lessonMapper.selectById(lessonId));
        }
        touchCatalog(courseId);
        return toAdminLesson(lessonMapper.selectById(lessonId));
    }

    @Transactional
    public void deleteLesson(Long courseId, Long chapterId, Long lessonId, Long revision) {
        requireLesson(courseId, chapterId, lessonId);
        var update = Wrappers.<Lesson>lambdaUpdate()
                .set(Lesson::getDeleted, true)
                .set(Lesson::getUpdatedAt, utcNow())
                .setSql("revision = revision + 1")
                .eq(Lesson::getId, lessonId);
        if (revision != null) {
            update.eq(Lesson::getRevision, revision);
        }
        if (lessonMapper.update(null, update) == 0) {
            throw conflict("小节", lessonMapper.selectById(lessonId));
        }
        touchCatalog(courseId);
    }

    private BusinessException conflict(String type, Object entity) {
        return new BusinessException(ErrorCode.CONFLICT, type + "已在其他位置更新，请刷新后重试");
    }

    private void validateCourseInput(Long categoryId, Long priceCent, Boolean isFree) {
        if (!categoryService.exists(categoryId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "课程分类不存在");
        }
        if (Boolean.TRUE.equals(isFree)) {
            return;
        }
        if (priceCent == null || priceCent < 990) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "付费课程价格不能低于 9.9 元");
        }
    }

    private void applyKeyword(LambdaQueryWrapper<Course> query, String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            query.and(wrapper -> wrapper.like(Course::getTitle, value)
                    .or()
                    .like(Course::getDescription, value));
        }
    }

    private void applyCategory(LambdaQueryWrapper<Course> query, Long categoryId) {
        if (categoryId != null) {
            List<Long> ids = categoryService.descendantIds(categoryId);
            if (!ids.isEmpty()) {
                query.in(Course::getCategoryId, ids);
            }
        }
    }

    private void updateStatus(Long id, Long revision, String status) {
        int updated = courseMapper.update(null, Wrappers.<Course>lambdaUpdate()
                .set(Course::getStatus, status)
                .set(Course::getUpdatedAt, utcNow())
                .setSql("revision = revision + 1")
                .eq(Course::getId, id)
                .eq(Course::getRevision, revision));
        if (updated == 0) {
            Long currentRevision = courseMapper.selectById(id).getRevision();
            throw new BusinessException(ErrorCode.CONFLICT, "课程已在其他位置更新，请刷新后重试",
                    new ErrorData(null, currentRevision, null));
        }
    }

    private void touchCatalog(Long courseId) {
        courseMapper.update(null, Wrappers.<Course>lambdaUpdate()
                .setSql("catalog_version = catalog_version + 1")
                .set(Course::getUpdatedAt, utcNow())
                .eq(Course::getId, courseId));
        Course course = courseMapper.selectById(courseId);
        if (course != null && ONLINE.equals(course.getStatus())) {
            // 线上目录变更立即发布快照，再让路线重新校验分摊；失败由外层事务整体回滚。
            courseVersionService.publish(course);
            routeService.refreshRoutesForCourse(courseId);
        }
    }

    public void deleteChapter(Long chapterId, Long revision) {
        Chapter chapter = requireChapterById(chapterId);
        deleteChapter(chapter.getCourseId(), chapterId, revision);
    }

    public AdminLessonView createLesson(Long chapterId, LessonRequest request) {
        Chapter chapter = requireChapterById(chapterId);
        return createLesson(chapter.getCourseId(), chapterId, request);
    }

    public AdminLessonView updateLesson(Long chapterId, Long lessonId, LessonRequest request) {
        Lesson lesson = requireLessonById(chapterId, lessonId);
        return updateLesson(lesson.getCourseId(), chapterId, lessonId, request);
    }

    public void deleteLesson(Long chapterId, Long lessonId, Long revision) {
        Lesson lesson = requireLessonById(chapterId, lessonId);
        deleteLesson(lesson.getCourseId(), chapterId, lessonId, revision);
    }

    public void deleteLessonByLessonId(Long lessonId, Long revision) {
        Lesson lesson = lessonMapper.selectById(lessonId);
        if (lesson == null || Boolean.TRUE.equals(lesson.getDeleted())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "小节不存在");
        }
        deleteLesson(lesson.getCourseId(), lesson.getChapterId(), lessonId, revision);
    }

    private Chapter requireChapterById(Long chapterId) {
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null || Boolean.TRUE.equals(chapter.getDeleted())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "章节不存在");
        }
        return chapter;
    }

    private Lesson requireLessonById(Long chapterId, Long lessonId) {
        Lesson lesson = lessonMapper.selectById(lessonId);
        if (lesson == null
                || !lesson.getChapterId().equals(chapterId)
                || Boolean.TRUE.equals(lesson.getDeleted())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "小节不存在");
        }
        return lesson;
    }

    private Course requireCourse(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        return course;
    }

    private Chapter requireChapter(Long courseId, Long chapterId) {
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null || !chapter.getCourseId().equals(courseId) || Boolean.TRUE.equals(chapter.getDeleted())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "章节不存在");
        }
        return chapter;
    }

    private Lesson requireLesson(Long courseId, Long chapterId, Long lessonId) {
        Lesson lesson = lessonMapper.selectById(lessonId);
        if (lesson == null
                || !lesson.getCourseId().equals(courseId)
                || !lesson.getChapterId().equals(chapterId)
                || Boolean.TRUE.equals(lesson.getDeleted())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "小节不存在");
        }
        return lesson;
    }

    private Page<Course> page(int pageNumber, int pageSize) {
        return new Page<>(Math.max(pageNumber, 1), Math.min(Math.max(pageSize, 1), 100));
    }

    private CourseSummary toSummary(Course course) {
        return new CourseSummary(
                String.valueOf(course.getId()),
                course.getTitle(),
                course.getCoverUrl(),
                course.getPriceCent(),
                Boolean.TRUE.equals(course.getIsFree()),
                course.getStatus(),
                String.valueOf(course.getCategoryId()));
    }

    private AdminCourseItem toAdminItem(Course course) {
        return new AdminCourseItem(
                String.valueOf(course.getId()),
                course.getTitle(),
                course.getCoverUrl(),
                course.getPriceCent(),
                Boolean.TRUE.equals(course.getIsFree()),
                course.getStatus(),
                String.valueOf(course.getCategoryId()),
                course.getCatalogVersion(),
                course.getRevision());
    }

    private AdminLessonView toAdminLesson(Lesson lesson) {
        return new AdminLessonView(
                String.valueOf(lesson.getId()),
                lesson.getTitle(),
                lesson.getSort(),
                lesson.getMediaId() == null ? null : String.valueOf(lesson.getMediaId()),
                lesson.getRevision());
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}