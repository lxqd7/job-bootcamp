package com.jobbootcamp.trade.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.trade.domain.ChannelReceipt;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChannelReceiptMapper extends BaseMapper<ChannelReceipt> {
}