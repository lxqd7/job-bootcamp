package com.jobbootcamp.assignment.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.assignment.domain.AiUserSlot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AiUserSlotMapper extends BaseMapper<AiUserSlot> {

    @Insert("""
            INSERT IGNORE INTO ai_user_slot(user_id, active_task_id, updated_at)
            VALUES(#{userId}, NULL, #{now})
            """)
    int insertIfAbsent(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Select("SELECT * FROM ai_user_slot WHERE user_id = #{userId} FOR UPDATE")
    AiUserSlot selectForUpdate(@Param("userId") Long userId);

    @Update("""
            UPDATE ai_user_slot SET active_task_id = #{taskId}, updated_at = #{now}
            WHERE user_id = #{userId} AND active_task_id IS NULL
            """)
    int occupy(@Param("userId") Long userId, @Param("taskId") Long taskId,
               @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_user_slot SET active_task_id = NULL, updated_at = #{now}
            WHERE user_id = #{userId} AND active_task_id = #{taskId}
            """)
    int release(@Param("userId") Long userId, @Param("taskId") Long taskId,
                @Param("now") LocalDateTime now);
}