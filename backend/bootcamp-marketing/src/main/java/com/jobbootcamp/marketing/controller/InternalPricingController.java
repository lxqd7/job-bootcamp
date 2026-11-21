package com.jobbootcamp.marketing.controller;

import com.jobbootcamp.contracts.internal.PricingCalculateRequest;
import com.jobbootcamp.contracts.internal.PricingCalculateResponse;
import com.jobbootcamp.marketing.application.PricingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/int/v1/pricing")
public class InternalPricingController {

    private final PricingService pricingService;

    public InternalPricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @PostMapping("/calculate")
    public PricingCalculateResponse calculate(@RequestBody PricingCalculateRequest request) {
        return pricingService.calculate(request);
    }

    @GetMapping("/status/{quoteId}")
    public PricingCalculateResponse status(@PathVariable("quoteId") String quoteId,
                                           @RequestParam("userId") Long userId) {
        return pricingService.status(quoteId, userId);
    }
}