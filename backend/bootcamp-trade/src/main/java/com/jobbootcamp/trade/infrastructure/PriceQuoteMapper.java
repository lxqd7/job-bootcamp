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
}