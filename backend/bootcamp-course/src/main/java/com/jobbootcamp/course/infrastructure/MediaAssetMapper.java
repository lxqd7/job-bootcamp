package com.jobbootcamp.course.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.course.domain.MediaAsset;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MediaAssetMapper extends BaseMapper<MediaAsset> {
}