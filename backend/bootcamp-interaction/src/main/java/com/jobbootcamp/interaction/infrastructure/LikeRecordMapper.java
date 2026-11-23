package com.jobbootcamp.interaction.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.interaction.domain.LikeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface LikeRecordMapper extends BaseMapper<LikeRecord> {

    @Select("""
            SELECT * FROM like_record
            WHERE user_id = #{userId} AND target_type = #{targetType} AND target_id = #{targetId}
            FOR UPDATE
            """)
    LikeRecord selectForUpdate(@Param("userId") Long userId,
                               @Param("targetType") String targetType,
                               @Param("targetId") Long targetId);

    @Update("""
            UPDATE like_record SET active = #{active}, updated_at = #{updatedAt}
            WHERE user_id = #{userId} AND target_type = #{targetType} AND target_id = #{targetId}
            """)
    int updateActive(@Param("userId") Long userId,
                     @Param("targetType") String targetType,
                     @Param("targetId") Long targetId,
                     @Param("active") boolean active,
                     @Param("updatedAt") LocalDateTime updatedAt);

    @Select("""
            SELECT COUNT(*) FROM like_record
            WHERE user_id = #{userId} AND target_type = #{targetType} AND target_id = #{targetId}
              AND active = TRUE
            """)
    int active(@Param("userId") Long userId,
               @Param("targetType") String targetType,
               @Param("targetId") Long targetId);
}