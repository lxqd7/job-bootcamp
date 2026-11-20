package com.jobbootcamp.learning.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.learning.domain.LessonProgress;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LessonProgressMapper extends BaseMapper<LessonProgress> {
}