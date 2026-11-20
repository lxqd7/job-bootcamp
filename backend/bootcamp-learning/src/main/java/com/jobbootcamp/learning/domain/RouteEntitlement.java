package com.jobbootcamp.learning.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("route_entitlement")
public class RouteEntitlement {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long routeId;
    private Long sourceId;
    private Boolean futureCourses;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Boolean getFutureCourses() { return futureCourses; }
    public void setFutureCourses(Boolean futureCourses) { this.futureCourses = futureCourses; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}