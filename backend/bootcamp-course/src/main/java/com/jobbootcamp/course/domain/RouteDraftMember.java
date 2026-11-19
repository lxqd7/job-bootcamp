package com.jobbootcamp.course.domain;

public class RouteDraftMember {

    private Long routeId;
    private Long courseId;
    private Integer sort;

    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
}