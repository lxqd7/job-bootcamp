package com.jobbootcamp.course.infrastructure;

import com.jobbootcamp.course.domain.RouteMemberCurrent;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface RouteMemberCurrentMapper {

    @Select("SELECT route_id, course_id, epoch_id, sort FROM route_member_current WHERE route_id = #{routeId} ORDER BY sort, course_id")
    List<RouteMemberCurrent> selectByRoute(@Param("routeId") Long routeId);

    @Select("SELECT DISTINCT route_id FROM route_member_current WHERE course_id = #{courseId} ORDER BY route_id")
    List<Long> selectRouteIdsByCourse(@Param("courseId") Long courseId);

    @Delete("DELETE FROM route_member_current WHERE route_id = #{routeId}")
    int deleteByRoute(@Param("routeId") Long routeId);

    @Delete("DELETE FROM route_member_current WHERE route_id = #{routeId} AND course_id = #{courseId}")
    int deleteMember(@Param("routeId") Long routeId, @Param("courseId") Long courseId);

    @Insert("INSERT INTO route_member_current(route_id, course_id, epoch_id, sort) VALUES(#{routeId}, #{courseId}, #{epochId}, #{sort})")
    int insertMember(@Param("routeId") Long routeId,
                     @Param("courseId") Long courseId,
                     @Param("epochId") Long epochId,
                     @Param("sort") int sort);

    @Update("UPDATE route_member_current SET sort = #{sort} WHERE route_id = #{routeId} AND course_id = #{courseId}")
    int updateSort(@Param("routeId") Long routeId, @Param("courseId") Long courseId, @Param("sort") int sort);
}