package com.jobbootcamp.learning.domain;

import java.time.LocalDateTime;

public class GrantRouteMember {

    private Long sourceId;
    private Long epochId;
    private Long courseId;
    private LocalDateTime qualifiedAt;
    private LocalDateTime createdAt;

    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Long getEpochId() { return epochId; }
    public void setEpochId(Long epochId) { this.epochId = epochId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public LocalDateTime getQualifiedAt() { return qualifiedAt; }
    public void setQualifiedAt(LocalDateTime qualifiedAt) { this.qualifiedAt = qualifiedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}