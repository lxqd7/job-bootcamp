package com.jobbootcamp.assignment.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.assignment.domain.Attempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AttemptMapper extends BaseMapper<Attempt> {

    @Select("SELECT * FROM attempt WHERE id = #{id} FOR UPDATE")
    Attempt selectForUpdate(@Param("id") Long id);
}