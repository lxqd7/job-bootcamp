package com.jobbootcamp.trade.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.trade.domain.OrderCourse;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderCourseMapper extends BaseMapper<OrderCourse> {
}