package com.jobbootcamp.trade.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("order_course")
public class OrderCourse {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long courseId;
    private Long courseVersion;
    private Long epochId;
    private Long unitPriceCent;
    private Long allocatedCent;
    private Long creditCent;
    private Long couponCent;
    private Long payableCent;
    private Long sourceId;
    private Long sourceGrantId;
    private Boolean isFree;
    private Boolean isNewPaid;
    private String categoryIds;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getCourseVersion() { return courseVersion; }
    public void setCourseVersion(Long courseVersion) { this.courseVersion = courseVersion; }
    public Long getEpochId() { return epochId; }
    public void setEpochId(Long epochId) { this.epochId = epochId; }
    public Long getUnitPriceCent() { return unitPriceCent; }
    public void setUnitPriceCent(Long unitPriceCent) { this.unitPriceCent = unitPriceCent; }
    public Long getAllocatedCent() { return allocatedCent; }
    public void setAllocatedCent(Long allocatedCent) { this.allocatedCent = allocatedCent; }
    public Long getCreditCent() { return creditCent; }
    public void setCreditCent(Long creditCent) { this.creditCent = creditCent; }
    public Long getCouponCent() { return couponCent; }
    public void setCouponCent(Long couponCent) { this.couponCent = couponCent; }
    public Long getPayableCent() { return payableCent; }
    public void setPayableCent(Long payableCent) { this.payableCent = payableCent; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Long getSourceGrantId() { return sourceGrantId; }
    public void setSourceGrantId(Long sourceGrantId) { this.sourceGrantId = sourceGrantId; }
    public Boolean getIsFree() { return isFree; }
    public void setIsFree(Boolean isFree) { this.isFree = isFree; }
    public Boolean getIsNewPaid() { return isNewPaid; }
    public void setIsNewPaid(Boolean isNewPaid) { this.isNewPaid = isNewPaid; }
    public String getCategoryIds() { return categoryIds; }
    public void setCategoryIds(String categoryIds) { this.categoryIds = categoryIds; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}