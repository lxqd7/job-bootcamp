package com.jobbootcamp.user.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.user.domain.RefreshHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RefreshHistoryMapper extends BaseMapper<RefreshHistory> {
}