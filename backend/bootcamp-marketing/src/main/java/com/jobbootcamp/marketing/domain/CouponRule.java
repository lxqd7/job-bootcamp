package com.jobbootcamp.marketing.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("coupon_rule")
public class CouponRule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleKey;
    private Integer versionNo;
    private String type;
    private String scope;
    private Long targetId;
    private Long thresholdCent;
    private Long amountCent;
    private Integer payRateBps;
    private Long maxDiscountCent;
    private String validityType;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private Integer validDays;
    private Boolean issued;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long revision;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRuleKey() { return ruleKey; }
    public void setRuleKey(String ruleKey) { this.ruleKey = ruleKey; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public Long getThresholdCent() { return thresholdCent; }
    public void setThresholdCent(Long thresholdCent) { this.thresholdCent = thresholdCent; }
    public Long getAmountCent() { return amountCent; }
    public void setAmountCent(Long amountCent) { this.amountCent = amountCent; }
    public Integer getPayRateBps() { return payRateBps; }
    public void setPayRateBps(Integer payRateBps) { this.payRateBps = payRateBps; }
    public Long getMaxDiscountCent() { return maxDiscountCent; }
    public void setMaxDiscountCent(Long maxDiscountCent) { this.maxDiscountCent = maxDiscountCent; }
    public String getValidityType() { return validityType; }
    public void setValidityType(String validityType) { this.validityType = validityType; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDateTime validUntil) { this.validUntil = validUntil; }
    public Integer getValidDays() { return validDays; }
    public void setValidDays(Integer validDays) { this.validDays = validDays; }
    public Boolean getIssued() { return issued; }
    public void setIssued(Boolean issued) { this.issued = issued; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getRevision() { return revision; }
    public void setRevision(Long revision) { this.revision = revision; }
}