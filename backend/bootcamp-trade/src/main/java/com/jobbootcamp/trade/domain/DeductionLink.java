package com.jobbootcamp.trade.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 新订单对既有来源订单的抵扣依赖；退款前必须保留该边用于关联校验。
 */
@TableName("deduction_link")
public class DeductionLink {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sourceOrderId;
    private Long dependentOrderId;
    private Long courseId;
    private Long sourceId;
    private Long sourceGrantId;
    private String state;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSourceOrderId() { return sourceOrderId; }
    public void setSourceOrderId(Long sourceOrderId) { this.sourceOrderId = sourceOrderId; }
    public Long getDependentOrderId() { return dependentOrderId; }
    public void setDependentOrderId(Long dependentOrderId) { this.dependentOrderId = dependentOrderId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Long getSourceGrantId() { return sourceGrantId; }
    public void setSourceGrantId(Long sourceGrantId) { this.sourceGrantId = sourceGrantId; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}