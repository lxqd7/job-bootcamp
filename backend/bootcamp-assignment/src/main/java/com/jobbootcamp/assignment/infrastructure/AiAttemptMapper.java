package com.jobbootcamp.assignment.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.assignment.domain.AiAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiAttemptMapper extends BaseMapper<AiAttempt> {

    @Select("SELECT COUNT(*) FROM ai_attempt WHERE task_id = #{taskId}")
    int countByTask(@Param("taskId") Long taskId);

    @Select("SELECT COALESCE(MAX(call_index), 0) FROM ai_attempt WHERE task_id = #{taskId}")
    int maxCallIndex(@Param("taskId") Long taskId);

    @Select("SELECT * FROM ai_attempt WHERE task_id = #{taskId} ORDER BY call_index")
    List<AiAttempt> selectByTask(@Param("taskId") Long taskId);

    @Update("""
            UPDATE ai_attempt SET state = #{state}, http_status = #{httpStatus},
                error_class = #{errorClass}, response_hash = #{responseHash},
                finished_at = #{finishedAt}
            WHERE id = #{id} AND state = 'PLANNED'
            """)
    int finish(@Param("id") Long id,
               @Param("state") String state,
               @Param("httpStatus") Integer httpStatus,
               @Param("errorClass") String errorClass,
               @Param("responseHash") String responseHash,
               @Param("finishedAt") LocalDateTime finishedAt);
}