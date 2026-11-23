package com.jobbootcamp.assignment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("assignment_route")
public class AssignmentRoute {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assignmentId;
    private Long routeId;
    private Long epochId;
    private LocalDateTime boundAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAssignmentId() { return assignmentId; }
    public void setAssignmentId(Long assignmentId) { this.assignmentId = assignmentId; }
    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }
    public Long getEpochId() { return epochId; }
    public void setEpochId(Long epochId) { this.epochId = epochId; }
    public LocalDateTime getBoundAt() { return boundAt; }
    public void setBoundAt(LocalDateTime boundAt) { this.boundAt = boundAt; }
}