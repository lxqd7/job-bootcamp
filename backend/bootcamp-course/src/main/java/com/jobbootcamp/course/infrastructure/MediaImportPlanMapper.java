package com.jobbootcamp.course.infrastructure;

import com.jobbootcamp.course.domain.MediaImportPlanItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MediaImportPlanMapper {

    @Select("SELECT batch_id, old_id, app_id, file_id, input_hash, action FROM media_import_plan WHERE batch_id = #{batchId} ORDER BY old_id")
    List<MediaImportPlanItem> selectByBatch(@Param("batchId") String batchId);

    @Insert("""
            INSERT INTO media_import_plan(batch_id, old_id, app_id, file_id, input_hash, action)
            VALUES(#{batchId}, #{oldId}, #{appId}, #{fileId}, #{inputHash}, #{action})
            """)
    int insertItem(MediaImportPlanItem item);

    @Delete("DELETE FROM media_import_plan WHERE batch_id = #{batchId}")
    int deleteByBatch(@Param("batchId") String batchId);
}