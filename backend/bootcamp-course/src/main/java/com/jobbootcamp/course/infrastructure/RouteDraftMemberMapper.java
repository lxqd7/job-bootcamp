package com.jobbootcamp.course.infrastructure;

import com.jobbootcamp.course.domain.RouteDraftMember;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RouteDraftMemberMapper {

    @Select("SELECT route_id, course_id, sort FROM route_draft_member WHERE route_id = #{routeId} ORDER BY sort, course_id")
    List<RouteDraftMember> selectByRoute(@Param("routeId") Long routeId);

    @Delete("DELETE FROM route_draft_member WHERE route_id = #{routeId}")
    int deleteByRoute(@Param("routeId") Long routeId);

    @Insert("INSERT INTO route_draft_member(route_id, course_id, sort) VALUES(#{routeId}, #{courseId}, #{sort})")
    int insertMember(@Param("routeId") Long routeId,
                     @Param("courseId") Long courseId,
                     @Param("sort") int sort);
}