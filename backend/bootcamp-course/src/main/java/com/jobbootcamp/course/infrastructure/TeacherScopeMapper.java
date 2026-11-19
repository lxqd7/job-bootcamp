package com.jobbootcamp.course.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TeacherScopeMapper {

    @Insert("INSERT IGNORE INTO teacher_scope(teacher_id, scope_revision) VALUES(#{teacherId}, 0)")
    int insertIfAbsent(@Param("teacherId") Long teacherId);

    @Select("SELECT scope_revision FROM teacher_scope WHERE teacher_id = #{teacherId} FOR UPDATE")
    Long selectRevisionForUpdate(@Param("teacherId") Long teacherId);

    @Select("SELECT scope_revision FROM teacher_scope WHERE teacher_id = #{teacherId}")
    Long selectRevision(@Param("teacherId") Long teacherId);

    @Update("UPDATE teacher_scope SET scope_revision = scope_revision + 1 WHERE teacher_id = #{teacherId}")
    int bumpRevision(@Param("teacherId") Long teacherId);
}