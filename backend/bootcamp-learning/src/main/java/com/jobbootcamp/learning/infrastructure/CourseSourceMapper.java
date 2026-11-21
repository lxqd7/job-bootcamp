package com.jobbootcamp.learning.infrastructure;

import com.jobbootcamp.learning.domain.SourceSelectionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 读取目标课程的全部 ACTIVE 来源；SQL 先按课程、再按 grantedAt/grantId 排序，
 * 调用方取每门课第一行作为最早可抵扣来源。
 */
@Mapper
public interface CourseSourceMapper {

    @Select("""
            <script>
            SELECT cg.course_id,
                   cg.source_id,
                   cg.id AS source_grant_id,
                   es.type AS source_type,
                   es.order_id AS source_order_id,
                   cg.granted_at
            FROM course_grant cg
            JOIN entitlement_source es ON es.id = cg.source_id
            WHERE cg.user_id = #{userId}
              AND es.state = 'ACTIVE'
              AND cg.course_id IN
              <foreach collection="courseIds" item="courseId" open="(" separator="," close=")">
                #{courseId}
              </foreach>
            ORDER BY cg.course_id, cg.granted_at, cg.id
            </script>
            """)
    List<SourceSelectionRow> selectActiveSources(@Param("userId") Long userId,
                                                 @Param("courseIds") List<Long> courseIds);
}