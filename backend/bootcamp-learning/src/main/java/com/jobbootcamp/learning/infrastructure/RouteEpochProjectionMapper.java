package com.jobbootcamp.learning.infrastructure;

import com.jobbootcamp.learning.domain.RouteEpochProjection;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface RouteEpochProjectionMapper {

    @Insert("""
            INSERT INTO route_epoch_projection
                (epoch_id, route_id, course_id, joined_at, removed_at, revision_no, is_free, created_at, updated_at)
            VALUES (#{epochId}, #{routeId}, #{courseId}, #{joinedAt}, NULL, #{revisionNo}, #{isFree}, #{now}, #{now})
            ON DUPLICATE KEY UPDATE
                removed_at = NULL, revision_no = #{revisionNo}, updated_at = #{now}
            """)
    int upsert(@Param("epochId") Long epochId,
               @Param("routeId") Long routeId,
               @Param("courseId") Long courseId,
               @Param("joinedAt") LocalDateTime joinedAt,
               @Param("revisionNo") Long revisionNo,
               @Param("isFree") boolean isFree,
               @Param("now") LocalDateTime now);

    @Update("""
            UPDATE route_epoch_projection
            SET removed_at = #{removedAt}, revision_no = #{revisionNo}, updated_at = #{now}
            WHERE epoch_id = #{epochId} AND removed_at IS NULL
            """)
    int close(@Param("epochId") Long epochId,
              @Param("removedAt") LocalDateTime removedAt,
              @Param("revisionNo") Long revisionNo,
              @Param("now") LocalDateTime now);
}