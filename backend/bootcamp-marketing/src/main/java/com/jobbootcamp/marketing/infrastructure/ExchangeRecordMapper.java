package com.jobbootcamp.marketing.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.marketing.domain.ExchangeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExchangeRecordMapper extends BaseMapper<ExchangeRecord> {

    @Select("SELECT * FROM exchange_record WHERE user_id = #{userId} AND request_key = #{requestKey} LIMIT 1")
    ExchangeRecord findByUserAndRequestKey(@Param("userId") Long userId,
                                           @Param("requestKey") String requestKey);
}