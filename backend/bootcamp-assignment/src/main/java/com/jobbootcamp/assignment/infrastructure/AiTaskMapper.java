package com.jobbootcamp.assignment.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.assignment.domain.AiTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiTaskMapper extends BaseMapper<AiTask> {

    @Select("SELECT * FROM ai_task WHERE submission_id = #{submissionId}")
    AiTask selectBySubmission(@Param("submissionId") Long submissionId);

    @Select("SELECT * FROM ai_task WHERE id = #{id} FOR UPDATE")
    AiTask selectForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM ai_task WHERE state = 'QUEUED' ORDER BY id LIMIT #{limit}")
    List<AiTask> selectQueued(@Param("limit") int limit);

    @Update("""
            UPDATE ai_task SET state = #{target}, updated_at = #{now}
            WHERE id = #{id} AND state = #{expected}
            """)
    int transition(@Param("id") Long id,
                   @Param("expected") String expected,
                   @Param("target") String target,
                   @Param("now") LocalDateTime now);
}