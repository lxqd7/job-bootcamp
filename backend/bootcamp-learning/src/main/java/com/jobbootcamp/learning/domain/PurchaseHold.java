package com.jobbootcamp.learning.domain;

import java.time.LocalDateTime;

/**
 * 订单对既有课程来源的短生命周期占用；支付成功后 COMMITTED，取消/失败 RELEASED。
 */
public class PurchaseHold {

    private Long orderId;
    private Long userId;
    private Long courseId;
    private Long sourceId;
    private Long sourceGrantId;
    private String state;
    private LocalDateTime preparedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Long getSourceGrantId() { return sourceGrantId; }
    public void setSourceGrantId(Long sourceGrantId) { this.sourceGrantId = sourceGrantId; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public LocalDateTime getPreparedAt() { return preparedAt; }
    public void setPreparedAt(LocalDateTime preparedAt) { this.preparedAt = preparedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}