package com.jobbootcamp.learning.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.learning.domain.RouteEntitlement;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RouteEntitlementMapper extends BaseMapper<RouteEntitlement> {
}