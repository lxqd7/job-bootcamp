package com.jobbootcamp.course.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.course.domain.LearningRoute;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LearningRouteMapper extends BaseMapper<LearningRoute> {
}