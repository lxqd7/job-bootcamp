package com.jobbootcamp.learning.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.learning.domain.RefundIntent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface RefundIntentMapper extends BaseMapper<RefundIntent> {

    @Select("""
            SELECT * FROM refund_intent
            WHERE user_id = #{userId} AND order_id = #{orderId}
              AND state IN ('ACCEPTED', 'PROCESSING')
            ORDER BY id DESC LIMIT 1
            """)
    RefundIntent selectActive(@Param("userId") Long userId, @Param("orderId") Long orderId);

    @Update("""
            UPDATE refund_intent
            SET state = #{state}, reason = #{reason}, active_order_id = #{activeOrderId}, updated_at = #{now}
            WHERE id = #{id}
            """)
    int updateState(@Param("id") Long id,
                    @Param("state") String state,
                    @Param("reason") String reason,
                    @Param("activeOrderId") Long activeOrderId,
                    @Param("now") LocalDateTime now);
}