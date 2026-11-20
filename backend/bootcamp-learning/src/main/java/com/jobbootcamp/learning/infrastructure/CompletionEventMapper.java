package com.jobbootcamp.learning.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.learning.domain.CompletionEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CompletionEventMapper extends BaseMapper<CompletionEvent> {

    @Select("""
            SELECT * FROM completion_event
            WHERE user_id = #{userId} AND event_sequence > #{afterSequence}
            ORDER BY event_sequence
            LIMIT #{limit}
            """)
    List<CompletionEvent> selectAfter(@Param("userId") Long userId,
                                      @Param("afterSequence") long afterSequence,
                                      @Param("limit") int limit);
}