package com.jobbootcamp.marketing.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.marketing.domain.StudentProfile;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface StudentProfileMapper extends BaseMapper<StudentProfile> {

    @Insert("""
            INSERT INTO student_profile(user_id, nickname, role_code, user_revision, updated_at)
            VALUES (#{userId}, #{nickname}, #{roleCode}, #{userRevision}, #{updatedAt}) AS incoming
            ON DUPLICATE KEY UPDATE
                nickname = incoming.nickname,
                role_code = incoming.role_code,
                user_revision = incoming.user_revision,
                updated_at = incoming.updated_at
            """)
    int upsert(@Param("userId") Long userId,
               @Param("nickname") String nickname,
               @Param("roleCode") String roleCode,
               @Param("userRevision") long userRevision,
               @Param("updatedAt") LocalDateTime updatedAt);
}