package com.jobbootcamp.marketing.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.marketing.domain.PricingJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface PricingJobMapper extends BaseMapper<PricingJob> {

    @Update("""
            UPDATE pricing_job
            SET lease_token = #{token}, lease_until = #{leaseUntil},
                attempts = attempts + 1, updated_at = #{now}
            WHERE quote_id = #{quoteId} AND status = 'CALCULATING'
              AND (lease_until IS NULL OR lease_until < #{now})
            """)
    int claimLease(@Param("quoteId") String quoteId,
                   @Param("token") String token,
                   @Param("leaseUntil") LocalDateTime leaseUntil,
                   @Param("now") LocalDateTime now);

    @Update("""
            UPDATE pricing_job
            SET status = #{status}, result_json = #{resultJson}, result_hash = #{resultHash},
                error_code = #{errorCode}, error_message = #{errorMessage},
                lease_token = NULL, lease_until = NULL, updated_at = #{now}
            WHERE quote_id = #{quoteId} AND lease_token = #{leaseToken}
            """)
    int finish(@Param("quoteId") String quoteId,
               @Param("leaseToken") String leaseToken,
               @Param("status") String status,
               @Param("resultJson") String resultJson,
               @Param("resultHash") String resultHash,
               @Param("errorCode") String errorCode,
               @Param("errorMessage") String errorMessage,
               @Param("now") LocalDateTime now);
}