package com.jobbootcamp.trade.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.trade.domain.Refund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RefundMapper extends BaseMapper<Refund> {

    @Select("SELECT * FROM refund WHERE system_return_payment_id = #{paymentId}")
    Refund selectBySystemReturnPaymentId(@Param("paymentId") Long paymentId);

    @Select("SELECT * FROM refund WHERE intent_id = #{intentId}")
    Refund selectByIntentId(@Param("intentId") Long intentId);
}