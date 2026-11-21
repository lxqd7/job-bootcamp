package com.jobbootcamp.trade.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("trade_order")
public class TradeOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private String productType;
    private Long productId;
    private String state;
    private String fulfillmentState;
    private Long paymentSequence;
    private String quoteId;
    private String pricingHash;
    private String couponMode;
    private String couponState;
    private Long totalCent;
    private Long creditCent;
    private Long discountCent;
    private Long payableCent;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime cancelRequestedAt;
    private LocalDateTime paidAt;
    private String snapshotHash;
    private String snapshot;
    private String requestKey;
    private String requestHash;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getFulfillmentState() { return fulfillmentState; }
    public void setFulfillmentState(String fulfillmentState) { this.fulfillmentState = fulfillmentState; }
    public Long getPaymentSequence() { return paymentSequence; }
    public void setPaymentSequence(Long paymentSequence) { this.paymentSequence = paymentSequence; }
    public String getQuoteId() { return quoteId; }
    public void setQuoteId(String quoteId) { this.quoteId = quoteId; }
    public String getPricingHash() { return pricingHash; }
    public void setPricingHash(String pricingHash) { this.pricingHash = pricingHash; }
    public String getCouponMode() { return couponMode; }
    public void setCouponMode(String couponMode) { this.couponMode = couponMode; }
    public String getCouponState() { return couponState; }
    public void setCouponState(String couponState) { this.couponState = couponState; }
    public Long getTotalCent() { return totalCent; }
    public void setTotalCent(Long totalCent) { this.totalCent = totalCent; }
    public Long getCreditCent() { return creditCent; }
    public void setCreditCent(Long creditCent) { this.creditCent = creditCent; }
    public Long getDiscountCent() { return discountCent; }
    public void setDiscountCent(Long discountCent) { this.discountCent = discountCent; }
    public Long getPayableCent() { return payableCent; }
    public void setPayableCent(Long payableCent) { this.payableCent = payableCent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getCancelRequestedAt() { return cancelRequestedAt; }
    public void setCancelRequestedAt(LocalDateTime cancelRequestedAt) { this.cancelRequestedAt = cancelRequestedAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public String getSnapshotHash() { return snapshotHash; }
    public void setSnapshotHash(String snapshotHash) { this.snapshotHash = snapshotHash; }
    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}