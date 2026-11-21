package com.jobbootcamp.trade.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.trade.domain.OrderCoupon;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderCouponMapper extends BaseMapper<OrderCoupon> {
}