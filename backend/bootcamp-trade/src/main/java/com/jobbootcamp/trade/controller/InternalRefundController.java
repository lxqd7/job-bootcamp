package com.jobbootcamp.trade.controller;

import com.jobbootcamp.contracts.internal.RefundBasisResponse;
import com.jobbootcamp.contracts.internal.RefundQualificationRequest;
import com.jobbootcamp.contracts.internal.RefundQualificationResponse;
import com.jobbootcamp.trade.application.RefundEligibilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/int/v1")
public class InternalRefundController {

    private final RefundEligibilityService eligibilityService;

    public InternalRefundController(RefundEligibilityService eligibilityService) {
        this.eligibilityService = eligibilityService;
    }

    @GetMapping("/orders/{orderId}/refund-basis")
    public RefundBasisResponse basis(@PathVariable("orderId") Long orderId,
                                     @RequestParam("userId") Long userId) {
        return eligibilityService.basis(orderId, userId);
    }

    @PostMapping("/refunds/qualify")
    public RefundQualificationResponse qualify(@RequestBody RefundQualificationRequest request) {
        return eligibilityService.qualify(request);
    }
}