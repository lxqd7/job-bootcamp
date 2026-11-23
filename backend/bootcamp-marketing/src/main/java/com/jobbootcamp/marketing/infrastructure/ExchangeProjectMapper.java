package com.jobbootcamp.marketing.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.marketing.domain.ExchangeProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExchangeProjectMapper extends BaseMapper<ExchangeProject> {

    @Select("SELECT * FROM exchange_project WHERE id = #{id} FOR UPDATE")
    ExchangeProject selectForUpdate(@Param("id") Long id);
}