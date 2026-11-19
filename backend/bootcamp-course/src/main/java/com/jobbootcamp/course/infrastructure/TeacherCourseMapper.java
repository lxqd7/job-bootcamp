package com.jobbootcamp.course.infrastructure;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TeacherCourseMapper {

    @Select("SELECT course_id FROM teacher_course WHERE teacher_id = #{teacherId} AND active = TRUE ORDER BY course_id")
    List<Long> selectActiveCourseIds(@Param("teacherId") Long teacherId);

    @Delete("DELETE FROM teacher_course WHERE teacher_id = #{teacherId}")
    int deleteByTeacher(@Param("teacherId") Long teacherId);

    @Insert("INSERT INTO teacher_course(teacher_id, course_id, active) VALUES(#{teacherId}, #{courseId}, TRUE)")
    int insertAssignment(@Param("teacherId") Long teacherId, @Param("courseId") Long courseId);
}