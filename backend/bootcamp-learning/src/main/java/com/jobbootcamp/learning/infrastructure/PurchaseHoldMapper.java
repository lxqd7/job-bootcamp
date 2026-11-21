package com.jobbootcamp.learning.infrastructure;

import com.jobbootcamp.learning.domain.PurchaseHold;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学习侧购买预留的持久化；唯一键 (order_id, course_id) 让重复 prepare 不重复占来源。
 */
@Mapper
public interface PurchaseHoldMapper {

    @Insert("""
            INSERT IGNORE INTO purchase_hold
                (order_id, user_id, course_id, source_id, source_grant_id, state, prepared_at, created_at, updated_at)
            VALUES (#{orderId}, #{userId}, #{courseId}, #{sourceId}, #{sourceGrantId}, 'PREPARED',
                    #{now}, #{now}, #{now})
            """)
    int insertIfAbsent(@Param("orderId") Long orderId,
                       @Param("userId") Long userId,
                       @Param("courseId") Long courseId,
                       @Param("sourceId") Long sourceId,
                       @Param("sourceGrantId") Long sourceGrantId,
                       @Param("now") LocalDateTime now);

    @Select("SELECT * FROM purchase_hold WHERE order_id = #{orderId} ORDER BY course_id")
    List<PurchaseHold> selectByOrder(@Param("orderId") Long orderId);

    @Update("""
            UPDATE purchase_hold SET state = #{target}, updated_at = #{now}
            WHERE order_id = #{orderId} AND state = #{expected}
            """)
    int updateState(@Param("orderId") Long orderId,
                    @Param("expected") String expected,
                    @Param("target") String target,
                    @Param("now") LocalDateTime now);
}