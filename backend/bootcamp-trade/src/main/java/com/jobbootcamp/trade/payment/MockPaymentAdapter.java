package com.jobbootcamp.trade.payment;

import com.jobbootcamp.trade.domain.PaymentRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 仅用于本地/测试的显式模拟渠道；生产通过 PAYMENT_MODE 关闭，不会注册该适配器。
 * 退款结果可由管理员场景接口切换为 UNKNOWN/CLOSED/FAILED，用于验证 query-before-retry。
 */
@Component
@ConditionalOnProperty(name = "app.payment.mode", havingValue = "mock", matchIfMissing = true)
public class MockPaymentAdapter implements PaymentPort {

    private final String signingKey;
    private final Map<Long, String> refundScenarios = new ConcurrentHashMap<>();

    public MockPaymentAdapter(@Value("${app.payment.mock-signing-key:dev-mock-payment-key}") String signingKey) {
        this.signingKey = signingKey;
    }

    public void setRefundScenario(Long orderId, String outcome) {
        if (outcome == null || outcome.isBlank()) {
            refundScenarios.remove(orderId);
        } else {
            refundScenarios.put(orderId, outcome.trim().toUpperCase());
        }
    }

    @Override
    public String channel() {
        return "MOCK";
    }

    @Override
    public CreatedPayment create(PaymentRequest request) {
        return new CreatedPayment("mock://pay/" + request.getMerchantRequestNo(), "PENDING");
    }

    @Override
    public CloseResult close(PaymentRequest request) {
        return new CloseResult("CLOSED", "MOCK_CLOSED");
    }

    @Override
    public RefundResult refund(PaymentRequest request, String refundNo, long amountCent) {
        String outcome = refundScenarios.getOrDefault(request.getOrderId(), "COMPLETED");
        return switch (outcome) {
            case "UNKNOWN" -> new RefundResult("UNKNOWN", null, "MOCK_UNKNOWN");
            case "CLOSED" -> new RefundResult("CLOSED", null, "MOCK_CLOSED");
            case "FAILED" -> new RefundResult("FAILED", null, "MOCK_FAILED");
            default -> new RefundResult("COMPLETED", "MOCKR-" + refundNo, "MOCK_REFUNDED");
        };
    }

    @Override
    public RefundQueryResult queryRefund(PaymentRequest request, String refundNo) {
        String outcome = refundScenarios.getOrDefault(request.getOrderId(), "COMPLETED");
        return switch (outcome) {
            case "CLOSED" -> new RefundQueryResult("CLOSED", null, "MOCK_CLOSED");
            case "UNKNOWN", "FAILED" -> new RefundQueryResult("UNKNOWN", null, "MOCK_UNKNOWN");
            default -> new RefundQueryResult("COMPLETED", "MOCKR-" + refundNo, "MOCK_REFUNDED");
        };
    }

    @Override
    public boolean verify(String rawBody, String signature) {
        if (rawBody == null || signature == null) {
            return false;
        }
        byte[] expected = sign(rawBody).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    public String sign(String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("mock payment signing failed", e);
        }
    }
}