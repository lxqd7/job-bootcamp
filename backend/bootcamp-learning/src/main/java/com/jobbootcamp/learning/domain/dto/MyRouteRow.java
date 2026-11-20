package com.jobbootcamp.learning.domain.dto;

import java.time.LocalDateTime;

public class MyRouteRow {

    private Long routeId;
    private Long sourceId;
    private Boolean futureCourses;
    private LocalDateTime acquiredAt;

    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Boolean getFutureCourses() { return futureCourses; }
    public void setFutureCourses(Boolean futureCourses) { this.futureCourses = futureCourses; }
    public LocalDateTime getAcquiredAt() { return acquiredAt; }
    public void setAcquiredAt(LocalDateTime acquiredAt) { this.acquiredAt = acquiredAt; }
}