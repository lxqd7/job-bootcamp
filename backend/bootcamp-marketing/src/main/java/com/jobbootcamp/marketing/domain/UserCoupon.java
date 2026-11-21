package com.jobbootcamp.marketing.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("user_coupon")
public class UserCoupon {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long ruleId;
    private String sourceType;
    private Long sourceGroupId;
    private Long sourceItemId;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private String status;
    private Long lockedOrderId;
    private Long usedOrderId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long revision;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceGroupId() { return sourceGroupId; }
    public void setSourceGroupId(Long sourceGroupId) { this.sourceGroupId = sourceGroupId; }
    public Long getSourceItemId() { return sourceItemId; }
    public void setSourceItemId(Long sourceItemId) { this.sourceItemId = sourceItemId; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDateTime validUntil) { this.validUntil = validUntil; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getLockedOrderId() { return lockedOrderId; }
    public void setLockedOrderId(Long lockedOrderId) { this.lockedOrderId = lockedOrderId; }
    public Long getUsedOrderId() { return usedOrderId; }
    public void setUsedOrderId(Long usedOrderId) { this.usedOrderId = usedOrderId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getRevision() { return revision; }
    public void setRevision(Long revision) { this.revision = revision; }
}