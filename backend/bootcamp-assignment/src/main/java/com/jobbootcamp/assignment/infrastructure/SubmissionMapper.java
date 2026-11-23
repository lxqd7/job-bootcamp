package com.jobbootcamp.assignment.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.assignment.domain.Submission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SubmissionMapper extends BaseMapper<Submission> {

    @Select("SELECT * FROM submission WHERE attempt_id = #{attemptId}")
    Submission selectByAttempt(@Param("attemptId") Long attemptId);

    @Select("""
            SELECT * FROM submission
            WHERE user_id = #{userId} AND attempt_id = #{attemptId} AND request_key = #{requestKey}
            """)
    Submission selectByRequest(@Param("userId") Long userId,
                               @Param("attemptId") Long attemptId,
                               @Param("requestKey") String requestKey);
}