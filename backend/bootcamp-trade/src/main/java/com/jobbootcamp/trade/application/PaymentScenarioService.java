package com.jobbootcamp.trade.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.trade.domain.PaymentRequest;
import com.jobbootcamp.trade.domain.TradeOrder;
import com.jobbootcamp.trade.domain.dto.PaymentScenarioRequest;
import com.jobbootcamp.trade.infrastructure.PaymentRequestMapper;
import com.jobbootcamp.trade.infrastructure.TradeOrderMapper;
import com.jobbootcamp.trade.payment.MockPaymentAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.payment.mock-scenario-enabled", havingValue = "true")
public class PaymentScenarioService {

    private final PaymentRequestMapper requestMapper;
    private final TradeOrderMapper orderMapper;
    private final MockPaymentAdapter mockPaymentAdapter;
    private final PaymentCallbackService callbackService;
    private final PaymentService paymentService;

    public PaymentScenarioService(PaymentRequestMapper requestMapper,
                                  TradeOrderMapper orderMapper,
                                  MockPaymentAdapter mockPaymentAdapter,
                                  PaymentCallbackService callbackService,
                                  PaymentService paymentService) {
        this.requestMapper = requestMapper;
        this.orderMapper = orderMapper;
        this.mockPaymentAdapter = mockPaymentAdapter;
        this.callbackService = callbackService;
        this.paymentService = paymentService;
    }

    public Map<String, Object> run(Long requestId, PaymentScenarioRequest body) {
        PaymentRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "支付请求不存在");
        }
        String scenario = body == null || body.scenario() == null
                ? "SUCCESS"
                : body.scenario().trim().toUpperCase();
        return switch (scenario) {
            case "SUCCESS", "LATE_NOTIFICATION" -> callback(request, paidAt(body), "1");
            case "DUPLICATE" -> {
                String paidAt = paidAt(body);
                Map<String, Object> first = callback(request, paidAt, "1");
                Map<String, Object> second = callback(request, paidAt, "1");
                second.put("firstResult", first);
                yield second;
            }
            case "EXTRA_PAYMENT" -> {
                String paidAt = paidAt(body);
                callback(request, paidAt, "1");
                yield callback(request, paidAt, "2");
            }
            case "UNKNOWN", "CLOSE_UNKNOWN" -> {
                requestMapper.updateState(requestId, "UNKNOWN", java.time.LocalDateTime.now(ZoneOffset.UTC));
                TradeOrder order = orderMapper.selectById(request.getOrderId());
                if (order != null && "WAIT_PAY".equals(order.getState())) {
                    orderMapper.markClosing(order.getId(), java.time.LocalDateTime.now(ZoneOffset.UTC),
                            java.time.LocalDateTime.now(ZoneOffset.UTC));
                }
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("scenario", scenario);
                result.put("orderId", request.getOrderId());
                result.put("state", "UNKNOWN");
                yield result;
            }
            case "CLOSE_SUCCESS" -> {
                TradeOrder order = paymentService.closeBySystem(request.getOrderId());
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("scenario", scenario);
                result.put("orderId", order.getId());
                result.put("state", order.getState());
                yield result;
            }
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "未知模拟场景");
        };
    }

    private String paidAt(PaymentScenarioRequest body) {
        if (body == null || body.paidAt() == null || body.paidAt().isBlank()) {
            return Instant.now().toString();
        }
        try {
            return Instant.parse(body.paidAt()).toString();
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "paidAt 必须是带时区的 ISO 时间");
        }
    }

    private Map<String, Object> callback(PaymentRequest request, String paidAtText, String suffix) {
        Instant paidAt = Instant.parse(paidAtText);
        String payload = "{\"merchantRequestNo\":\"" + request.getMerchantRequestNo()
                + "\",\"channelTradeNo\":\"MOCK-" + request.getId() + "-" + suffix
                + "\",\"amountCent\":" + request.getAmountCent()
                + ",\"currency\":\"CNY\",\"paidAt\":\"" + paidAt + "\"}";
        PaymentCallbackService.CallbackResult result = callbackService.handle(
                "MOCK", payload, mockPaymentAdapter.sign(payload));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("paymentId", result.paymentId());
        map.put("orderId", result.orderId());
        map.put("orderState", result.orderState());
        map.put("valid", result.valid());
        map.put("refundId", result.refundId());
        map.put("duplicate", result.duplicate());
        return map;
    }
}