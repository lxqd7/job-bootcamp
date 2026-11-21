package com.jobbootcamp.marketing.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.marketing.domain.UserCoupon;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {
}