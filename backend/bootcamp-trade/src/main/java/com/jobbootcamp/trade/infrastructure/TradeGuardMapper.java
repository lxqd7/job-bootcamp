package com.jobbootcamp.trade.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface TradeGuardMapper {

    @Insert("""
            INSERT IGNORE INTO user_trade_guard (user_id, confirm_sequence, created_at, updated_at)
            VALUES (#{userId}, 0, #{now}, #{now})
            """)
    int ensure(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Select("SELECT confirm_sequence FROM user_trade_guard WHERE user_id = #{userId} FOR UPDATE")
    Long lock(@Param("userId") Long userId);

    @Update("""
            UPDATE user_trade_guard
            SET confirm_sequence = confirm_sequence + 1, updated_at = #{now}
            WHERE user_id = #{userId}
            """)
    int increment(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}