package com.jobbootcamp.marketing.controller;

import com.jobbootcamp.contracts.internal.CouponLockPrepareRequest;
import com.jobbootcamp.contracts.internal.CouponLockResponse;
import com.jobbootcamp.contracts.internal.CouponLockStateRequest;
import com.jobbootcamp.marketing.application.CouponLockRecoveryService;
import com.jobbootcamp.marketing.application.CouponLockService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/int/v1/coupon-locks")
public class InternalCouponLockController {

    private final CouponLockService lockService;
    private final CouponLockRecoveryService recoveryService;

    public InternalCouponLockController(CouponLockService lockService,
                                        CouponLockRecoveryService recoveryService) {
        this.lockService = lockService;
        this.recoveryService = recoveryService;
    }

    @PostMapping("/prepare")
    public CouponLockResponse prepare(@RequestBody CouponLockPrepareRequest request) {
        return lockService.prepare(request);
    }

    @PostMapping("/use")
    public CouponLockResponse use(@RequestBody CouponLockStateRequest request) {
        return lockService.use(request);
    }

    @PostMapping("/release")
    public CouponLockResponse release(@RequestBody CouponLockStateRequest request) {
        return lockService.release(request);
    }

    @PostMapping("/recover")
    public Map<String, Integer> recover() {
        return Map.of("handled", recoveryService.recover(100));
    }
}