package com.jobbootcamp.trade.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.trade.domain.OrderCourse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrderCourseMapper extends BaseMapper<OrderCourse> {

    @Update("""
            UPDATE order_course
            SET source_id = #{sourceId}, source_grant_id = #{sourceGrantId}
            WHERE order_id = #{orderId} AND course_id = #{courseId}
              AND (source_id IS NULL OR source_id = #{sourceId})
            """)
    int updateGrant(@Param("orderId") Long orderId,
                    @Param("courseId") Long courseId,
                    @Param("sourceId") Long sourceId,
                    @Param("sourceGrantId") Long sourceGrantId);

    @Select("""
            SELECT COUNT(*) FROM order_course
            WHERE order_id = #{orderId} AND source_id IS NULL AND is_new_paid = TRUE
            """)
    int countMissingGrant(@Param("orderId") Long orderId);
}