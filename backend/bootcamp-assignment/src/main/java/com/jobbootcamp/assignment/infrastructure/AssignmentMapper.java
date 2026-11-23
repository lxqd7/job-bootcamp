package com.jobbootcamp.assignment.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.assignment.domain.Assignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AssignmentMapper extends BaseMapper<Assignment> {

    @Select("SELECT * FROM assignment WHERE id = #{id} FOR UPDATE")
    Assignment selectForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM assignment WHERE formal_scope_key = #{scopeKey}")
    Assignment selectByScope(@Param("scopeKey") String scopeKey);
}