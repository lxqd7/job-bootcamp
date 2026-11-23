package com.jobbootcamp.interaction.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.interaction.domain.ModerationAction;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModerationActionMapper extends BaseMapper<ModerationAction> {
}