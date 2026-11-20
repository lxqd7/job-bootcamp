package com.jobbootcamp.learning.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.learning.domain.FreeClaimReceipt;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FreeClaimReceiptMapper extends BaseMapper<FreeClaimReceipt> {
}