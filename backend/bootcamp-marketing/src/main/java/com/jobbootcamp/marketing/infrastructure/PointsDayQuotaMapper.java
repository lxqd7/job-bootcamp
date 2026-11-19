package com.jobbootcamp.marketing.infrastructure;

import com.jobbootcamp.marketing.domain.PointsDayQuota;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Mapper
public interface PointsDayQuotaMapper {

    @Insert("""
            INSERT IGNORE INTO points_day_quota(user_id, business_date, awarded_points, revision, updated_at)
            VALUES (#{userId}, #{businessDate}, 0, 0, #{now})
            """)
    int insertIfAbsent(@Param("userId") Long userId,
                       @Param("businessDate") LocalDate businessDate,
                       @Param("now") LocalDateTime now);

    @Select("""
            SELECT user_id, business_date, awarded_points, revision, updated_at
            FROM points_day_quota
            WHERE user_id = #{userId} AND business_date = #{businessDate}
            FOR UPDATE
            """)
    PointsDayQuota selectForUpdate(@Param("userId") Long userId,
                                   @Param("businessDate") LocalDate businessDate);

    @Update("""
            UPDATE points_day_quota
            SET awarded_points = awarded_points + #{points}, revision = revision + 1, updated_at = #{now}
            WHERE user_id = #{userId} AND business_date = #{businessDate}
            """)
    int addAwarded(@Param("userId") Long userId,
                   @Param("businessDate") LocalDate businessDate,
                   @Param("points") int points,
                   @Param("now") LocalDateTime now);
}