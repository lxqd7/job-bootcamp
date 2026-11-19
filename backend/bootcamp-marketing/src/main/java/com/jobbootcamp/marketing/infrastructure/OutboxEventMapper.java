package com.jobbootcamp.marketing.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.marketing.domain.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    @Select("""
            SELECT * FROM outbox_event
            WHERE status = 'PENDING'
              AND next_retry_at <= #{now}
              AND (lease_until IS NULL OR lease_until < #{now})
            ORDER BY id
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<OutboxEvent> selectClaimable(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
            UPDATE outbox_event
            SET lease_token = #{leaseToken}, lease_until = #{leaseUntil},
                attempt = attempt + 1, updated_at = #{now}
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int claim(@Param("id") Long id,
              @Param("leaseToken") String leaseToken,
              @Param("leaseUntil") LocalDateTime leaseUntil,
              @Param("now") LocalDateTime now);

    @Update("""
            UPDATE outbox_event
            SET status = 'SENT', lease_token = NULL, lease_until = NULL,
                error_code = NULL, updated_at = #{now}
            WHERE id = #{id} AND lease_token = #{leaseToken} AND status = 'PENDING'
            """)
    int markSent(@Param("id") Long id,
                 @Param("leaseToken") String leaseToken,
                 @Param("now") LocalDateTime now);

    @Update("""
            UPDATE outbox_event
            SET status = #{status}, lease_token = NULL, lease_until = NULL,
                error_code = #{errorCode}, next_retry_at = #{nextRetryAt}, updated_at = #{now}
            WHERE id = #{id} AND lease_token = #{leaseToken} AND status = 'PENDING'
            """)
    int markRetry(@Param("id") Long id,
                  @Param("leaseToken") String leaseToken,
                  @Param("status") String status,
                  @Param("errorCode") String errorCode,
                  @Param("nextRetryAt") LocalDateTime nextRetryAt,
                  @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM outbox_event WHERE status = #{status}")
    long countByStatus(@Param("status") String status);
}