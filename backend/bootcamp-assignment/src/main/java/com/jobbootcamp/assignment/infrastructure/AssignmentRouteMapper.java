package com.jobbootcamp.assignment.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.assignment.domain.AssignmentRoute;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AssignmentRouteMapper extends BaseMapper<AssignmentRoute> {

    @Select("""
            SELECT id, assignment_id, route_id, epoch_id, bound_at
            FROM assignment_route WHERE assignment_id = #{assignmentId} ORDER BY route_id
            """)
    List<AssignmentRoute> selectByAssignment(@Param("assignmentId") Long assignmentId);

    @Select("SELECT COUNT(*) FROM assignment_route WHERE assignment_id = #{assignmentId}")
    int countByAssignment(@Param("assignmentId") Long assignmentId);

    @Select("""
            SELECT ar.id, ar.assignment_id, ar.route_id, ar.epoch_id, ar.bound_at
            FROM assignment_route ar
            JOIN assignment a ON a.id = ar.assignment_id
            WHERE ar.route_id = #{routeId} AND a.course_id = #{courseId}
              AND a.kind IN ('BENEFIT_CHOICE', 'BENEFIT_CODE')
            ORDER BY a.updated_at DESC, a.id DESC
            """)
    List<AssignmentRoute> selectByRouteCourse(@Param("routeId") Long routeId,
                                              @Param("courseId") Long courseId);

    @Delete("DELETE FROM assignment_route WHERE assignment_id = #{assignmentId}")
    int deleteByAssignment(@Param("assignmentId") Long assignmentId);
}