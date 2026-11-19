package com.jobbootcamp.marketing.infrastructure;

import com.jobbootcamp.marketing.domain.SignRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface SignRecordMapper {

    @Select("""
            SELECT user_id, business_date, continuous_days, base_points, bonus_points, occurred_at, created_at
            FROM sign_record WHERE user_id = #{userId} AND business_date = #{businessDate}
            """)
    SignRecord select(@Param("userId") Long userId, @Param("businessDate") LocalDate businessDate);

    @Insert("""
            INSERT INTO sign_record(user_id, business_date, continuous_days, base_points, bonus_points, occurred_at, created_at)
            VALUES (#{userId}, #{businessDate}, #{continuousDays}, #{basePoints}, #{bonusPoints}, #{occurredAt}, #{createdAt})
            """)
    int insert(SignRecord record);

    @Select("SELECT COUNT(*) FROM sign_record WHERE user_id = #{userId} AND business_date BETWEEN #{from} AND #{to}")
    long countBetween(@Param("userId") Long userId,
                      @Param("from") LocalDate from,
                      @Param("to") LocalDate to);

    @Select("""
            SELECT user_id, business_date, continuous_days, base_points, bonus_points, occurred_at, created_at
            FROM sign_record WHERE user_id = #{userId} AND business_date BETWEEN #{from} AND #{to}
            ORDER BY business_date
            """)
    List<SignRecord> selectBetween(@Param("userId") Long userId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);
}