package com.jobbootcamp.marketing.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.marketing.domain.ActivityItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ActivityItemMapper extends BaseMapper<ActivityItem> {

    @Select("SELECT * FROM activity_item WHERE id = #{id} FOR UPDATE")
    ActivityItem selectForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM activity_item WHERE activity_id = #{activityId} ORDER BY id")
    List<ActivityItem> selectByActivity(@Param("activityId") Long activityId);

    @Update("""
            UPDATE activity_item
            SET issued_count = issued_count + 1, updated_at = #{updatedAt}
            WHERE id = #{id} AND issued_count < stock_total AND enabled = TRUE
            """)
    int incrementIssued(@Param("id") Long id, @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE activity_item
            SET stock_total = #{stockTotal}, per_user_limit = #{perUserLimit},
                enabled = #{enabled}, updated_at = #{updatedAt}, revision = revision + 1
            WHERE id = #{id} AND revision = #{revision}
            """)
    int updateConfig(@Param("id") Long id,
                     @Param("stockTotal") int stockTotal,
                     @Param("perUserLimit") int perUserLimit,
                     @Param("enabled") boolean enabled,
                     @Param("revision") Long revision,
                     @Param("updatedAt") LocalDateTime updatedAt);
}