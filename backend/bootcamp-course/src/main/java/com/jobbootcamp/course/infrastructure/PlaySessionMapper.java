package com.jobbootcamp.course.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.course.domain.PlaySession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlaySessionMapper extends BaseMapper<PlaySession> {
}