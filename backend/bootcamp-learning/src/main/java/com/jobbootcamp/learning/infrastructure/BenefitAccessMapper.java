package com.jobbootcamp.learning.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BenefitAccessMapper {

    /**
     * 路线福利资格：有效路线来源 + 该题目绑定 epoch 的课程 grant。
     * 历史 epoch 的 grant 不会因路线移除课程而删除，因此老学员保留访问。
     */
    @Select("""
            SELECT re.source_id
            FROM route_entitlement re
            JOIN entitlement_source es ON es.id = re.source_id
            JOIN grant_route_member gr ON gr.source_id = re.source_id
            WHERE re.user_id = #{userId}
              AND re.route_id = #{routeId}
              AND re.future_courses = TRUE
              AND es.state = 'ACTIVE'
              AND gr.epoch_id = #{epochId}
              AND gr.course_id = #{courseId}
            LIMIT 1
            """)
    Long selectBenefitSource(@Param("userId") Long userId,
                             @Param("courseId") Long courseId,
                             @Param("routeId") Long routeId,
                             @Param("epochId") Long epochId);
}