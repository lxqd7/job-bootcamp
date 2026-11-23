package com.jobbootcamp.interaction.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("note")
public class Note {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerId;
    private Long courseId;
    private Long lessonId;
    private Long positionMs;
    private String type;
    private Long sourceNoteId;
    private String sourceAuthorSnapshot;
    private String body;
    private String visibility;
    private String state;
    private Long contentRevision;
    private Boolean excellent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getLessonId() { return lessonId; }
    public void setLessonId(Long lessonId) { this.lessonId = lessonId; }
    public Long getPositionMs() { return positionMs; }
    public void setPositionMs(Long positionMs) { this.positionMs = positionMs; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getSourceNoteId() { return sourceNoteId; }
    public void setSourceNoteId(Long sourceNoteId) { this.sourceNoteId = sourceNoteId; }
    public String getSourceAuthorSnapshot() { return sourceAuthorSnapshot; }
    public void setSourceAuthorSnapshot(String sourceAuthorSnapshot) { this.sourceAuthorSnapshot = sourceAuthorSnapshot; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Long getContentRevision() { return contentRevision; }
    public void setContentRevision(Long contentRevision) { this.contentRevision = contentRevision; }
    public Boolean getExcellent() { return excellent; }
    public void setExcellent(Boolean excellent) { this.excellent = excellent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}