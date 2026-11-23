package com.jobbootcamp.marketing.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.marketing.domain.ExcellentRevocation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ExcellentRevocationMapper extends BaseMapper<ExcellentRevocation> {

    @Insert("""
            INSERT IGNORE INTO excellent_revocation(
                note_id, original_event_id, reason, occurred_at, state, created_at, updated_at)
            VALUES (#{noteId}, #{originalEventId}, #{reason}, #{occurredAt}, 'PENDING', #{now}, #{now})
            """)
    int insertPending(@Param("noteId") Long noteId,
                      @Param("originalEventId") String originalEventId,
                      @Param("reason") String reason,
                      @Param("occurredAt") LocalDateTime occurredAt,
                      @Param("now") LocalDateTime now);

    @Select("SELECT * FROM excellent_revocation WHERE note_id = #{noteId} FOR UPDATE")
    ExcellentRevocation selectForUpdate(@Param("noteId") Long noteId);

    @Update("""
            UPDATE excellent_revocation
            SET state = 'APPLIED', revoke_ledger_id = #{ledgerId}, updated_at = #{now}
            WHERE note_id = #{noteId} AND state = 'PENDING'
            """)
    int markApplied(@Param("noteId") Long noteId,
                    @Param("ledgerId") Long ledgerId,
                    @Param("now") LocalDateTime now);
}