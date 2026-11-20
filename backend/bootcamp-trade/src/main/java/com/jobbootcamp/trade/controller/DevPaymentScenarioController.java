package com.jobbootcamp.trade.controller;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.trade.application.PaymentScenarioService;
import com.jobbootcamp.trade.config.StudentAuthInterceptor;
import com.jobbootcamp.trade.domain.dto.PaymentScenarioRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api/v1/dev/payments", "/dev/payments"})
@ConditionalOnProperty(name = "app.payment.mock-scenario-enabled", havingValue = "true")
public class DevPaymentScenarioController {

    private final PaymentScenarioService scenarioService;

    public DevPaymentScenarioController(PaymentScenarioService scenarioService) {
        this.scenarioService = scenarioService;
    }

    @PostMapping("/{id}/scenario")
    public ApiResponse<Map<String, Object>> run(
            @PathVariable("id") Long requestId,
            @RequestAttribute(StudentAuthInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestAttribute(StudentAuthInterceptor.ROLE_ATTRIBUTE) String role,
            @RequestBody(required = false) PaymentScenarioRequest request) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "模拟支付场景仅面向管理员");
        }
        return ApiResponse.ok(scenarioService.run(requestId, request));
    }
}