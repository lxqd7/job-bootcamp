package com.jobbootcamp.marketing.infrastructure;

import com.jobbootcamp.marketing.domain.BoardRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BoardMapper {

    String STUDENT_FILTER = " AND (sp.role_code = 'STUDENT' OR sp.role_code IS NULL) ";

    @Select("SELECT user_id FROM points_month WHERE month = #{month} AND score > 0 ORDER BY user_id")
    List<Long> selectParticipantIds(@Param("month") String month);

    @Select("""
            SELECT pm.user_id, COALESCE(sp.nickname, CONCAT('学员', pm.user_id)) AS nickname,
                   pm.score, ROW_NUMBER() OVER (ORDER BY pm.score DESC, pm.user_id ASC) AS rank_no
            FROM points_month pm
            LEFT JOIN student_profile sp ON sp.user_id = pm.user_id
            WHERE pm.month = #{month} AND pm.score > 0
            """ + STUDENT_FILTER + """
            ORDER BY pm.score DESC, pm.user_id ASC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<BoardRow> selectPage(@Param("month") String month,
                              @Param("limit") int limit,
                              @Param("offset") int offset);

    @Select("""
            SELECT COUNT(*)
            FROM points_month pm
            LEFT JOIN student_profile sp ON sp.user_id = pm.user_id
            WHERE pm.month = #{month} AND pm.score > 0
            """ + STUDENT_FILTER)
    long count(@Param("month") String month);

    @Select("""
            SELECT COALESCE(SUM(revision), 0)
            FROM points_month
            WHERE month = #{month}
            """)
    long cacheRevision(@Param("month") String month);

    @Select("SELECT COALESCE(SUM(user_revision), 0) FROM student_profile")
    long profileRevision();

    @Select("""
            SELECT COUNT(*) + 1
            FROM points_month pm
            LEFT JOIN student_profile sp ON sp.user_id = pm.user_id
            WHERE pm.month = #{month} AND pm.score > 0
            """ + STUDENT_FILTER + """
            AND (pm.score > #{score} OR (pm.score = #{score} AND pm.user_id < #{userId}))
            """)
    long myRank(@Param("month") String month,
                @Param("score") long score,
                @Param("userId") long userId);

    @Select("""
            SELECT COALESCE(MAX(score), 0)
            FROM points_month
            WHERE month = #{month} AND user_id = #{userId}
            """)
    long myScore(@Param("month") String month, @Param("userId") long userId);

    @Select("""
            SELECT pm.user_id, COALESCE(sp.nickname, CONCAT('学员', pm.user_id)) AS nickname,
                   pm.score, ROW_NUMBER() OVER (ORDER BY pm.score DESC, pm.user_id ASC) AS rank_no
            FROM points_month pm
            LEFT JOIN student_profile sp ON sp.user_id = pm.user_id
            WHERE pm.month = #{month} AND pm.score > 0
            """ + STUDENT_FILTER + """
            ORDER BY pm.score DESC, pm.user_id ASC
            LIMIT 10000
            """)
    List<BoardRow> selectAll(@Param("month") String month);
}