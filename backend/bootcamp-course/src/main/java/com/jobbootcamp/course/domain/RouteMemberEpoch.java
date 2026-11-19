package com.jobbootcamp.course.domain;

import java.time.LocalDateTime;

public class RouteMemberEpoch {

    private Long id;
    private Long routeId;
    private Long courseId;
    private Long joinedVersion;
    private Long removedVersion;
    private LocalDateTime joinedAt;
    private LocalDateTime removedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getJoinedVersion() { return joinedVersion; }
    public void setJoinedVersion(Long joinedVersion) { this.joinedVersion = joinedVersion; }
    public Long getRemovedVersion() { return removedVersion; }
    public void setRemovedVersion(Long removedVersion) { this.removedVersion = removedVersion; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
    public LocalDateTime getRemovedAt() { return removedAt; }
    public void setRemovedAt(LocalDateTime removedAt) { this.removedAt = removedAt; }
}