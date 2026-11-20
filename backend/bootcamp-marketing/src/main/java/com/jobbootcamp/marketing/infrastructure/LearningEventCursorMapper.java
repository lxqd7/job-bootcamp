package com.jobbootcamp.marketing.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface LearningEventCursorMapper {

    @Select("SELECT COALESCE(last_sequence, 0) FROM learning_event_cursor WHERE user_id = #{userId}")
    Long selectLastSequence(@Param("userId") Long userId);

    @Insert("""
            INSERT INTO learning_event_cursor(user_id, last_sequence, updated_at)
            VALUES (#{userId}, #{sequence}, #{now}) AS incoming
            ON DUPLICATE KEY UPDATE
                last_sequence = GREATEST(learning_event_cursor.last_sequence, incoming.last_sequence),
                updated_at = incoming.updated_at
            """)
    int upsert(@Param("userId") Long userId,
               @Param("sequence") long sequence,
               @Param("now") LocalDateTime now);
}