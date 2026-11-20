package com.jobbootcamp.trade.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.trade.domain.Payment;
import com.jobbootcamp.trade.domain.PaymentRequest;
import com.jobbootcamp.trade.domain.Refund;
import com.jobbootcamp.trade.domain.TradeOrder;
import com.jobbootcamp.trade.domain.dto.RefundResponse;
import com.jobbootcamp.trade.infrastructure.PaymentRequestMapper;
import com.jobbootcamp.trade.infrastructure.RefundMapper;
import com.jobbootcamp.trade.infrastructure.TradeOrderMapper;
import com.jobbootcamp.trade.payment.PaymentPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class RefundService {

    private final RefundMapper refundMapper;
    private final PaymentRequestMapper paymentRequestMapper;
    private final TradeOrderMapper orderMapper;
    private final PaymentPort paymentPort;

    public RefundService(RefundMapper refundMapper,
                         PaymentRequestMapper paymentRequestMapper,
                         TradeOrderMapper orderMapper,
                         PaymentPort paymentPort) {
        this.refundMapper = refundMapper;
        this.paymentRequestMapper = paymentRequestMapper;
        this.orderMapper = orderMapper;
        this.paymentPort = paymentPort;
    }

    public Refund createSystemReturn(Payment payment, TradeOrder order, LocalDateTime now) {
        Refund existing = refundMapper.selectBySystemReturnPaymentId(payment.getId());
        if (existing != null) {
            return existing;
        }
        PaymentRequest request = paymentRequestMapper.selectById(payment.getPaymentRequestId());
        if (request == null) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "支付请求不存在");
        }
        String refundNo = "SR" + UUID.randomUUID().toString().replace("-", "");
        Refund refund = new Refund();
        refund.setRefundNo(refundNo);
        refund.setOrderId(order.getId());
        refund.setPaymentId(payment.getId());
        refund.setKind("SYSTEM_RETURN");
        refund.setState("CHANNEL_PENDING");
        refund.setAmountCent(payment.getAmountCent());
        refund.setAcceptedAt(now);
        refund.setSystemReturnPaymentId(payment.getId());
        refund.setQualification("{\"reason\":\"LATE_OR_EXTRA_PAYMENT\",\"paidAt\":\"" + payment.getPaidAt() + "\"}");
        refund.setCreatedAt(now);
        refund.setUpdatedAt(now);
        try {
            refundMapper.insert(refund);
        } catch (DuplicateKeyException duplicate) {
            Refund raced = refundMapper.selectBySystemReturnPaymentId(payment.getId());
            if (raced != null) {
                return raced;
            }
            throw duplicate;
        }
        PaymentPort.RefundResult result = paymentPort.refund(request, refundNo, payment.getAmountCent());
        refund.setChannelRefundNo(result.channelRefundNo());
        refund.setState(switch (result.state()) {
            case "COMPLETED" -> "COMPLETED";
            case "UNKNOWN" -> "CHANNEL_UNKNOWN";
            default -> "FAILED";
        });
        refund.setUpdatedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        refundMapper.updateById(refund);
        return refund;
    }

    public RefundResponse get(Long userId, Long refundId) {
        Refund refund = refundMapper.selectById(refundId);
        if (refund == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "退款单不存在");
        }
        TradeOrder order = orderMapper.selectById(refund.getOrderId());
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "退款单不存在");
        }
        return toResponse(refund);
    }

    public RefundResponse toResponse(Refund refund) {
        return new RefundResponse(refund.getId(), refund.getRefundNo(), refund.getOrderId(),
                refund.getPaymentId(), refund.getKind(), refund.getState(), refund.getAmountCent(),
                refund.getAcceptedAt(), refund.getChannelRefundNo());
    }
}