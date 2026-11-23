package com.jobbootcamp.interaction.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.interaction.domain.ExcellentReward;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExcellentRewardMapper extends BaseMapper<ExcellentReward> {

    @Select("SELECT * FROM excellent_reward WHERE note_id = #{noteId} FOR UPDATE")
    ExcellentReward selectForUpdate(@Param("noteId") Long noteId);
}