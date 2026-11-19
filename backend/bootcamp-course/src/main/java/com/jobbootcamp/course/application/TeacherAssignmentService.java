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
import com.jobbootcamp.course.infrastructure.TeacherCourseMapper;
import com.jobbootcamp.course.infrastructure.TeacherRouteMapper;
import com.jobbootcamp.course.infrastructure.TeacherScopeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class TeacherAssignmentService {

    private final TeacherScopeMapper scopeMapper;
    private final TeacherCourseMapper teacherCourseMapper;
    private final TeacherRouteMapper teacherRouteMapper;
    private final CourseMapper courseMapper;
    private final LearningRouteMapper routeMapper;
    private final RouteMemberCurrentMapper currentMapper;
    private final RouteDraftMemberMapper draftMapper;

    public TeacherAssignmentService(TeacherScopeMapper scopeMapper,
                                    TeacherCourseMapper teacherCourseMapper,
                                    TeacherRouteMapper teacherRouteMapper,
                                    CourseMapper courseMapper,
                                    LearningRouteMapper routeMapper,
                                    RouteMemberCurrentMapper currentMapper,
                                    RouteDraftMemberMapper draftMapper) {
        this.scopeMapper = scopeMapper;
        this.teacherCourseMapper = teacherCourseMapper;
        this.teacherRouteMapper = teacherRouteMapper;
        this.courseMapper = courseMapper;
        this.routeMapper = routeMapper;
        this.currentMapper = currentMapper;
        this.draftMapper = draftMapper;
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