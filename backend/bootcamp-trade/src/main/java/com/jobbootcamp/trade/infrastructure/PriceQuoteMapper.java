package com.jobbootcamp.trade.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.trade.domain.PriceQuote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface PriceQuoteMapper extends BaseMapper<PriceQuote> {

    @Update("""
            UPDATE price_quote
            SET status = #{status}, updated_at = #{now}
            WHERE quote_id = #{quoteId} AND status = 'READY'
            """)
    int updateStatusFromReady(@Param("quoteId") String quoteId,
                              @Param("status") String status,
                              @Param("now") LocalDateTime now);

    @Update("""
            UPDATE price_quote
            SET status = #{toStatus}, updated_at = #{now}
            WHERE quote_id = #{quoteId} AND status = #{fromStatus}
            """)
    int updateStatus(@Param("quoteId") String quoteId,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus,
                     @Param("now") LocalDateTime now);

    @Update("""
            UPDATE price_quote
            SET status = #{status}, discount_cent = #{discountCent}, payable_cent = #{payableCent},
                result_hash = #{resultHash}, pricing_hash = #{pricingHash}, coupon_plan_json = #{couponPlanJson},
                result_json = #{resultJson}, updated_at = #{now}
            WHERE quote_id = #{quoteId} AND status = 'CALCULATING'
            """)
    int updatePricingResult(@Param("quoteId") String quoteId,
                            @Param("status") String status,
                            @Param("discountCent") long discountCent,
                            @Param("payableCent") long payableCent,
                            @Param("resultHash") String resultHash,
                            @Param("pricingHash") String pricingHash,
                            @Param("couponPlanJson") String couponPlanJson,
                            @Param("resultJson") String resultJson,
                            @Param("now") LocalDateTime now);
}