package com.jobbootcamp.course.infrastructure;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TeacherRouteMapper {

    @Select("SELECT route_id FROM teacher_route WHERE teacher_id = #{teacherId} AND active = TRUE ORDER BY route_id")
    List<Long> selectActiveRouteIds(@Param("teacherId") Long teacherId);

    @Select("SELECT COUNT(*) FROM teacher_route WHERE teacher_id = #{teacherId} AND route_id = #{routeId} AND active = TRUE")
    int countActiveRoute(@Param("teacherId") Long teacherId, @Param("routeId") Long routeId);

    @Delete("DELETE FROM teacher_route WHERE teacher_id = #{teacherId}")
    int deleteByTeacher(@Param("teacherId") Long teacherId);

    @Insert("INSERT INTO teacher_route(teacher_id, route_id, active) VALUES(#{teacherId}, #{routeId}, TRUE)")
    int insertAssignment(@Param("teacherId") Long teacherId, @Param("routeId") Long routeId);
}