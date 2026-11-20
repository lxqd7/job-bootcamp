package com.jobbootcamp.learning.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("entitlement_source")
public class EntitlementSource {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;
    private String sourceKey;
    private Long orderId;
    private Long routeId;
    private LocalDateTime acquiredAt;
    private LocalDateTime paidAt;
    private String state;
    private Long lifecycleVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }
    public LocalDateTime getAcquiredAt() { return acquiredAt; }
    public void setAcquiredAt(LocalDateTime acquiredAt) { this.acquiredAt = acquiredAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Long getLifecycleVersion() { return lifecycleVersion; }
    public void setLifecycleVersion(Long lifecycleVersion) { this.lifecycleVersion = lifecycleVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}