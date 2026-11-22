package com.jobbootcamp.trade.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.RefundQualificationRequest;
import com.jobbootcamp.contracts.internal.RefundStartResponse;
import com.jobbootcamp.contracts.internal.SourceLifecycleRequest;
import com.jobbootcamp.contracts.internal.SourceLifecycleResponse;
import com.jobbootcamp.trade.domain.Payment;
import com.jobbootcamp.trade.domain.PaymentRequest;
import com.jobbootcamp.trade.domain.Refund;
import com.jobbootcamp.trade.domain.TradeOrder;
import com.jobbootcamp.trade.infrastructure.PaymentMapper;
import com.jobbootcamp.trade.infrastructure.PaymentRequestMapper;
import com.jobbootcamp.trade.infrastructure.RefundMapper;
import com.jobbootcamp.trade.infrastructure.TradeOrderMapper;
import com.jobbootcamp.trade.payment.PaymentPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 用户退款状态机。渠道结果未知时只查询不重复提交；渠道成功后先撤权，撤权失败保留
 * REVOKE_PENDING 继续重试，绝不因为“已提交退款”再次发起第二笔退款。
 */
@Service
public class UserRefundService {

    private static final Logger log = LoggerFactory.getLogger(UserRefundService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RefundMapper refundMapper;
    private final PaymentMapper paymentMapper;
    private final PaymentRequestMapper requestMapper;
    private final PaymentPort paymentPort;
    private final LearningRefundClient learningClient;
    private final TradeOrderMapper orderMapper;

    public UserRefundService(RefundMapper refundMapper,
                             PaymentMapper paymentMapper,
                             PaymentRequestMapper requestMapper,
                             PaymentPort paymentPort,
                             LearningRefundClient learningClient,
                             TradeOrderMapper orderMapper) {
        this.refundMapper = refundMapper;
        this.paymentMapper = paymentMapper;
        this.requestMapper = requestMapper;
        this.paymentPort = paymentPort;
        this.learningClient = learningClient;
        this.orderMapper = orderMapper;
    }

    public RefundStartResponse start(RefundQualificationRequest request) {
        TradeOrder order = orderMapper.selectById(request.orderId());
        if (order == null || !order.getUserId().equals(request.userId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "退款订单不存在");
        }
        Refund refund = start(request, order);
        return new RefundStartResponse(refund.getId(), refund.getState());
    }

    public Refund start(RefundQualificationRequest request, TradeOrder order) {
        Refund existing = refundMapper.selectByIntentId(request.intentId());
        if (existing != null) {
            return existing;
        }
        Payment payment = paymentMapper.selectSucceededByOrder(order.getId());
        if (payment == null) {
            throw new BusinessException(ErrorCode.ORDER_SYNCING, "支付事实尚未就绪");
        }
        LocalDateTime now = now();
        Refund refund = new Refund();
        refund.setRefundNo("UR" + UUID.randomUUID().toString().replace("-", ""));
        refund.setOrderId(order.getId());
        refund.setUserId(request.userId());
        refund.setPaymentId(payment.getId());
        refund.setIntentId(request.intentId());
        refund.setKind("USER");
        refund.setState("FREEZE_CONFIRMED");
        refund.setAmountCent(order.getPayableCent());
        refund.setAcceptedAt(LocalDateTime.ofInstant(Instant.parse(request.acceptedAt()), ZoneOffset.UTC));
        refund.setProgressN(request.completedCount());
        refund.setProgressD(request.totalCount());
        refund.setSourceVersion(request.sourceVersion());
        refund.setTradeStateVersion(1L);
        refund.setActiveOrderId(order.getId());
        refund.setQualification(qualification(request, payment));
        refund.setCreatedAt(now);
        refund.setUpdatedAt(now);
        try {
            refundMapper.insert(refund);
        } catch (DuplicateKeyException duplicate) {
            Refund raced = refundMapper.selectByIntentId(request.intentId());
            if (raced != null) {
                return raced;
            }
            throw duplicate;
        }
        return process(refund.getId());
    }

    public Refund process(Long refundId) {
        Refund refund = refundMapper.selectById(refundId);
        if (refund == null || !"USER".equals(refund.getKind()) || terminal(refund.getState())) {
            return refund;
        }
        PaymentRequest request = paymentRequest(refund);
        try {
            return switch (refund.getState()) {
                case "REVOKE_PENDING" -> revoke(refund);
                case "CLOSE_RESTORE" -> restore(refund);
                case "CHANNEL_UNKNOWN" -> query(refund, request);
                default -> channel(refund, request);
            };
        } catch (RuntimeException e) {
            log.warn("user refund step deferred refundId={} state={} error={}",
                    refund.getId(), refund.getState(), e.getMessage());
            return refundMapper.selectById(refundId);
        }
    }

    public int retryPending(int limit) {
        LocalDateTime cutoff = now().minusSeconds(5);
        List<Refund> pending = refundMapper.selectList(Wrappers.<Refund>lambdaQuery()
                .eq(Refund::getKind, "USER")
                .in(Refund::getState, List.of("CHANNEL_PENDING", "CHANNEL_UNKNOWN", "REVOKE_PENDING", "CLOSE_RESTORE"))
                .le(Refund::getUpdatedAt, cutoff)
                .orderByAsc(Refund::getUpdatedAt)
                .last("LIMIT " + Math.min(100, Math.max(1, limit))));
        int handled = 0;
        for (Refund refund : pending) {
            Refund latest = process(refund.getId());
            if (latest != null && !refund.getState().equals(latest.getState())) {
                handled++;
            }
        }
        return handled;
    }

    public Refund get(Long refundId) {
        return refundMapper.selectById(refundId);
    }

    private Refund channel(Refund refund, PaymentRequest request) {
        transition(refund, "CHANNEL_PENDING");
        PaymentPort.RefundResult result = paymentPort.refund(request, refund.getRefundNo(), refund.getAmountCent());
        refund.setChannelRefundNo(result.channelRefundNo());
        return switch (result.state()) {
            case "COMPLETED" -> completedChannel(refund);
            case "UNKNOWN" -> transition(refund, "CHANNEL_UNKNOWN");
            case "CLOSED" -> closeRestore(refund);
            default -> transition(refund, "CHANNEL_PENDING");
        };
    }

    private Refund query(Refund refund, PaymentRequest request) {
        PaymentPort.RefundQueryResult result = paymentPort.queryRefund(request, refund.getRefundNo());
        refund.setChannelRefundNo(result.channelRefundNo());
        return switch (result.state()) {
            case "COMPLETED" -> completedChannel(refund);
            case "CLOSED" -> closeRestore(refund);
            default -> transition(refund, "CHANNEL_UNKNOWN");
        };
    }

    private Refund completedChannel(Refund refund) {
        transition(refund, "REVOKE_PENDING");
        return revoke(refund);
    }

    private Refund revoke(Refund refund) {
        SourceLifecycleRequest request = new SourceLifecycleRequest(refund.getId(), refund.getIntentId(),
                refund.getOrderId(), userId(refund), refund.getSourceVersion(), "CHANNEL_COMPLETED");
        SourceLifecycleResponse response = learningClient.revoke(request);
        if (response == null || !"REVOKED".equals(response.state())) {
            return transition(refund, "REVOKE_PENDING");
        }
        return transition(refund, "COMPLETED");
    }

    private Refund closeRestore(Refund refund) {
        transition(refund, "CLOSE_RESTORE");
        return restore(refund);
    }

    private Refund restore(Refund refund) {
        SourceLifecycleRequest request = new SourceLifecycleRequest(refund.getId(), refund.getIntentId(),
                refund.getOrderId(), userId(refund), refund.getSourceVersion(), "CHANNEL_CLOSED");
        SourceLifecycleResponse response = learningClient.restore(request);
        if (response == null || !"ACTIVE".equals(response.state())) {
            return transition(refund, "CLOSE_RESTORE");
        }
        return transition(refund, "CLOSED");
    }

    private Refund transition(Refund refund, String state) {
        if (state.equals(refund.getState())) {
            return refund;
        }
        refund.setState(state);
        refund.setTradeStateVersion((refund.getTradeStateVersion() == null ? 0 : refund.getTradeStateVersion()) + 1);
        refund.setActiveOrderId(terminal(state) ? null : refund.getOrderId());
        refund.setUpdatedAt(now());
        refundMapper.updateById(refund);
        return refund;
    }

    private boolean terminal(String state) {
        return "COMPLETED".equals(state) || "REJECTED".equals(state) || "CLOSED".equals(state);
    }

    private PaymentRequest paymentRequest(Refund refund) {
        Payment payment = paymentMapper.selectById(refund.getPaymentId());
        if (payment == null) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "退款支付事实不存在");
        }
        PaymentRequest request = requestMapper.selectById(payment.getPaymentRequestId());
        if (request == null) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "退款支付请求不存在");
        }
        return request;
    }

    private Long userId(Refund refund) {
        return refund.getUserId();
    }

    private String qualification(RefundQualificationRequest request, Payment payment) {
        try {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("snapshotHash", request.snapshotHash());
            value.put("acceptedAt", request.acceptedAt());
            value.put("paidAt", payment.getPaidAt().toInstant(ZoneOffset.UTC).toString());
            value.put("completedCount", request.completedCount());
            value.put("totalCount", request.totalCount());
            value.put("userId", request.userId());
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "退款资格快照序列化失败");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}