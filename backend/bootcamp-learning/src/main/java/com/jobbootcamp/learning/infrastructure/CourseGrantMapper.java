package com.jobbootcamp.learning.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.learning.domain.CourseGrant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CourseGrantMapper extends BaseMapper<CourseGrant> {

    @Select("""
            SELECT cg.source_id
            FROM course_grant cg
            JOIN entitlement_source es ON es.id = cg.source_id
            WHERE cg.user_id = #{userId} AND cg.course_id = #{courseId} AND es.state = 'ACTIVE'
            ORDER BY cg.granted_at, cg.id
            LIMIT 1
            """)
    Long selectActiveSource(@Param("userId") Long userId, @Param("courseId") Long courseId);
}