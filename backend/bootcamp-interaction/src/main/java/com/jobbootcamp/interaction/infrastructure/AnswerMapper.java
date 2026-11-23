package com.jobbootcamp.interaction.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.interaction.domain.Answer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AnswerMapper extends BaseMapper<Answer> {

    @Select("SELECT * FROM answer WHERE id = #{id} FOR UPDATE")
    Answer selectForUpdate(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM answer WHERE question_id = #{questionId} AND state = 'VISIBLE'")
    int countVisible(@Param("questionId") Long questionId);
}