package com.jobbootcamp.marketing.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface AcquisitionMapper {

    @Select("""
            SELECT user_coupon_id FROM acquisition
            WHERE user_id = #{userId} AND source_type = #{sourceType}
              AND source_item_id = #{sourceItemId} AND request_key = #{requestKey}
            """)
    Long findCouponId(@Param("userId") Long userId,
                      @Param("sourceType") String sourceType,
                      @Param("sourceItemId") Long sourceItemId,
                      @Param("requestKey") String requestKey);

    @Insert("""
            INSERT INTO acquisition(user_id, source_type, source_item_id, user_coupon_id, request_key, created_at)
            VALUES(#{userId}, #{sourceType}, #{sourceItemId}, #{userCouponId}, #{requestKey}, #{now})
            """)
    int insert(@Param("userId") Long userId,
               @Param("sourceType") String sourceType,
               @Param("sourceItemId") Long sourceItemId,
               @Param("userCouponId") Long userCouponId,
               @Param("requestKey") String requestKey,
               @Param("now") LocalDateTime now);
}