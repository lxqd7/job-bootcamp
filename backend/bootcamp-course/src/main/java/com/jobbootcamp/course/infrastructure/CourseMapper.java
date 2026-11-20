package com.jobbootcamp.course.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.course.domain.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {

    @Select("SELECT * FROM course WHERE id = #{id} FOR UPDATE")
    Course selectForUpdate(@Param("id") Long id);
}