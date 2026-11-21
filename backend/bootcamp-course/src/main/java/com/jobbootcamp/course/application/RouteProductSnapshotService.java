package com.jobbootcamp.course.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.ProductSnapshotResponse;
import com.jobbootcamp.contracts.internal.RouteSnapshotResponse;
import com.jobbootcamp.course.domain.Course;
import com.jobbootcamp.course.domain.LearningRoute;
import com.jobbootcamp.course.domain.Lesson;
import com.jobbootcamp.course.domain.MediaAsset;
import com.jobbootcamp.course.domain.RouteMemberCurrent;
import com.jobbootcamp.course.infrastructure.CategoryClosureMapper;
import com.jobbootcamp.course.infrastructure.CourseMapper;
import com.jobbootcamp.course.infrastructure.LearningRouteMapper;
import com.jobbootcamp.course.infrastructure.LessonMapper;
import com.jobbootcamp.course.infrastructure.MediaAssetMapper;
import com.jobbootcamp.course.infrastructure.RouteMemberCurrentMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 路线报价快照：取当前路线版本成员、epoch、课程版本、分类祖先和活动小节，
 * 并按路线售价分摊出每门课在套餐中的折后价值。已拥有的课程仍以该分摊价抵扣。
 */
@Service
public class RouteProductSnapshotService {

    private final LearningRouteMapper routeMapper;
    private final RouteMemberCurrentMapper currentMapper;
    private final CourseMapper courseMapper;
    private final LessonMapper lessonMapper;
    private final MediaAssetMapper mediaMapper;
    private final CategoryClosureMapper categoryClosureMapper;
    private final CourseVersionService courseVersionService;

    public RouteProductSnapshotService(LearningRouteMapper routeMapper,
                                       RouteMemberCurrentMapper currentMapper,
                                       CourseMapper courseMapper,
                                       LessonMapper lessonMapper,
                                       MediaAssetMapper mediaMapper,
                                       CategoryClosureMapper categoryClosureMapper,
                                       CourseVersionService courseVersionService) {
        this.routeMapper = routeMapper;
        this.currentMapper = currentMapper;
        this.courseMapper = courseMapper;
        this.lessonMapper = lessonMapper;
        this.mediaMapper = mediaMapper;
        this.categoryClosureMapper = categoryClosureMapper;
        this.courseVersionService = courseVersionService;
    }

    public RouteSnapshotResponse routeSnapshot(Long routeId) {
        LearningRoute route = routeMapper.selectById(routeId);
        if (route == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "路线不存在");
        }
        List<RouteMemberCurrent> currents = currentMapper.selectByRoute(routeId);
        if (currents.isEmpty()) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "路线没有当前成员");
        }
        Map<Long, Course> courses = new LinkedHashMap<>();
        List<RouteAllocation.Weight> weights = new ArrayList<>();
        for (RouteMemberCurrent current : currents) {
            Course course = courseMapper.selectById(current.getCourseId());
            if (course == null) {
                throw new BusinessException(ErrorCode.DATA_INTEGRITY, "路线成员课程不存在");
            }
            courses.put(course.getId(), course);
            if (!Boolean.TRUE.equals(course.getIsFree())) {
                weights.add(new RouteAllocation.Weight(course.getId(), course.getPriceCent()));
            }
        }
        Map<Long, Long> allocations = new HashMap<>();
        if (!weights.isEmpty()) {
            for (RouteAllocation.Share share : RouteAllocation.allocate(route.getPriceCent(), weights)) {
                allocations.put(share.courseId(), share.allocatedCent());
            }
        }
        List<RouteSnapshotResponse.Member> members = new ArrayList<>();
        for (RouteMemberCurrent current : currents) {
            Course course = courses.get(current.getCourseId());
            if (course == null) {
                continue;
            }
            List<Lesson> lessons = lessonMapper.selectList(Wrappers.<Lesson>lambdaQuery()
                    .eq(Lesson::getCourseId, course.getId())
                    .eq(Lesson::getDeleted, false)
                    .orderByAsc(Lesson::getSort)
                    .orderByAsc(Lesson::getId));
            List<Long> mediaIds = lessons.stream().map(Lesson::getMediaId)
                    .filter(Objects::nonNull).distinct().toList();
            Map<Long, MediaAsset> mediaById = new LinkedHashMap<>();
            if (!mediaIds.isEmpty()) {
                mediaMapper.selectBatchIds(mediaIds).forEach(media -> mediaById.put(media.getId(), media));
            }
            List<ProductSnapshotResponse.SnapshotLesson> lessonSnapshots = lessons.stream()
                    .map(lesson -> new ProductSnapshotResponse.SnapshotLesson(
                            lesson.getId(), lesson.getChapterId(), lesson.getTitle(),
                            lesson.getSort() == null ? 0 : lesson.getSort(), lesson.getMediaId(),
                            duration(mediaById.get(lesson.getMediaId()))))
                    .toList();
            members.add(new RouteSnapshotResponse.Member(
                    course.getId(),
                    course.getTitle(),
                    courseVersionService.latestVersionNo(course.getId()),
                    current.getEpochId(),
                    course.getPriceCent(),
                    Boolean.TRUE.equals(course.getIsFree()) ? 0L : allocations.getOrDefault(course.getId(), 0L),
                    Boolean.TRUE.equals(course.getIsFree()),
                    course.getCategoryId(),
                    categoryClosureMapper.selectAncestorIds(course.getCategoryId()),
                    lessonSnapshots));
        }
        return new RouteSnapshotResponse(route.getId(), route.getTitle(), route.getDescription(),
                route.getCurrentVersionNo(), route.getPriceCent(), route.getStatus(), members);
    }

    private Long duration(MediaAsset media) {
        return media == null ? null : media.getDurationMs();
    }
}