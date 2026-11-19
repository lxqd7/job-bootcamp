package com.jobbootcamp.marketing.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.marketing.domain.PointsLedger;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PointsLedgerMapper extends BaseMapper<PointsLedger> {
}