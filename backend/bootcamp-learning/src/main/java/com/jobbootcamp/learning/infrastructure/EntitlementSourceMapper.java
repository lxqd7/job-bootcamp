package com.jobbootcamp.learning.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.learning.domain.EntitlementSource;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EntitlementSourceMapper extends BaseMapper<EntitlementSource> {
}