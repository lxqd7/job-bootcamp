package com.jobbootcamp.trade.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("price_quote")
public class PriceQuote {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String quoteId;
    private Long userId;
    private String productType;
    private Long productId;
    private Long productVersion;
    private String productTitle;
    private Long unitPriceCent;
    private Long totalCent;
    private Long creditCent;
    private Long discountCent;
    private Long payableCent;
    private String inputHash;
    private String resultHash;
    private String status;
    private String inputJson;
    private String resultJson;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQuoteId() { return quoteId; }
    public void setQuoteId(String quoteId) { this.quoteId = quoteId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getProductVersion() { return productVersion; }
    public void setProductVersion(Long productVersion) { this.productVersion = productVersion; }
    public String getProductTitle() { return productTitle; }
    public void setProductTitle(String productTitle) { this.productTitle = productTitle; }
    public Long getUnitPriceCent() { return unitPriceCent; }
    public void setUnitPriceCent(Long unitPriceCent) { this.unitPriceCent = unitPriceCent; }
    public Long getTotalCent() { return totalCent; }
    public void setTotalCent(Long totalCent) { this.totalCent = totalCent; }
    public Long getCreditCent() { return creditCent; }
    public void setCreditCent(Long creditCent) { this.creditCent = creditCent; }
    public Long getDiscountCent() { return discountCent; }
    public void setDiscountCent(Long discountCent) { this.discountCent = discountCent; }
    public Long getPayableCent() { return payableCent; }
    public void setPayableCent(Long payableCent) { this.payableCent = payableCent; }
    public String getInputHash() { return inputHash; }
    public void setInputHash(String inputHash) { this.inputHash = inputHash; }
    public String getResultHash() { return resultHash; }
    public void setResultHash(String resultHash) { this.resultHash = resultHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getInputJson() { return inputJson; }
    public void setInputJson(String inputJson) { this.inputJson = inputJson; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}