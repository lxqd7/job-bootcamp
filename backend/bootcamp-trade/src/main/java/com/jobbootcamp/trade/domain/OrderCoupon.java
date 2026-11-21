package com.jobbootcamp.trade.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("order_coupon")
public class OrderCoupon {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long userCouponId;
    private Integer applyOrder;
    private Long baseCent;
    private Long discountCent;
    private String allocationJson;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getUserCouponId() { return userCouponId; }
    public void setUserCouponId(Long userCouponId) { this.userCouponId = userCouponId; }
    public Integer getApplyOrder() { return applyOrder; }
    public void setApplyOrder(Integer applyOrder) { this.applyOrder = applyOrder; }
    public Long getBaseCent() { return baseCent; }
    public void setBaseCent(Long baseCent) { this.baseCent = baseCent; }
    public Long getDiscountCent() { return discountCent; }
    public void setDiscountCent(Long discountCent) { this.discountCent = discountCent; }
    public String getAllocationJson() { return allocationJson; }
    public void setAllocationJson(String allocationJson) { this.allocationJson = allocationJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}