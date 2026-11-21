package com.jobbootcamp.learning.infrastructure;

import com.jobbootcamp.learning.domain.GrantRouteMember;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface GrantRouteMemberMapper {

    @Insert("""
            INSERT IGNORE INTO grant_route_member
                (source_id, epoch_id, course_id, qualified_at, created_at)
            VALUES (#{sourceId}, #{epochId}, #{courseId}, #{qualifiedAt}, #{createdAt})
            """)
    int insertIfAbsent(@Param("sourceId") Long sourceId,
                       @Param("epochId") Long epochId,
                       @Param("courseId") Long courseId,
                       @Param("qualifiedAt") LocalDateTime qualifiedAt,
                       @Param("createdAt") LocalDateTime createdAt);
}