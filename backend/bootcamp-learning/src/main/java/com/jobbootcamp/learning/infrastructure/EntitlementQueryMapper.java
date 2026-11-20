package com.jobbootcamp.learning.infrastructure;

import com.jobbootcamp.learning.domain.dto.MyCourseRow;
import com.jobbootcamp.learning.domain.dto.MyRouteRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EntitlementQueryMapper {

    @Select("""
            SELECT cg.course_id, cg.source_id, es.type AS source_type, cg.granted_at
            FROM course_grant cg
            JOIN entitlement_source es ON es.id = cg.source_id
            WHERE cg.user_id = #{userId} AND es.state = 'ACTIVE'
            ORDER BY cg.granted_at, cg.id
            """)
    List<MyCourseRow> selectActiveCourses(@Param("userId") Long userId);

    @Select("""
            SELECT re.route_id, re.source_id, re.future_courses, es.acquired_at
            FROM route_entitlement re
            JOIN entitlement_source es ON es.id = re.source_id
            WHERE re.user_id = #{userId} AND es.state = 'ACTIVE'
            ORDER BY es.acquired_at, re.id
            """)
    List<MyRouteRow> selectActiveRoutes(@Param("userId") Long userId);
}