package com.jobbootcamp.assignment.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.assignment.domain.AiFeedback;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiFeedbackMapper extends BaseMapper<AiFeedback> {
}