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

    @Delete("DELETE FROM assignment_route WHERE assignment_id = #{assignmentId}")
    int deleteByAssignment(@Param("assignmentId") Long assignmentId);
}