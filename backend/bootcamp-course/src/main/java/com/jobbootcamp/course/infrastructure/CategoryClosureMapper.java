package com.jobbootcamp.course.infrastructure;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CategoryClosureMapper {

    @Delete("DELETE FROM category_closure")
    int deleteAll();

    @Insert("INSERT INTO category_closure(ancestor_id, descendant_id, depth) VALUES(#{ancestorId}, #{descendantId}, #{depth})")
    int insertRow(@Param("ancestorId") Long ancestorId,
                  @Param("descendantId") Long descendantId,
                  @Param("depth") int depth);

    @Select("SELECT descendant_id FROM category_closure WHERE ancestor_id = #{ancestorId} ORDER BY descendant_id")
    List<Long> selectDescendantIds(@Param("ancestorId") Long ancestorId);

    @Select("SELECT ancestor_id FROM category_closure WHERE descendant_id = #{descendantId} ORDER BY depth DESC")
    List<Long> selectAncestorIds(@Param("descendantId") Long descendantId);

    @Select("SELECT COUNT(*) FROM category_closure WHERE ancestor_id = #{ancestorId} AND descendant_id = #{descendantId}")
    int countRelation(@Param("ancestorId") Long ancestorId, @Param("descendantId") Long descendantId);
}