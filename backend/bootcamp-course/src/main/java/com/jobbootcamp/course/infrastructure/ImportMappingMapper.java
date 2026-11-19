package com.jobbootcamp.course.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.course.domain.ImportMapping;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ImportMappingMapper extends BaseMapper<ImportMapping> {
}