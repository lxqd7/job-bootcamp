package com.jobbootcamp.assignment.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.assignment.domain.Attempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AttemptMapper extends BaseMapper<Attempt> {

    @Select("SELECT * FROM attempt WHERE id = #{id} FOR UPDATE")
    Attempt selectForUpdate(@Param("id") Long id);

    @Select("""
            SELECT * FROM attempt
            WHERE user_id = #{userId} AND assignment_id = #{assignmentId}
              AND version_id = #{versionId} AND status = 'OPEN'
            ORDER BY id DESC LIMIT 1
            """)
    Attempt selectLatestOpen(@Param("userId") Long userId,
                             @Param("assignmentId") Long assignmentId,
                             @Param("versionId") Long versionId);
}