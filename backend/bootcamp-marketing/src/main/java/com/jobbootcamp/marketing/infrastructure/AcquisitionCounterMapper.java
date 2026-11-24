package com.jobbootcamp.marketing.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AcquisitionCounterMapper {

    @Insert("""
            INSERT IGNORE INTO acquisition_counter(user_id, source_type, source_item_id, total_count, updated_at)
            VALUES(#{userId}, #{sourceType}, #{sourceItemId}, 0, #{now})
            """)
    int insertIfAbsent(@Param("userId") Long userId,
                       @Param("sourceType") String sourceType,
                       @Param("sourceItemId") Long sourceItemId,
                       @Param("now") LocalDateTime now);

    @Select("""
            SELECT total_count FROM acquisition_counter
            WHERE user_id = #{userId} AND source_type = #{sourceType} AND source_item_id = #{sourceItemId}
            FOR UPDATE
            """)
    Integer selectForUpdate(@Param("userId") Long userId,
                            @Param("sourceType") String sourceType,
                            @Param("sourceItemId") Long sourceItemId);

    @Select("""
            SELECT COALESCE(MAX(total_count), 0) FROM acquisition_counter
            WHERE user_id = #{userId} AND source_type = #{sourceType} AND source_item_id = #{sourceItemId}
            """)
    Integer selectCount(@Param("userId") Long userId,
                        @Param("sourceType") String sourceType,
                        @Param("sourceItemId") Long sourceItemId);

    @Update("""
            UPDATE acquisition_counter SET total_count = total_count + 1, updated_at = #{now}
            WHERE user_id = #{userId} AND source_type = #{sourceType} AND source_item_id = #{sourceItemId}
            """)
    int increment(@Param("userId") Long userId,
                  @Param("sourceType") String sourceType,
                  @Param("sourceItemId") Long sourceItemId,
                  @Param("now") LocalDateTime now);
}