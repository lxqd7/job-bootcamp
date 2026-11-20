package com.jobbootcamp.trade.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.trade.domain.IdempotencyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface IdempotencyMapper extends BaseMapper<IdempotencyRecord> {

    @Update("""
            UPDATE api_idempotency
            SET status = 'COMPLETED', resource_id = #{resourceId}, response_json = #{responseJson}, updated_at = #{now}
            WHERE id = #{id} AND status = 'IN_PROGRESS'
            """)
    int complete(@Param("id") Long id,
                 @Param("resourceId") String resourceId,
                 @Param("responseJson") String responseJson,
                 @Param("now") LocalDateTime now);

    @Update("""
            UPDATE api_idempotency
            SET status = 'FAILED', updated_at = #{now}
            WHERE id = #{id} AND status = 'IN_PROGRESS'
            """)
    int fail(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE api_idempotency
            SET status = 'IN_PROGRESS', updated_at = #{now}
            WHERE id = #{id} AND status = 'FAILED'
            """)
    int reclaim(@Param("id") Long id, @Param("now") LocalDateTime now);
}