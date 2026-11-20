package com.jobbootcamp.trade.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.trade.domain.PaymentRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface PaymentRequestMapper extends BaseMapper<PaymentRequest> {

    @Select("""
            SELECT * FROM payment_request
            WHERE channel = #{channel} AND merchant_request_no = #{merchantRequestNo}
            """)
    PaymentRequest selectByMerchant(@Param("channel") String channel,
                                    @Param("merchantRequestNo") String merchantRequestNo);

    @Select("SELECT * FROM payment_request WHERE id = #{id} FOR UPDATE")
    PaymentRequest selectForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE payment_request SET state = #{state}, updated_at = #{now}
            WHERE id = #{id}
            """)
    int updateState(@Param("id") Long id,
                    @Param("state") String state,
                    @Param("now") LocalDateTime now);
}