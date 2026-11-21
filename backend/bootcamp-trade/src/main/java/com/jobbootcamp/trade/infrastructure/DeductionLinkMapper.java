package com.jobbootcamp.trade.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.trade.domain.DeductionLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 抵扣依赖边状态迁移；只允许当前订单的 RESERVED 边释放，避免覆盖已提交关系。
 */
@Mapper
public interface DeductionLinkMapper extends BaseMapper<DeductionLink> {

    @Update("""
            UPDATE deduction_link SET state = #{target}, updated_at = #{now}
            WHERE dependent_order_id = #{orderId} AND state = #{expected}
            """)
    int updateState(@Param("orderId") Long orderId,
                    @Param("expected") String expected,
                    @Param("target") String target,
                    @Param("now") LocalDateTime now);
}