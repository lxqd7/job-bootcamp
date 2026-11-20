package com.jobbootcamp.trade.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("refund")
public class Refund {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String refundNo;
    private Long orderId;
    private Long paymentId;
    private Long intentId;
    private String kind;
    private String state;
    private Long amountCent;
    private LocalDateTime acceptedAt;
    private Integer progressN;
    private Integer progressD;
    private Long systemReturnPaymentId;
    private String channelRefundNo;
    private String qualification;
    private Long activeOrderId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRefundNo() { return refundNo; }
    public void setRefundNo(String refundNo) { this.refundNo = refundNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public Long getIntentId() { return intentId; }
    public void setIntentId(Long intentId) { this.intentId = intentId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Long getAmountCent() { return amountCent; }
    public void setAmountCent(Long amountCent) { this.amountCent = amountCent; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }
    public Integer getProgressN() { return progressN; }
    public void setProgressN(Integer progressN) { this.progressN = progressN; }
    public Integer getProgressD() { return progressD; }
    public void setProgressD(Integer progressD) { this.progressD = progressD; }
    public Long getSystemReturnPaymentId() { return systemReturnPaymentId; }
    public void setSystemReturnPaymentId(Long systemReturnPaymentId) { this.systemReturnPaymentId = systemReturnPaymentId; }
    public String getChannelRefundNo() { return channelRefundNo; }
    public void setChannelRefundNo(String channelRefundNo) { this.channelRefundNo = channelRefundNo; }
    public String getQualification() { return qualification; }
    public void setQualification(String qualification) { this.qualification = qualification; }
    public Long getActiveOrderId() { return activeOrderId; }
    public void setActiveOrderId(Long activeOrderId) { this.activeOrderId = activeOrderId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}