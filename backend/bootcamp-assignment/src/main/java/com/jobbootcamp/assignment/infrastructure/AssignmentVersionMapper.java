package com.jobbootcamp.assignment.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.assignment.domain.AssignmentVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AssignmentVersionMapper extends BaseMapper<AssignmentVersion> {

    @Select("SELECT COALESCE(MAX(version_no), 0) FROM assignment_version WHERE assignment_id = #{assignmentId}")
    int maxVersionNo(@Param("assignmentId") Long assignmentId);
}