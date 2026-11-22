package com.jobbootcamp.learning.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.learning.domain.RefundIntentLesson;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RefundIntentLessonMapper extends BaseMapper<RefundIntentLesson> {
}