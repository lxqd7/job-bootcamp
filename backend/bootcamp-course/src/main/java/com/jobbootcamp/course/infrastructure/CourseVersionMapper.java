package com.jobbootcamp.course.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.course.domain.CourseVersion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseVersionMapper extends BaseMapper<CourseVersion> {
}