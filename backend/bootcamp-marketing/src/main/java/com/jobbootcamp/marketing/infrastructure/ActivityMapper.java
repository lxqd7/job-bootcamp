package com.jobbootcamp.marketing.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.marketing.domain.Activity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ActivityMapper extends BaseMapper<Activity> {

    @Select("SELECT * FROM activity WHERE id = #{id} FOR UPDATE")
    Activity selectForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE activity SET state = #{state}, updated_at = #{updatedAt}, revision = revision + 1
            WHERE id = #{id} AND revision = #{revision}
            """)
    int updateState(@Param("id") Long id,
                    @Param("state") String state,
                    @Param("revision") Long revision,
                    @Param("updatedAt") LocalDateTime updatedAt);
}