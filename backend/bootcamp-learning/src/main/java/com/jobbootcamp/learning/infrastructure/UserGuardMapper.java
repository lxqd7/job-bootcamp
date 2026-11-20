package com.jobbootcamp.learning.infrastructure;

import com.jobbootcamp.learning.domain.UserGuard;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface UserGuardMapper {

    @Insert("""
            INSERT IGNORE INTO user_guard(user_id, sequence, completion_event_sequence, created_at, updated_at)
            VALUES (#{userId}, 0, 0, #{now}, #{now})
            """)
    int insertIfAbsent(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Select("""
            SELECT user_id, sequence, completion_event_sequence
            FROM user_guard WHERE user_id = #{userId} FOR UPDATE
            """)
    UserGuard selectForUpdate(@Param("userId") Long userId);

    @Update("""
            UPDATE user_guard SET sequence = #{sequence}, updated_at = #{now}
            WHERE user_id = #{userId}
            """)
    int updateSequence(@Param("userId") Long userId,
                       @Param("sequence") long sequence,
                       @Param("now") LocalDateTime now);
}