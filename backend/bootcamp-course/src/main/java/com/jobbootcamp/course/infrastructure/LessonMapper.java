package com.jobbootcamp.course.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.course.domain.Lesson;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LessonMapper extends BaseMapper<Lesson> {
}