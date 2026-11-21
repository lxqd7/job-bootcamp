package com.jobbootcamp.course.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ErrorData;
import com.jobbootcamp.course.domain.Course;
import com.jobbootcamp.course.domain.LearningRoute;
import com.jobbootcamp.course.domain.Lesson;
import com.jobbootcamp.course.domain.OutboxEvent;
import com.jobbootcamp.course.domain.RouteDraftMember;
import com.jobbootcamp.course.domain.RouteMemberCurrent;
import com.jobbootcamp.course.domain.RouteMemberEpoch;
import com.jobbootcamp.course.domain.RouteVersion;
import com.jobbootcamp.course.domain.dto.PageResponse;
import com.jobbootcamp.course.domain.dto.RouteCreateRequest;
import com.jobbootcamp.course.domain.dto.RouteDetail;
import com.jobbootcamp.course.domain.dto.RouteMemberRequest;
import com.jobbootcamp.course.domain.dto.RouteMemberView;
import com.jobbootcamp.course.domain.dto.RouteSummary;
import com.jobbootcamp.course.domain.dto.RouteUpdateRequest;
import com.jobbootcamp.course.infrastructure.CategoryClosureMapper;
import com.jobbootcamp.course.infrastructure.CourseMapper;
import com.jobbootcamp.course.infrastructure.LearningRouteMapper;
import com.jobbootcamp.course.infrastructure.LessonMapper;
import com.jobbootcamp.course.infrastructure.OutboxEventMapper;
import com.jobbootcamp.course.infrastructure.RouteDraftMemberMapper;
import com.jobbootcamp.course.infrastructure.RouteMemberCurrentMapper;
import com.jobbootcamp.course.infrastructure.RouteMemberEpochMapper;
import com.jobbootcamp.course.infrastructure.RouteVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 路线版本与成员 epoch。成员加入生成新 epoch，移除只关闭旧 epoch，不物理删除；
 * 每次发布生成不可变 route_version，并通过 outbox 通知学习/福利侧重放成员区间。
 */
@Service
public class RouteService {

    private static final String DRAFT = "DRAFT";
    private static final String ONLINE = "ONLINE";
    private static final String OFFLINE = "OFFLINE";

    private final LearningRouteMapper routeMapper;
    private final RouteVersionMapper routeVersionMapper;
    private final RouteMemberEpochMapper epochMapper;
    private final RouteMemberCurrentMapper currentMapper;
    private final RouteDraftMemberMapper draftMapper;
    private final CourseMapper courseMapper;
    private final LessonMapper lessonMapper;
    private final CategoryClosureMapper categoryClosureMapper;
    private final CourseVersionService courseVersionService;
    private final OutboxEventMapper outboxEventMapper;

    public RouteService(LearningRouteMapper routeMapper,
                        RouteVersionMapper routeVersionMapper,
                        RouteMemberEpochMapper epochMapper,
                        RouteMemberCurrentMapper currentMapper,
                        RouteDraftMemberMapper draftMapper,
                        CourseMapper courseMapper,
                        LessonMapper lessonMapper,
                        CategoryClosureMapper categoryClosureMapper,
                        CourseVersionService courseVersionService,
                        OutboxEventMapper outboxEventMapper) {
        this.routeMapper = routeMapper;
        this.routeVersionMapper = routeVersionMapper;
        this.epochMapper = epochMapper;
        this.currentMapper = currentMapper;
        this.draftMapper = draftMapper;
        this.courseMapper = courseMapper;
        this.lessonMapper = lessonMapper;
        this.categoryClosureMapper = categoryClosureMapper;
        this.courseVersionService = courseVersionService;
        this.outboxEventMapper = outboxEventMapper;
    }

    public PageResponse<RouteSummary> publicList(int pageNumber, int pageSize) {
        Page<LearningRoute> result = routeMapper.selectPage(page(pageNumber, pageSize),
                Wrappers.<LearningRoute>lambdaQuery()
                        .eq(LearningRoute::getStatus, ONLINE)
                        .orderByDesc(LearningRoute::getId));
        return new PageResponse<>(result.getRecords().stream().map(this::toSummary).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    public PageResponse<RouteSummary> adminList(String status, int pageNumber, int pageSize) {
        var query = Wrappers.<LearningRoute>lambdaQuery();
        if (status != null && !status.isBlank()) {
            query.eq(LearningRoute::getStatus, status);
        }
        query.orderByDesc(LearningRoute::getId);
        Page<LearningRoute> result = routeMapper.selectPage(page(pageNumber, pageSize), query);
        return new PageResponse<>(result.getRecords().stream().map(this::toSummary).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    public RouteDetail publicDetail(Long id) {
        LearningRoute route = requireRoute(id);
        if (!ONLINE.equals(route.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "路线不存在或未上架");
        }
        return detail(route, false);
    }

    public RouteDetail adminDetail(Long id) {
        return detail(requireRoute(id), true);
    }

    @Transactional
    public RouteSummary create(RouteCreateRequest request) {
        List<RouteMemberRequest> members = normalizeMembers(request.members());
        validateMembers(request.priceCent(), members, false);
        LocalDateTime now = utcNow();
        LearningRoute route = new LearningRoute();
        route.setTitle(request.title().trim());
        route.setDescription(request.description() == null ? "" : request.description());
        route.setPriceCent(request.priceCent());
        route.setStatus(DRAFT);
        route.setCurrentVersionNo(0L);
        route.setCreatedAt(now);
        route.setUpdatedAt(now);
        route.setRevision(0L);
        routeMapper.insert(route);
        replaceDraftMembers(route.getId(), members);
        return toSummary(route);
    }

    @Transactional
    public RouteSummary update(Long id, RouteUpdateRequest request) {
        LearningRoute route = requireRoute(id);
        List<RouteMemberRequest> members = normalizeMembers(request.members());
        boolean online = ONLINE.equals(route.getStatus());
        validateMembers(request.priceCent(), members, online);
        LocalDateTime now = utcNow();
        int updated = routeMapper.update(null, Wrappers.<LearningRoute>lambdaUpdate()
                .set(LearningRoute::getTitle, request.title().trim())
                .set(LearningRoute::getDescription, request.description() == null ? "" : request.description())
                .set(LearningRoute::getPriceCent, request.priceCent())
                .set(LearningRoute::getUpdatedAt, now)
                .setSql("revision = revision + 1")
                .eq(LearningRoute::getId, id)
                .eq(LearningRoute::getRevision, request.revision()));
        if (updated == 0) {
            Long currentRevision = routeMapper.selectById(id).getRevision();
            throw new BusinessException(ErrorCode.CONFLICT, "路线已在其他位置更新，请刷新后重试",
                    new ErrorData(null, currentRevision, null));
        }
        LearningRoute updatedRoute = routeMapper.selectById(id);
        if (online) {
            createVersion(updatedRoute, members, false);
        } else {
            replaceDraftMembers(id, members);
        }
        return toSummary(routeMapper.selectById(id));
    }

    @Transactional
    public RouteSummary publish(Long id, Long revision) {
        LearningRoute route = requireRoute(id);
        List<RouteMemberRequest> members = effectiveMembers(route);
        validateMembers(route.getPriceCent(), members, true);
        int updated = routeMapper.update(null, Wrappers.<LearningRoute>lambdaUpdate()
                .set(LearningRoute::getStatus, ONLINE)
                .set(LearningRoute::getUpdatedAt, utcNow())
                .setSql("revision = revision + 1")
                .eq(LearningRoute::getId, id)
                .eq(LearningRoute::getRevision, revision));
        if (updated == 0) {
            Long currentRevision = routeMapper.selectById(id).getRevision();
            throw new BusinessException(ErrorCode.CONFLICT, "路线已在其他位置更新，请刷新后重试",
                    new ErrorData(null, currentRevision, null));
        }
        LearningRoute onlineRoute = routeMapper.selectById(id);
        createVersion(onlineRoute, members, false);
        draftMapper.deleteByRoute(id);
        return toSummary(routeMapper.selectById(id));
    }

    @Transactional
    public RouteSummary offline(Long id, Long revision) {
        requireRoute(id);
        int updated = routeMapper.update(null, Wrappers.<LearningRoute>lambdaUpdate()
                .set(LearningRoute::getStatus, OFFLINE)
                .set(LearningRoute::getUpdatedAt, utcNow())
                .setSql("revision = revision + 1")
                .eq(LearningRoute::getId, id)
                .eq(LearningRoute::getRevision, revision));
        if (updated == 0) {
            Long currentRevision = routeMapper.selectById(id).getRevision();
            throw new BusinessException(ErrorCode.CONFLICT, "路线已在其他位置更新，请刷新后重试",
                    new ErrorData(null, currentRevision, null));
        }
        return toSummary(routeMapper.selectById(id));
    }

    /**
     * 课程改价前调用：若课程属于已上架路线，验证新价格不会让路线分摊失效。
     */
    public void validateCoursePriceChange(Long courseId, long newPriceCent, boolean newIsFree) {
        List<Long> routeIds = currentMapper.selectRouteIdsByCourse(courseId);
        for (Long routeId : routeIds) {
            LearningRoute route = routeMapper.selectById(routeId);
            if (route == null || !ONLINE.equals(route.getStatus())) {
                continue;
            }
            List<Course> courses = currentCourses(routeId, courseId, newPriceCent, newIsFree);
            try {
                validateCourses(route.getPriceCent(), courses, true);
            } catch (BusinessException e) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "课程改价会使路线 " + route.getId() + "（" + route.getTitle() + "）不合法：" + e.getMessage());
            }
        }
    }

    /**
     * 课程价格/目录变化后，为所有包含该课程的已上架路线创建新版本快照。
     */
    @Transactional
    public void refreshRoutesForCourse(Long courseId) {
        List<Long> routeIds = currentMapper.selectRouteIdsByCourse(courseId);
        for (Long routeId : routeIds) {
            LearningRoute route = routeMapper.selectById(routeId);
            if (route == null || !ONLINE.equals(route.getStatus())) {
                continue;
            }
            createVersion(route, currentMemberRequests(routeId), false);
        }
    }

    private RouteDetail detail(LearningRoute route, boolean admin) {
        boolean draft = !ONLINE.equals(route.getStatus()) && !draftMapper.selectByRoute(route.getId()).isEmpty();
        List<RouteMemberRequest> members = draft ? draftMemberRequests(route.getId()) : currentMemberRequests(route.getId());
        List<Course> courses = new ArrayList<>();
        Map<Long, Course> byId = new HashMap<>();
        for (RouteMemberRequest member : members) {
            Course course = courseMapper.selectById(member.courseId());
            if (course != null) {
                courses.add(course);
                byId.put(course.getId(), course);
            }
        }
        Map<Long, Long> allocations = new HashMap<>();
        if (!courses.isEmpty()) {
            try {
                allocations = validateCourses(route.getPriceCent(), courses, ONLINE.equals(route.getStatus()));
            } catch (BusinessException e) {
                allocations = new HashMap<>();
            }
        }
        Map<Long, Long> epochs = new HashMap<>();
        for (RouteMemberCurrent current : currentMapper.selectByRoute(route.getId())) {
            epochs.put(current.getCourseId(), current.getEpochId());
        }
        List<RouteMemberView> views = new ArrayList<>();
        for (RouteMemberRequest member : members) {
            Course course = byId.get(member.courseId());
            if (course == null) {
                continue;
            }
            long allocated = Boolean.TRUE.equals(course.getIsFree()) ? 0 : allocations.getOrDefault(course.getId(), 0L);
            views.add(new RouteMemberView(
                    String.valueOf(course.getId()),
                    course.getTitle(),
                    member.sort() == null ? 0 : member.sort(),
                    course.getPriceCent(),
                    Boolean.TRUE.equals(course.getIsFree()),
                    allocated,
                    draft || epochs.get(course.getId()) == null ? null : String.valueOf(epochs.get(course.getId()))));
        }
        return new RouteDetail(
                String.valueOf(route.getId()),
                route.getTitle(),
                route.getDescription(),
                route.getPriceCent(),
                route.getStatus(),
                route.getCurrentVersionNo(),
                route.getRevision(),
                draft,
                views);
    }

    private void createVersion(LearningRoute route, List<RouteMemberRequest> members, boolean unused) {
        long versionNo = route.getCurrentVersionNo() + 1;
        LocalDateTime now = utcNow();
        List<RouteMemberCurrent> current = currentMapper.selectByRoute(route.getId());
        Map<Long, RouteMemberCurrent> currentByCourse = new HashMap<>();
        for (RouteMemberCurrent member : current) {
            currentByCourse.put(member.getCourseId(), member);
        }
        Set<Long> requested = new HashSet<>();
        for (RouteMemberRequest member : members) {
            requested.add(member.courseId());
        }
        // 移除成员只关闭 epoch 并回到草稿差集，历史购买仍可按 [joinedAt, removedAt) 重放。
        List<String> removedEpochIds = new ArrayList<>();
        for (RouteMemberCurrent member : current) {
            if (!requested.contains(member.getCourseId())) {
                epochMapper.closeEpoch(route.getId(), member.getCourseId(), versionNo, now);
                currentMapper.deleteMember(route.getId(), member.getCourseId());
                removedEpochIds.add(String.valueOf(member.getEpochId()));
            }
        }
        List<Map<String, Object>> addedEpochs = new ArrayList<>();
        for (RouteMemberRequest member : members) {
            int sort = member.sort() == null ? 0 : member.sort();
            RouteMemberCurrent existing = currentByCourse.get(member.courseId());
            if (existing == null) {
                RouteMemberEpoch epoch = new RouteMemberEpoch();
                epoch.setRouteId(route.getId());
                epoch.setCourseId(member.courseId());
                epoch.setJoinedVersion(versionNo);
                epoch.setJoinedAt(now);
                epochMapper.insertEpoch(epoch);
                currentMapper.insertMember(route.getId(), member.courseId(), epoch.getId(), sort);
                Map<String, Object> added = new LinkedHashMap<>();
                added.put("courseId", String.valueOf(member.courseId()));
                added.put("epochId", String.valueOf(epoch.getId()));
                // 学习侧用 joinedAt 作为后续新增课的 grantedAt，不能回填成路线购买时间。
                added.put("joinedAt", now.toInstant(ZoneOffset.UTC).toString());
                Course joinedCourse = courseMapper.selectById(member.courseId());
                added.put("isFree", joinedCourse != null && Boolean.TRUE.equals(joinedCourse.getIsFree()));
                added.put("courseVersion", courseVersionService.latestVersionNo(member.courseId()));
                addedEpochs.add(added);
            } else {
                currentMapper.updateSort(route.getId(), member.courseId(), sort);
            }
        }
        Map<Long, Long> epochIds = new HashMap<>();
        for (RouteMemberCurrent member : currentMapper.selectByRoute(route.getId())) {
            epochIds.put(member.getCourseId(), member.getEpochId());
        }

        List<Course> courses = new ArrayList<>();
        for (RouteMemberRequest member : members) {
            Course course = courseMapper.selectById(member.courseId());
            if (course != null) {
                courses.add(course);
            }
        }
        Map<Long, Long> allocations = validateCourses(route.getPriceCent(), courses, true);

        List<Map<String, Object>> memberSnapshots = new ArrayList<>();
        List<RouteMemberRequest> sorted = new ArrayList<>(members);
        sorted.sort(Comparator
                .comparingInt((RouteMemberRequest member) -> member.sort() == null ? 0 : member.sort())
                .thenComparingLong(RouteMemberRequest::courseId));
        for (RouteMemberRequest member : sorted) {
            Course course = courseMapper.selectById(member.courseId());
            if (course == null) {
                continue;
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("courseId", String.valueOf(course.getId()));
            snapshot.put("epochId", String.valueOf(epochIds.get(course.getId())));
            snapshot.put("sort", member.sort() == null ? 0 : member.sort());
            snapshot.put("courseVersion", courseVersionService.latestVersionNo(course.getId()));
            snapshot.put("unitPriceCent", course.getPriceCent());
            snapshot.put("isFree", Boolean.TRUE.equals(course.getIsFree()));
            snapshot.put("allocatedCent", Boolean.TRUE.equals(course.getIsFree())
                    ? 0 : allocations.getOrDefault(course.getId(), 0L));
            snapshot.put("categoryAncestorIds", categoryClosureMapper.selectAncestorIds(course.getCategoryId())
                    .stream().map(String::valueOf).toList());
            List<Map<String, Object>> videos = new ArrayList<>();
            for (Lesson lesson : lessonMapper.selectList(Wrappers.<Lesson>lambdaQuery()
                    .eq(Lesson::getCourseId, course.getId())
                    .eq(Lesson::getDeleted, false)
                    .isNotNull(Lesson::getMediaId))) {
                Map<String, Object> video = new LinkedHashMap<>();
                video.put("lessonId", String.valueOf(lesson.getId()));
                video.put("mediaId", String.valueOf(lesson.getMediaId()));
                video.put("durationMs", null);
                videos.add(video);
            }
            snapshot.put("videoLessons", videos);
            memberSnapshots.add(snapshot);
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("routeId", String.valueOf(route.getId()));
        snapshot.put("versionNo", versionNo);
        snapshot.put("priceCent", route.getPriceCent());
        snapshot.put("members", memberSnapshots);

        RouteVersion version = new RouteVersion();
        version.setRouteId(route.getId());
        version.setVersionNo(versionNo);
        version.setEffectiveAt(now);
        version.setSnapshot(SimpleJson.write(snapshot));
        routeVersionMapper.insert(version);

        routeMapper.update(null, Wrappers.<LearningRoute>lambdaUpdate()
                .set(LearningRoute::getCurrentVersionNo, versionNo)
                .set(LearningRoute::getUpdatedAt, now)
                .eq(LearningRoute::getId, route.getId()));

        emitRouteChanged(route.getId(), versionNo, now, addedEpochs, removedEpochIds);
    }

    private void emitRouteChanged(Long routeId, long versionNo, LocalDateTime now,
                                  List<Map<String, Object>> addedEpochs, List<String> removedEpochIds) {
        // 事件只携带增量 epoch，不携带当前会员列表；消费方按成员区间重放，避免并发变更漏课。
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("routeId", String.valueOf(routeId));
        payload.put("version", versionNo);
        payload.put("effectiveAt", now.toInstant(ZoneOffset.UTC).toString());
        payload.put("addedEpochs", addedEpochs);
        payload.put("removedEpochIds", removedEpochIds);
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setType("course.route-changed.v1");
        event.setAggregateId(String.valueOf(routeId));
        event.setAggregateVersion(versionNo);
        event.setOccurredAt(now);
        event.setPayload(SimpleJson.write(payload));
        event.setStatus("NEW");
        event.setAttempt(0);
        event.setNextRetryAt(now);
        outboxEventMapper.insert(event);
    }

    private List<RouteMemberRequest> normalizeMembers(List<RouteMemberRequest> members) {
        Set<Long> seen = new HashSet<>();
        List<RouteMemberRequest> normalized = new ArrayList<>();
        int order = 0;
        for (RouteMemberRequest member : members) {
            if (!seen.add(member.courseId())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "路线成员不能重复");
            }
            if (courseMapper.selectById(member.courseId()) == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "路线成员课程不存在");
            }
            order++;
            normalized.add(new RouteMemberRequest(member.courseId(),
                    member.sort() == null ? order : member.sort()));
        }
        return normalized;
    }

    private void validateMembers(long routePrice, List<RouteMemberRequest> members, boolean requireOnline) {
        List<Course> courses = new ArrayList<>();
        for (RouteMemberRequest member : members) {
            Course course = courseMapper.selectById(member.courseId());
            if (course == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "路线成员课程不存在");
            }
            courses.add(course);
        }
        validateCourses(routePrice, courses, requireOnline);
    }

    private Map<Long, Long> validateCourses(long routePrice, List<Course> courses, boolean requireOnline) {
        if (courses.size() < 2) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "路线至少需要两门课程");
        }
        long paidSum = 0;
        List<RouteAllocation.Weight> weights = new ArrayList<>();
        for (Course course : courses) {
            if (requireOnline && !ONLINE.equals(course.getStatus())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "路线成员课程必须已上架：" + course.getTitle());
            }
            if (!Boolean.TRUE.equals(course.getIsFree())) {
                paidSum += course.getPriceCent();
                weights.add(new RouteAllocation.Weight(course.getId(), course.getPriceCent()));
            }
        }
        if (weights.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "路线至少需要一门付费课程");
        }
        if (routePrice <= 0 || routePrice >= paidSum) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "路线价格必须大于 0 且低于付费课程单卖价之和");
        }
        List<RouteAllocation.Share> shares = RouteAllocation.allocate(routePrice, weights);
        Map<Long, Long> allocations = new HashMap<>();
        for (RouteAllocation.Share share : shares) {
            // 每门付费课至少分摊 1 元，避免出现 0 元抵扣或异常退款基准。
            if (share.allocatedCent() < 100) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "每门付费课程在套餐中的分摊至少 1 元，课程 " + share.courseId() + " 当前为 "
                                + share.allocatedCent() + " 分");
            }
            allocations.put(share.courseId(), share.allocatedCent());
        }
        return allocations;
    }

    private List<Course> currentCourses(Long routeId, Long overrideCourseId, long newPrice, boolean newIsFree) {
        List<Course> courses = new ArrayList<>();
        for (RouteMemberCurrent member : currentMapper.selectByRoute(routeId)) {
            Course course = courseMapper.selectById(member.getCourseId());
            if (course == null) {
                continue;
            }
            if (course.getId().equals(overrideCourseId)) {
                course = copyWithPrice(course, newPrice, newIsFree);
            }
            courses.add(course);
        }
        return courses;
    }

    private Course copyWithPrice(Course source, long newPrice, boolean newIsFree) {
        Course copy = new Course();
        copy.setId(source.getId());
        copy.setTitle(source.getTitle());
        copy.setCategoryId(source.getCategoryId());
        copy.setPriceCent(newIsFree ? 0 : newPrice);
        copy.setIsFree(newIsFree);
        copy.setStatus(source.getStatus());
        return copy;
    }

    private List<RouteMemberRequest> effectiveMembers(LearningRoute route) {
        List<RouteDraftMember> drafts = draftMapper.selectByRoute(route.getId());
        if (!drafts.isEmpty()) {
            return drafts.stream()
                    .map(member -> new RouteMemberRequest(member.getCourseId(), member.getSort()))
                    .toList();
        }
        return currentMemberRequests(route.getId());
    }

    private List<RouteMemberRequest> currentMemberRequests(Long routeId) {
        return currentMapper.selectByRoute(routeId).stream()
                .map(member -> new RouteMemberRequest(member.getCourseId(), member.getSort()))
                .toList();
    }

    private List<RouteMemberRequest> draftMemberRequests(Long routeId) {
        return draftMapper.selectByRoute(routeId).stream()
                .map(member -> new RouteMemberRequest(member.getCourseId(), member.getSort()))
                .toList();
    }

    private void replaceDraftMembers(Long routeId, List<RouteMemberRequest> members) {
        draftMapper.deleteByRoute(routeId);
        for (RouteMemberRequest member : members) {
            draftMapper.insertMember(routeId, member.courseId(), member.sort() == null ? 0 : member.sort());
        }
    }

    private LearningRoute requireRoute(Long id) {
        LearningRoute route = routeMapper.selectById(id);
        if (route == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "路线不存在");
        }
        return route;
    }

    private RouteSummary toSummary(LearningRoute route) {
        int count;
        if (!ONLINE.equals(route.getStatus()) && !draftMapper.selectByRoute(route.getId()).isEmpty()) {
            count = draftMapper.selectByRoute(route.getId()).size();
        } else {
            count = currentMapper.selectByRoute(route.getId()).size();
        }
        return new RouteSummary(
                String.valueOf(route.getId()),
                route.getTitle(),
                route.getPriceCent(),
                route.getStatus(),
                count,
                route.getRevision());
    }

    private Page<LearningRoute> page(int pageNumber, int pageSize) {
        return new Page<>(Math.max(pageNumber, 1), Math.min(Math.max(pageSize, 1), 100));
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}