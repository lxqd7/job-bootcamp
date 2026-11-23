package com.jobbootcamp.interaction.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.interaction.domain.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    @Select("SELECT * FROM comment WHERE id = #{id} FOR UPDATE")
    Comment selectForUpdate(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM comment WHERE answer_id = #{answerId} AND state = 'VISIBLE'")
    int countVisible(@Param("answerId") Long answerId);
}