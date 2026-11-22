package com.jobbootcamp.trade.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.trade.domain.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {

    @Select("""
            SELECT * FROM payment
            WHERE channel = #{channel} AND channel_trade_no = #{channelTradeNo}
            """)
    Payment selectByChannelTradeNo(@Param("channel") String channel,
                                   @Param("channelTradeNo") String channelTradeNo);

    @Select("""
            SELECT * FROM payment
            WHERE order_id = #{orderId} AND status = 'SUCCEEDED'
            ORDER BY id LIMIT 1
            """)
    Payment selectSucceededByOrder(@Param("orderId") Long orderId);
}