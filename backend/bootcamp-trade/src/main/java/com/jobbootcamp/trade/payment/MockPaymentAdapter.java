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

/**
 * 仅用于本地/测试的显式模拟渠道；生产通过 PAYMENT_MODE 关闭，不会注册该适配器。
 */
@Component
@ConditionalOnProperty(name = "app.payment.mode", havingValue = "mock", matchIfMissing = true)
public class MockPaymentAdapter implements PaymentPort {

    private final String signingKey;

    public MockPaymentAdapter(@Value("${app.payment.mock-signing-key:dev-mock-payment-key}") String signingKey) {
        this.signingKey = signingKey;
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
        return new RefundResult("COMPLETED", "MOCKR-" + refundNo, "MOCK_REFUNDED");
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