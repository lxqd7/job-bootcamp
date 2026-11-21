package com.jobbootcamp.learning.domain;

import java.time.LocalDateTime;

public class SourceSelectionRow {

    private Long courseId;
    private Long sourceId;
    private Long sourceGrantId;
    private String sourceType;
    private Long sourceOrderId;
    private LocalDateTime grantedAt;

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Long getSourceGrantId() { return sourceGrantId; }
    public void setSourceGrantId(Long sourceGrantId) { this.sourceGrantId = sourceGrantId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceOrderId() { return sourceOrderId; }
    public void setSourceOrderId(Long sourceOrderId) { this.sourceOrderId = sourceOrderId; }
    public LocalDateTime getGrantedAt() { return grantedAt; }
    public void setGrantedAt(LocalDateTime grantedAt) { this.grantedAt = grantedAt; }
}