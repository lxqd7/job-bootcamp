package com.jobbootcamp.trade.controller;

import com.jobbootcamp.trade.application.PaymentCallbackService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class PaymentCallbackController {

    private final PaymentCallbackService callbackService;

    public PaymentCallbackController(PaymentCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @PostMapping("/callbacks/payments/{channel}")
    public Map<String, Object> callback(@PathVariable("channel") String channel,
                                        @RequestBody String rawBody,
                                        @RequestHeader(name = "X-Payment-Signature", required = false) String signature) {
        PaymentCallbackService.CallbackResult result = callbackService.handle(channel, rawBody, signature);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", "SUCCESS");
        response.put("paymentId", result.paymentId());
        response.put("orderId", result.orderId());
        response.put("orderState", result.orderState());
        response.put("duplicate", result.duplicate());
        return response;
    }
}