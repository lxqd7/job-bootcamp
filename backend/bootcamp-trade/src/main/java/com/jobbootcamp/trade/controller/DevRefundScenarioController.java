package com.jobbootcamp.trade.controller;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.trade.config.StudentAuthInterceptor;
import com.jobbootcamp.trade.payment.MockPaymentAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dev/refunds")
@ConditionalOnProperty(name = "app.payment.mock-scenario-enabled", havingValue = "true")
public class DevRefundScenarioController {

    private final MockPaymentAdapter mockPaymentAdapter;

    public DevRefundScenarioController(MockPaymentAdapter mockPaymentAdapter) {
        this.mockPaymentAdapter = mockPaymentAdapter;
    }

    @PostMapping("/{orderId}/scenario")
    public ApiResponse<Map<String, String>> scenario(
            @PathVariable("orderId") Long orderId,
            @RequestAttribute(StudentAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestBody Map<String, String> body) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "退款模拟场景仅面向管理员");
        }
        String outcome = body == null ? null : body.get("outcome");
        mockPaymentAdapter.setRefundScenario(orderId, outcome);
        return ApiResponse.ok(Map.of("orderId", String.valueOf(orderId),
                "outcome", outcome == null ? "DEFAULT" : outcome.toUpperCase()));
    }
}