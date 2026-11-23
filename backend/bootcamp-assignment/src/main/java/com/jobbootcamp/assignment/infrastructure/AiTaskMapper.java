package com.jobbootcamp.assignment.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.assignment.domain.AiTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiTaskMapper extends BaseMapper<AiTask> {

    @Select("SELECT * FROM ai_task WHERE submission_id = #{submissionId}")
    AiTask selectBySubmission(@Param("submissionId") Long submissionId);

    @Select("SELECT * FROM ai_task WHERE id = #{id} FOR UPDATE")
    AiTask selectForUpdate(@Param("id") Long id);

    /**
     * 租约到期或等待重试到期的任务可被重新领取；SKIP LOCKED 让多实例不会抢同一行。
     */
    @Select("""
            SELECT id FROM ai_task
            WHERE ((state IN ('QUEUED', 'RETRY_WAIT')
                    AND (next_retry_at IS NULL OR next_retry_at <= #{now}))
                OR (state = 'RUNNING' AND lease_until IS NOT NULL AND lease_until < #{now}))
              AND deadline_at > #{now}
            ORDER BY id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """)
    Long selectClaimableId(@Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_task
            SET state = 'RUNNING', lease_token = #{token}, lease_until = #{leaseUntil},
                round_no = round_no + 1, next_retry_at = NULL, updated_at = #{now}
            WHERE id = #{id}
            """)
    int markClaimed(@Param("id") Long id,
                    @Param("token") String token,
                    @Param("leaseUntil") LocalDateTime leaseUntil,
                    @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_task SET lease_until = #{leaseUntil}, updated_at = #{now}
            WHERE id = #{id} AND state = 'RUNNING' AND lease_token = #{token}
            """)
    int renewLease(@Param("id") Long id,
                   @Param("token") String token,
                   @Param("leaseUntil") LocalDateTime leaseUntil,
                   @Param("now") LocalDateTime now);

    @Select("""
            SELECT id FROM ai_task
            WHERE state IN ('QUEUED', 'RUNNING', 'RETRY_WAIT') AND deadline_at <= #{now}
            ORDER BY id LIMIT #{limit}
            """)
    List<Long> selectExpiredDeadlineIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Select("""
            SELECT id FROM ai_task
            WHERE state = 'RUNNING' AND lease_until IS NOT NULL
              AND lease_until < #{now} AND deadline_at > #{now}
            ORDER BY id LIMIT #{limit}
            """)
    List<Long> selectExpiredLeaseIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
            UPDATE ai_task
            SET state = 'QUEUED', lease_token = NULL, lease_until = NULL,
                next_retry_at = #{now}, updated_at = #{now}
            WHERE id = #{id} AND state = 'RUNNING'
              AND lease_until IS NOT NULL AND lease_until < #{now}
            """)
    int requeueExpiredLease(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_task
            SET call_count = #{callCount}, round_no = #{roundNo},
                repair_used = #{repairUsed}, updated_at = #{now}
            WHERE id = #{id} AND state = 'RUNNING' AND lease_token = #{token}
            """)
    int updateProgress(@Param("id") Long id,
                       @Param("token") String token,
                       @Param("callCount") int callCount,
                       @Param("roundNo") int roundNo,
                       @Param("repairUsed") boolean repairUsed,
                       @Param("now") LocalDateTime now);
}