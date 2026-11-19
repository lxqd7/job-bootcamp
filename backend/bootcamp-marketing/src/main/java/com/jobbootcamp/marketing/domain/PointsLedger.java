package com.jobbootcamp.marketing.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("points_ledger")
public class PointsLedger {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;
    private String businessKey;
    private Long rewardPoints;
    private Long balanceDelta;
    private Long debtDelta;
    private Long rankDelta;
    private Long balanceAfter;
    private Long debtAfter;
    private LocalDate businessDate;
    private String businessMonth;
    private LocalDateTime occurredAt;
    private Long originalLedgerId;
    private String resultCode;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
    public Long getRewardPoints() { return rewardPoints; }
    public void setRewardPoints(Long rewardPoints) { this.rewardPoints = rewardPoints; }
    public Long getBalanceDelta() { return balanceDelta; }
    public void setBalanceDelta(Long balanceDelta) { this.balanceDelta = balanceDelta; }
    public Long getDebtDelta() { return debtDelta; }
    public void setDebtDelta(Long debtDelta) { this.debtDelta = debtDelta; }
    public Long getRankDelta() { return rankDelta; }
    public void setRankDelta(Long rankDelta) { this.rankDelta = rankDelta; }
    public Long getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(Long balanceAfter) { this.balanceAfter = balanceAfter; }
    public Long getDebtAfter() { return debtAfter; }
    public void setDebtAfter(Long debtAfter) { this.debtAfter = debtAfter; }
    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }
    public String getBusinessMonth() { return businessMonth; }
    public void setBusinessMonth(String businessMonth) { this.businessMonth = businessMonth; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public Long getOriginalLedgerId() { return originalLedgerId; }
    public void setOriginalLedgerId(Long originalLedgerId) { this.originalLedgerId = originalLedgerId; }
    public String getResultCode() { return resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}