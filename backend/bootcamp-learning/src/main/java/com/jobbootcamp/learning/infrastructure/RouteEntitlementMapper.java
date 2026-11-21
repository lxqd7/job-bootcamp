package com.jobbootcamp.learning.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.learning.domain.RouteEntitlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RouteEntitlementMapper extends BaseMapper<RouteEntitlement> {

    @Select("""
            SELECT re.* FROM route_entitlement re
            JOIN entitlement_source es ON es.id = re.source_id
            WHERE re.route_id = #{routeId}
              AND re.future_courses = TRUE
              AND es.state = 'ACTIVE'
            ORDER BY re.id
            """)
    List<RouteEntitlement> selectActiveByRoute(@Param("routeId") Long routeId);
}