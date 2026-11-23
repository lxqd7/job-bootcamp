package com.jobbootcamp.interaction.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.interaction.domain.Note;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NoteMapper extends BaseMapper<Note> {

    @Select("SELECT * FROM note WHERE id = #{id} FOR UPDATE")
    Note selectForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM note WHERE owner_id = #{ownerId} AND source_note_id = #{sourceNoteId} LIMIT 1")
    Note selectByOwnerAndSource(@Param("ownerId") Long ownerId,
                                @Param("sourceNoteId") Long sourceNoteId);
}