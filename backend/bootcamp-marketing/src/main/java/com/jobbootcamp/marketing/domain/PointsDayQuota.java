package com.jobbootcamp.marketing.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class PointsDayQuota {

    private Long userId;
    private LocalDate businessDate;
    private Integer awardedPoints;
    private Long revision;
    private LocalDateTime updatedAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }
    public Integer getAwardedPoints() { return awardedPoints; }
    public void setAwardedPoints(Integer awardedPoints) { this.awardedPoints = awardedPoints; }
    public Long getRevision() { return revision; }
    public void setRevision(Long revision) { this.revision = revision; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}