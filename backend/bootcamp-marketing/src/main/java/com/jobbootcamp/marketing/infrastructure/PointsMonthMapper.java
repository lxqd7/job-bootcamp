package com.jobbootcamp.marketing.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PointsMonthMapper {

    @Insert("""
            INSERT INTO points_month(month, user_id, score, revision)
            VALUES (#{month}, #{userId}, #{scoreDelta}, 1) AS incoming
            ON DUPLICATE KEY UPDATE
                score = points_month.score + incoming.score,
                revision = points_month.revision + 1
            """)
    int addScore(@Param("month") String month,
                 @Param("userId") Long userId,
                 @Param("scoreDelta") long scoreDelta);

    @Select("SELECT COALESCE(MAX(score), 0) FROM points_month WHERE month = #{month} AND user_id = #{userId}")
    Long selectScore(@Param("month") String month, @Param("userId") Long userId);

    @Update("""
            UPDATE points_month
            SET score = GREATEST(0, score - #{scoreDelta}), revision = revision + 1
            WHERE month = #{month} AND user_id = #{userId}
            """)
    int subtractScore(@Param("month") String month,
                      @Param("userId") Long userId,
                      @Param("scoreDelta") long scoreDelta);

    @Insert("""
            INSERT INTO points_month(month, user_id, score, revision)
            VALUES (#{month}, #{userId}, #{score}, 1) AS incoming
            ON DUPLICATE KEY UPDATE
                score = incoming.score,
                revision = points_month.revision + 1
            """)
    int setScore(@Param("month") String month,
                 @Param("userId") Long userId,
                 @Param("score") long score);
}