package com.jobbootcamp.course.domain;

public class RouteMemberCurrent {

    private Long routeId;
    private Long courseId;
    private Long epochId;
    private Integer sort;

    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getEpochId() { return epochId; }
    public void setEpochId(Long epochId) { this.epochId = epochId; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
}