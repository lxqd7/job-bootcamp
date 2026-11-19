package com.jobbootcamp.course.infrastructure;

import com.jobbootcamp.course.domain.RouteMemberEpoch;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RouteMemberEpochMapper {

    @Select("""
            SELECT id, route_id, course_id, joined_version, removed_version, joined_at, removed_at
            FROM route_member_epoch
            WHERE route_id = #{routeId} AND removed_version IS NULL
            ORDER BY course_id
            """)
    List<RouteMemberEpoch> selectActiveByRoute(@Param("routeId") Long routeId);

    @Select("""
            SELECT id, route_id, course_id, joined_version, removed_version, joined_at, removed_at
            FROM route_member_epoch
            WHERE route_id = #{routeId} AND course_id = #{courseId} AND removed_version IS NULL
            LIMIT 1
            """)
    RouteMemberEpoch selectActive(@Param("routeId") Long routeId, @Param("courseId") Long courseId);

    @Insert("""
            INSERT INTO route_member_epoch(route_id, course_id, joined_version, joined_at)
            VALUES(#{routeId}, #{courseId}, #{joinedVersion}, #{joinedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertEpoch(RouteMemberEpoch epoch);

    @Update("""
            UPDATE route_member_epoch
            SET removed_version = #{removedVersion}, removed_at = #{removedAt}
            WHERE route_id = #{routeId} AND course_id = #{courseId} AND removed_version IS NULL
            """)
    int closeEpoch(@Param("routeId") Long routeId,
                   @Param("courseId") Long courseId,
                   @Param("removedVersion") long removedVersion,
                   @Param("removedAt") LocalDateTime removedAt);
}