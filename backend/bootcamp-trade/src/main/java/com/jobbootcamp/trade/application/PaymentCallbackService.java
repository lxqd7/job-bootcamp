package com.jobbootcamp.trade.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.trade.domain.ChannelReceipt;
import com.jobbootcamp.trade.domain.OrderCourse;
import com.jobbootcamp.trade.domain.OutboxEvent;
import com.jobbootcamp.trade.domain.Payment;
import com.jobbootcamp.trade.domain.PaymentRequest;
import com.jobbootcamp.trade.domain.TradeOrder;
import com.jobbootcamp.trade.infrastructure.ChannelReceiptMapper;
import com.jobbootcamp.trade.infrastructure.OrderCourseMapper;
import com.jobbootcamp.trade.infrastructure.OrderLessonMapper;
import com.jobbootcamp.trade.infrastructure.OutboxEventMapper;
import com.jobbootcamp.trade.infrastructure.PaymentMapper;
import com.jobbootcamp.trade.infrastructure.PaymentRequestMapper;
import com.jobbootcamp.trade.infrastructure.PendingCourseMapper;
import com.jobbootcamp.trade.infrastructure.TradeGuardMapper;
import com.jobbootcamp.trade.infrastructure.TradeOrderMapper;
import com.jobbootcamp.trade.payment.PaymentPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 支付回调处理器。先验签，再用回执编号/渠道流水去重，最后按渠道 paidAt 判断有效付款窗口；
 * 订单 PAID 与学习发权分离，有效付款只写支付事实和 outbox，系统退回只处理真实额外收款。
 */
@Service
public class PaymentCallbackService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PaymentPort paymentPort;
    private final PaymentRequestMapper requestMapper;
    private final PaymentMapper paymentMapper;
    private final ChannelReceiptMapper receiptMapper;
    private final TradeOrderMapper orderMapper;
    private final OrderCourseMapper orderCourseMapper;
    private final OrderLessonMapper orderLessonMapper;
    private final PendingCourseMapper pendingCourseMapper;
    private final TradeGuardMapper guardMapper;
    private final OutboxEventMapper outboxMapper;
    private final RefundService refundService;
    private final TransactionTemplate transactionTemplate;

    public PaymentCallbackService(PaymentPort paymentPort,
                                  PaymentRequestMapper requestMapper,
                                  PaymentMapper paymentMapper,
                                  ChannelReceiptMapper receiptMapper,
                                  TradeOrderMapper orderMapper,
                                  OrderCourseMapper orderCourseMapper,
                                  OrderLessonMapper orderLessonMapper,
                                  PendingCourseMapper pendingCourseMapper,
                                  TradeGuardMapper guardMapper,
                                  OutboxEventMapper outboxMapper,
                                  RefundService refundService,
                                  PlatformTransactionManager transactionManager) {
        this.paymentPort = paymentPort;
        this.requestMapper = requestMapper;
        this.paymentMapper = paymentMapper;
        this.receiptMapper = receiptMapper;
        this.orderMapper = orderMapper;
        this.orderCourseMapper = orderCourseMapper;
        this.orderLessonMapper = orderLessonMapper;
        this.pendingCourseMapper = pendingCourseMapper;
        this.guardMapper = guardMapper;
        this.outboxMapper = outboxMapper;
        this.refundService = refundService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public CallbackResult handle(String channel, String rawBody, String signature) {
        String normalized = channel == null ? "" : channel.trim().toUpperCase();
        if (!paymentPort.channel().equals(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "未知支付渠道");
        }
        if (!paymentPort.verify(rawBody, signature)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "支付回调验签失败");
        }
        PaymentCallbackBody body;
        try {
            body = JSON.readValue(rawBody, PaymentCallbackBody.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "支付回调报文格式错误");
        }
        if (body.merchantRequestNo() == null || body.channelTradeNo() == null
                || body.amountCent() == null || body.paidAt() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "支付回调缺少必要字段");
        }
        if (!"CNY".equalsIgnoreCase(body.currency())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "支付币种必须为 CNY");
        }
        String bodyHash = sha256(rawBody);
        try {
            CallbackResult result = transactionTemplate.execute(status ->
                    process(normalized, body, bodyHash));
            if (result == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "支付回调未产生结果");
            }
            return result;
        } catch (DuplicateKeyException duplicate) {
            ChannelReceipt existing = receiptMapper.selectOne(Wrappers.<ChannelReceipt>lambdaQuery()
                    .eq(ChannelReceipt::getChannel, normalized)
                    .eq(ChannelReceipt::getReceiptId, body.channelTradeNo()));
            if (existing != null && existing.getProcessedAt() != null) {
                return new CallbackResult(existing.getPaymentId(), null, null, false, null, true);
            }
            throw new BusinessException(ErrorCode.ORDER_SYNCING, "支付回执处理中，请稍后重试");
        }
    }

    private CallbackResult process(String channel, PaymentCallbackBody body, String bodyHash) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        PaymentRequest request = requestMapper.selectByMerchant(channel, body.merchantRequestNo());
        if (request == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "支付请求不存在");
        }
        if (!request.getAmountCent().equals(body.amountCent())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "支付金额与订单不一致");
        }
        ChannelReceipt existing = receiptMapper.selectOne(Wrappers.<ChannelReceipt>lambdaQuery()
                .eq(ChannelReceipt::getChannel, channel)
                .eq(ChannelReceipt::getReceiptId, body.channelTradeNo()));
        if (existing != null) {
            if (!existing.getBodyHash().equals(bodyHash)) {
                throw new BusinessException(ErrorCode.CONFLICT, "同一回执编号的报文内容不一致");
            }
            return new CallbackResult(existing.getPaymentId(), request.getOrderId(), null, false, null, true);
        }

        Payment existingPayment = paymentMapper.selectByChannelTradeNo(channel, body.channelTradeNo());
        if (existingPayment != null) {
            return new CallbackResult(existingPayment.getId(), request.getOrderId(), null, false, null, true);
        }

        LocalDateTime paidAt;
        try {
            paidAt = LocalDateTime.ofInstant(Instant.parse(body.paidAt()), ZoneOffset.UTC);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "paidAt 必须是带时区的渠道时间");
        }

        ChannelReceipt receipt = new ChannelReceipt();
        receipt.setChannel(channel);
        receipt.setReceiptId(body.channelTradeNo());
        receipt.setBodyHash(bodyHash);
        receipt.setVerified(true);
        receipt.setEventType("PAYMENT_SUCCEEDED");
        receipt.setCreatedAt(now);
        receiptMapper.insert(receipt);

        Payment payment = new Payment();
        payment.setOrderId(request.getOrderId());
        payment.setPaymentRequestId(request.getId());
        payment.setChannel(channel);
        payment.setChannelTradeNo(body.channelTradeNo());
        payment.setAmountCent(body.amountCent());
        payment.setCurrency("CNY");
        payment.setStatus("SUCCEEDED");
        payment.setPaidAt(paidAt);
        payment.setTimePrecisionMs(1);
        payment.setEvidenceHash(bodyHash);
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        paymentMapper.insert(payment);

        TradeOrder order = orderMapper.selectForUpdate(request.getOrderId());
        if (order != null && "ROUTE".equals(order.getProductType())) {
            throw new BusinessException(ErrorCode.CONFLICT, "路线支付处理将在 S07-b 开放");
        }
        boolean valid = isValidPayment(order, paidAt);
        Long refundId = null;
        if (valid) {
            guardMapper.ensure(order.getUserId(), now);
            guardMapper.increment(order.getUserId(), now);
            Long sequence = guardMapper.lock(order.getUserId());
            orderMapper.markPaid(order.getId(), paidAt, sequence, now);
            pendingCourseMapper.deleteByOrderId(order.getId());
            requestMapper.updateState(request.getId(), "CLOSED", now);
            // 注意这里只写“已付款”事实和 outbox；学习来源由异步消费者建立，fulfillment 留在 PENDING。
            insertPaymentConfirmedOutbox(order, payment, paidAt, now);
        } else {
            if (order != null && ("WAIT_PAY".equals(order.getState()) || "CLOSING".equals(order.getState()))) {
                orderMapper.markClosed(order.getId(), "CLOSED", now);
                pendingCourseMapper.deleteByOrderId(order.getId());
            }
            requestMapper.updateState(request.getId(), "CLOSED", now);
            if (order != null) {
                refundId = refundService.createSystemReturn(payment, order, now).getId();
            }
        }

        receipt.setPaymentId(payment.getId());
        receipt.setProcessedAt(now);
        receiptMapper.updateById(receipt);
        TradeOrder updated = orderMapper.selectById(request.getOrderId());
        return new CallbackResult(payment.getId(), request.getOrderId(),
                updated == null ? null : updated.getState(), valid, refundId, false);
    }

    private boolean isValidPayment(TradeOrder order, LocalDateTime paidAt) {
        if (order == null) {
            return false;
        }
        // 已支付订单的再次收款不是主收款，不能覆盖原订单；CREATING 尚未建立支付资格。
        if ("PAID".equals(order.getState()) || "CREATING".equals(order.getState())
                || "CREATE_FAILED".equals(order.getState()) || "RECONCILING".equals(order.getState())) {
            return false;
        }
        // 时间边界使用渠道事实 paidAt，不能用回调到达时间；恰好等于截止时间视为无效。
        if (!paidAt.isBefore(order.getExpiresAt())) {
            return false;
        }
        return order.getCancelRequestedAt() == null || paidAt.isBefore(order.getCancelRequestedAt());
    }

    private void insertPaymentConfirmedOutbox(TradeOrder order, Payment payment,
                                              LocalDateTime paidAt, LocalDateTime now) {
        List<OrderCourse> courses = orderCourseMapper.selectList(Wrappers.<OrderCourse>lambdaQuery()
                .eq(OrderCourse::getOrderId, order.getId())
                .orderByAsc(OrderCourse::getCourseId));
        List<Map<String, Object>> coursePayloads = new ArrayList<>();
        List<Long> refundLessonIds = new ArrayList<>();
        for (OrderCourse course : courses) {
            List<Long> lessonIds = orderLessonMapper.selectLessonIds(order.getId(), course.getCourseId());
            refundLessonIds.addAll(lessonIds);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("courseId", course.getCourseId());
            row.put("courseVersion", course.getCourseVersion());
            row.put("lessonIds", lessonIds);
            coursePayloads.add(row);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("userId", order.getUserId());
        payload.put("paidAt", paidAt.toInstant(ZoneOffset.UTC).toString());
        payload.put("paymentId", payment.getId());
        payload.put("orderVersion", 1);
        payload.put("snapshotHash", order.getSnapshotHash());
        payload.put("courses", coursePayloads);
        payload.put("couponIds", List.of());
        payload.put("refundLessonIds", refundLessonIds);

        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setType("trade.payment-confirmed.v1");
        event.setAggregateId(String.valueOf(order.getId()));
        event.setAggregateVersion(1L);
        event.setOccurredAt(paidAt);
        event.setPayload(TradeJson.write(payload));
        event.setStatus("PENDING");
        event.setAttempt(0);
        event.setNextRetryAt(now);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        outboxMapper.insert(event);
    }

    private String sha256(String text) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentCallbackBody(String merchantRequestNo, String channelTradeNo,
                                      Long amountCent, String currency, String paidAt) {
    }

    public record CallbackResult(Long paymentId, Long orderId, String orderState,
                                 boolean valid, Long refundId, boolean duplicate) {
    }
}