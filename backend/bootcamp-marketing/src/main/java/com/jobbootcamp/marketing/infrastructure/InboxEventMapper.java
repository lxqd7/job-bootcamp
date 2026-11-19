package com.jobbootcamp.marketing.infrastructure;

import com.jobbootcamp.marketing.domain.InboxEventRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface InboxEventMapper {

    @Insert("""
            INSERT INTO inbox_event(consumer, event_id, business_key, payload_hash, state, payload, created_at, updated_at)
            VALUES (#{consumer}, #{eventId}, #{businessKey}, #{payloadHash}, 'PROCESSING', #{payload}, #{now}, #{now})
            """)
    int insertProcessing(@Param("consumer") String consumer,
                         @Param("eventId") String eventId,
                         @Param("businessKey") String businessKey,
                         @Param("payloadHash") String payloadHash,
                         @Param("payload") String payload,
                         @Param("now") LocalDateTime now);

    @Select("SELECT * FROM inbox_event WHERE consumer = #{consumer} AND event_id = #{eventId}")
    InboxEventRow select(@Param("consumer") String consumer, @Param("eventId") String eventId);

    @Update("""
            UPDATE inbox_event SET state = 'PROCESSED', updated_at = #{now}
            WHERE consumer = #{consumer} AND event_id = #{eventId} AND state = 'PROCESSING'
            """)
    int markProcessed(@Param("consumer") String consumer,
                      @Param("eventId") String eventId,
                      @Param("now") LocalDateTime now);

    @Update("""
            UPDATE inbox_event SET state = 'FAILED', next_retry_at = #{nextRetryAt}, updated_at = #{now}
            WHERE consumer = #{consumer} AND event_id = #{eventId} AND state = 'PROCESSING'
            """)
    int markFailed(@Param("consumer") String consumer,
                   @Param("eventId") String eventId,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt,
                   @Param("now") LocalDateTime now);
}