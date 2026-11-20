package com.jobbootcamp.trade.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface OrderLessonMapper {

    @Insert("""
            INSERT INTO order_lesson (order_id, course_id, lesson_id, duration_ms, created_at)
            VALUES (#{orderId}, #{courseId}, #{lessonId}, #{durationMs}, #{now})
            """)
    int insert(@Param("orderId") Long orderId,
               @Param("courseId") Long courseId,
               @Param("lessonId") Long lessonId,
               @Param("durationMs") Long durationMs,
               @Param("now") LocalDateTime now);
}