package com.jobbootcamp.learning.controller;

import com.jobbootcamp.contracts.internal.PurchaseActionRequest;
import com.jobbootcamp.contracts.internal.PurchasePrepareRequest;
import com.jobbootcamp.contracts.internal.PurchasePrepareResponse;
import com.jobbootcamp.contracts.internal.SourceSelectionRequest;
import com.jobbootcamp.contracts.internal.SourceSelectionResponse;
import com.jobbootcamp.learning.application.EntitlementService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 交易内部调用：只返回来源选择结果或操作购买预留，不暴露给学生客户端。
 */
@RestController
@RequestMapping("/int/v1")
public class InternalPurchaseController {

    private final EntitlementService entitlementService;

    public InternalPurchaseController(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @PostMapping("/entitlements/sources")
    public SourceSelectionResponse sources(@RequestBody SourceSelectionRequest request) {
        return entitlementService.selectSources(request.userId(), request.courseIds());
    }

    @PostMapping("/purchases/prepare")
    public PurchasePrepareResponse prepare(@RequestBody PurchasePrepareRequest request) {
        return entitlementService.preparePurchase(request.orderId(), request.userId(), request.courseIds());
    }

    @PostMapping("/purchases/commit")
    public void commit(@RequestBody PurchaseActionRequest request) {
        entitlementService.commitPurchase(request.orderId());
    }

    @PostMapping("/purchases/release")
    public void release(@RequestBody PurchaseActionRequest request) {
        entitlementService.releasePurchase(request.orderId());
    }
}