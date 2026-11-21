package com.jobbootcamp.trade.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.trade.domain.TradeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface TradeOrderMapper extends BaseMapper<TradeOrder> {

    @Select("SELECT * FROM trade_order WHERE id = #{id} FOR UPDATE")
    TradeOrder selectForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE trade_order
            SET state = 'PAID', paid_at = #{paidAt}, payment_sequence = #{paymentSequence},
                fulfillment_state = 'PENDING', cancel_requested_at = NULL, updated_at = #{now}
            WHERE id = #{id}
            """)
    int markPaid(@Param("id") Long id,
                 @Param("paidAt") LocalDateTime paidAt,
                 @Param("paymentSequence") Long paymentSequence,
                 @Param("now") LocalDateTime now);

    @Update("""
            UPDATE trade_order SET state = #{state}, updated_at = #{now}
            WHERE id = #{id} AND state IN ('WAIT_PAY', 'CLOSING', 'CREATING')
            """)
    int markClosed(@Param("id") Long id, @Param("state") String state, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE trade_order SET state = 'CLOSING', cancel_requested_at = #{cancelAt}, updated_at = #{now}
            WHERE id = #{id} AND state = 'WAIT_PAY'
            """)
    int markClosing(@Param("id") Long id,
                    @Param("cancelAt") LocalDateTime cancelAt,
                    @Param("now") LocalDateTime now);

    @Update("""
            UPDATE trade_order SET fulfillment_state = #{fulfillmentState}, updated_at = #{now}
            WHERE id = #{id}
            """)
    int updateFulfillment(@Param("id") Long id,
                          @Param("fulfillmentState") String fulfillmentState,
                          @Param("now") LocalDateTime now);

    @Update("""
            UPDATE trade_order SET coupon_state = #{couponState}, updated_at = #{now}
            WHERE id = #{id} AND coupon_state IN ('LOCKED', 'PENDING')
            """)
    int updateCouponState(@Param("id") Long id,
                          @Param("couponState") String couponState,
                          @Param("now") LocalDateTime now);
}