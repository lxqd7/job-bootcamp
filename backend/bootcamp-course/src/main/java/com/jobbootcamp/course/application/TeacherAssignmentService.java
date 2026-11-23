package com.jobbootcamp.course.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ErrorData;
import com.jobbootcamp.course.domain.Course;
import com.jobbootcamp.course.domain.LearningRoute;
import com.jobbootcamp.course.domain.RouteDraftMember;
import com.jobbootcamp.course.domain.dto.RouteSummary;
import com.jobbootcamp.course.domain.dto.TeacherAssignmentsRequest;
import com.jobbootcamp.course.domain.dto.TeacherAssignmentsResponse;
import com.jobbootcamp.course.domain.dto.TeacherCourseItem;
import com.jobbootcamp.course.domain.dto.TeacherRouteItem;
import com.jobbootcamp.course.infrastructure.CourseMapper;
import com.jobbootcamp.course.infrastructure.LearningRouteMapper;
import com.jobbootcamp.course.infrastructure.RouteDraftMemberMapper;
import com.jobbootcamp.course.infrastructure.RouteMemberCurrentMapper;
import com.jobbootcamp.course.infrastructure.RouteMemberEpochMapper;
import com.jobbootcamp.course.infrastructure.TeacherCourseMapper;
import com.jobbootcamp.course.infrastructure.TeacherRouteMapper;
import com.jobbootcamp.course.infrastructure.TeacherScopeMapper;
import com.jobbootcamp.contracts.internal.TeacherBenefitScopeResponse;
import com.jobbootcamp.course.domain.RouteMemberEpoch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 教师分配是课程/路线两个独立集合，不能由教师端自行扩大；
 * 更新使用教师级 revision 做乐观锁，整组替换后新会话读取新分配。
 */
@Service
public class TeacherAssignmentService {

    private final TeacherScopeMapper scopeMapper;
    private final TeacherCourseMapper teacherCourseMapper;
    private final TeacherRouteMapper teacherRouteMapper;
    private final CourseMapper courseMapper;
    private final LearningRouteMapper routeMapper;
    private final RouteMemberCurrentMapper currentMapper;
    private final RouteDraftMemberMapper draftMapper;
    private final RouteMemberEpochMapper epochMapper;

    public TeacherAssignmentService(TeacherScopeMapper scopeMapper,
                                    TeacherCourseMapper teacherCourseMapper,
                                    TeacherRouteMapper teacherRouteMapper,
                                    CourseMapper courseMapper,
                                    LearningRouteMapper routeMapper,
                                    RouteMemberCurrentMapper currentMapper,
                                    RouteDraftMemberMapper draftMapper,
                                    RouteMemberEpochMapper epochMapper) {
        this.scopeMapper = scopeMapper;
        this.teacherCourseMapper = teacherCourseMapper;
        this.teacherRouteMapper = teacherRouteMapper;
        this.courseMapper = courseMapper;
        this.routeMapper = routeMapper;
        this.currentMapper = currentMapper;
        this.draftMapper = draftMapper;
        this.epochMapper = epochMapper;
    }

    public TeacherAssignmentsResponse assignments(Long teacherId) {
        scopeMapper.insertIfAbsent(teacherId);
        Long revision = scopeMapper.selectRevision(teacherId);
        List<String> courseIds = teacherCourseMapper.selectActiveCourseIds(teacherId).stream()
                .map(String::valueOf).toList();
        List<String> routeIds = teacherRouteMapper.selectActiveRouteIds(teacherId).stream()
                .map(String::valueOf).toList();
        return new TeacherAssignmentsResponse(String.valueOf(teacherId), courseIds, routeIds,
                revision == null ? 0 : revision);
    }

    @Transactional
    public TeacherAssignmentsResponse assign(Long teacherId, TeacherAssignmentsRequest request) {
        Set<Long> courseIds = new LinkedHashSet<>(request.courseIds() == null ? List.of() : request.courseIds());
        Set<Long> routeIds = new LinkedHashSet<>(request.routeIds() == null ? List.of() : request.routeIds());
        for (Long courseId : courseIds) {
            if (courseMapper.selectById(courseId) == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "课程不存在：" + courseId);
            }
        }
        for (Long routeId : routeIds) {
            if (routeMapper.selectById(routeId) == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "路线不存在：" + routeId);
            }
        }
        scopeMapper.insertIfAbsent(teacherId);
        Long currentRevision = scopeMapper.selectRevisionForUpdate(teacherId);
        if (currentRevision != null && !currentRevision.equals(request.revision())) {
            throw new BusinessException(ErrorCode.CONFLICT, "教师分配已在其他位置更新，请刷新后重试",
                    new ErrorData(null, currentRevision, null));
        }
        teacherCourseMapper.deleteByTeacher(teacherId);
        teacherRouteMapper.deleteByTeacher(teacherId);
        for (Long courseId : courseIds) {
            teacherCourseMapper.insertAssignment(teacherId, courseId);
        }
        for (Long routeId : routeIds) {
            teacherRouteMapper.insertAssignment(teacherId, routeId);
        }
        scopeMapper.bumpRevision(teacherId);
        return assignments(teacherId);
    }

    /**
     * 只读作答与 AI 反馈每次按当前分配验权，教师调离后旧页面不能继续读取。
     */
    public boolean hasCourseScope(Long teacherId, Long courseId) {
        return teacherCourseMapper.countActiveCourse(teacherId, courseId) > 0;
    }

    /**
     * 福利题发布前的双重范围检查：课程分配、路线分配、路线在线、路线当前包含该课程。
     * teacherId 为 null 表示管理员治理，不做教师分配检查，但仍要求路线在线且包含课程。
     */
    public TeacherBenefitScopeResponse benefitScope(Long teacherId, Long courseId, List<Long> routeIds) {
        boolean courseAssigned = teacherId == null
                || teacherCourseMapper.countActiveCourse(teacherId, courseId) > 0;
        List<TeacherBenefitScopeResponse.RouteScope> routes = new ArrayList<>();
        for (Long routeId : routeIds) {
            boolean routeAssigned = teacherId == null
                    || teacherRouteMapper.countActiveRoute(teacherId, routeId) > 0;
            LearningRoute route = routeMapper.selectById(routeId);
            boolean routeOnline = route != null && "ONLINE".equals(route.getStatus());
            RouteMemberEpoch epoch = route == null ? null : epochMapper.selectActive(routeId, courseId);
            boolean containsCourse = epoch != null;
            String reason = null;
            if (teacherId != null && !courseAssigned) {
                reason = "TEACHER_COURSE_SCOPE";
            } else if (!routeAssigned) {
                reason = "TEACHER_ROUTE_SCOPE";
            } else if (route == null) {
                reason = "ROUTE_NOT_FOUND";
            } else if (!routeOnline) {
                reason = "ROUTE_NOT_ONLINE";
            } else if (!containsCourse) {
                reason = "ROUTE_NOT_CONTAIN_COURSE";
            }
            routes.add(new TeacherBenefitScopeResponse.RouteScope(routeId,
                    epoch == null ? null : epoch.getId(), routeAssigned, routeOnline, containsCourse, reason));
        }
        boolean allRoutesAssigned = routes.stream()
                .allMatch(TeacherBenefitScopeResponse.RouteScope::routeAssigned);
        return new TeacherBenefitScopeResponse(courseAssigned, allRoutesAssigned, routes);
    }

    public List<TeacherCourseItem> courses(Long teacherId) {
        List<TeacherCourseItem> items = new ArrayList<>();
        for (Long courseId : teacherCourseMapper.selectActiveCourseIds(teacherId)) {
            Course course = courseMapper.selectById(courseId);
            if (course != null) {
                items.add(new TeacherCourseItem(String.valueOf(course.getId()), course.getTitle(),
                        course.getStatus(), String.valueOf(course.getCategoryId())));
            }
        }
        return items;
    }

    public List<TeacherRouteItem> routes(Long teacherId) {
        List<TeacherRouteItem> items = new ArrayList<>();
        for (Long routeId : teacherRouteMapper.selectActiveRouteIds(teacherId)) {
            LearningRoute route = routeMapper.selectById(routeId);
            if (route != null) {
                int count = !"ONLINE".equals(route.getStatus())
                        ? draftMapper.selectByRoute(routeId).size()
                        : currentMapper.selectByRoute(routeId).size();
                items.add(new TeacherRouteItem(String.valueOf(route.getId()), route.getTitle(),
                        route.getStatus(), route.getPriceCent(), count));
            }
        }
        return items;
    }
}