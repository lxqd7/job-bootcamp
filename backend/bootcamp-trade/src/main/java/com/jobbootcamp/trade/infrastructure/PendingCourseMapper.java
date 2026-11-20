package com.jobbootcamp.trade.infrastructure;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface PendingCourseMapper {

    @Select("SELECT order_id FROM pending_course WHERE user_id = #{userId} AND course_id = #{courseId}")
    Long findOrderId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Insert("""
            INSERT INTO pending_course (user_id, course_id, order_id, created_at)
            VALUES (#{userId}, #{courseId}, #{orderId}, #{now})
            """)
    int insert(@Param("userId") Long userId,
               @Param("courseId") Long courseId,
               @Param("orderId") Long orderId,
               @Param("now") LocalDateTime now);

    @Delete("DELETE FROM pending_course WHERE order_id = #{orderId}")
    int deleteByOrderId(@Param("orderId") Long orderId);
}