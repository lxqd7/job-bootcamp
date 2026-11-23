package com.jobbootcamp.interaction.domain;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("note_version")
public class NoteVersion {

    private Long noteId;
    private Long contentRevision;
    private String body;
    private String visibility;
    private LocalDateTime createdAt;

    public Long getNoteId() { return noteId; }
    public void setNoteId(Long noteId) { this.noteId = noteId; }
    public Long getContentRevision() { return contentRevision; }
    public void setContentRevision(Long contentRevision) { this.contentRevision = contentRevision; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}