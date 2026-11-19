package com.jobbootcamp.marketing.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.marketing.domain.PointsAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface PointsAccountMapper extends BaseMapper<PointsAccount> {

    @Insert("""
            INSERT IGNORE INTO points_account(user_id, available, debt, revision, created_at, updated_at)
            VALUES (#{userId}, 0, 0, 0, #{now}, #{now})
            """)
    int insertIfAbsent(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Select("SELECT * FROM points_account WHERE user_id = #{userId} FOR UPDATE")
    PointsAccount selectForUpdate(@Param("userId") Long userId);

    @Update("""
            UPDATE points_account
            SET available = #{available}, debt = #{debt}, revision = revision + 1, updated_at = #{now}
            WHERE user_id = #{userId}
            """)
    int updateBalances(@Param("userId") Long userId,
                       @Param("available") long available,
                       @Param("debt") long debt,
                       @Param("now") LocalDateTime now);
}