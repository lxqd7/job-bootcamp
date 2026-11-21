package com.jobbootcamp.trade.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.trade.domain.PaymentRequest;
import com.jobbootcamp.trade.domain.TradeOrder;
import com.jobbootcamp.trade.domain.dto.PaymentInitiateResponse;
import com.jobbootcamp.trade.domain.dto.PaymentInitiateRequest;
import com.jobbootcamp.trade.infrastructure.PaymentRequestMapper;
import com.jobbootcamp.trade.infrastructure.PendingCourseMapper;
import com.jobbootcamp.trade.infrastructure.TradeOrderMapper;
import com.jobbootcamp.trade.payment.PaymentPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * 支付单与关闭流程。支付单按 order/channel 复用；关闭意图先落库，
 * 只有渠道确认关闭后才释放 pending_course，未知状态保留 CLOSING 交给重试任务。
 */
@Service
public class PaymentService {

    private final TradeOrderMapper orderMapper;
    private final PaymentRequestMapper requestMapper;
    private final PendingCourseMapper pendingCourseMapper;
    private final PaymentPort paymentPort;
    private final TransactionTemplate transactionTemplate;

    public PaymentService(TradeOrderMapper orderMapper,
                          PaymentRequestMapper requestMapper,
                          PendingCourseMapper pendingCourseMapper,
                          PaymentPort paymentPort,
                          PlatformTransactionManager transactionManager) {
        this.orderMapper = orderMapper;
        this.requestMapper = requestMapper;
        this.pendingCourseMapper = pendingCourseMapper;
        this.paymentPort = paymentPort;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PaymentInitiateResponse initiate(Long userId, Long orderId, PaymentInitiateRequest request) {
        String channel = request == null || request.channel() == null
                ? paymentPort.channel()
                : request.channel().trim().toUpperCase();
        if (!paymentPort.channel().equals(channel)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前只支持 MOCK 测试支付渠道");
        }
        PaymentInitiateResponse response = transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            TradeOrder order = orderMapper.selectForUpdate(orderId);
            if (order == null || !order.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
            }
            if (!"WAIT_PAY".equals(order.getState())) {
                throw new BusinessException(ErrorCode.CONFLICT, "订单当前不可支付");
            }
            if (!order.getExpiresAt().isAfter(now)) {
                throw new BusinessException(ErrorCode.CONFLICT, "订单已过期，请重新下单");
            }
            PaymentRequest existing = requestMapper.selectOne(Wrappers.<PaymentRequest>lambdaQuery()
                    .eq(PaymentRequest::getOrderId, orderId)
                    .eq(PaymentRequest::getChannel, channel));
            if (existing != null) {
                if ("CLOSED".equals(existing.getState())) {
                    throw new BusinessException(ErrorCode.CONFLICT, "支付单已关闭");
                }
                return toResponse(existing);
            }
            PaymentRequest created = new PaymentRequest();
            created.setOrderId(orderId);
            created.setChannel(channel);
            created.setMerchantRequestNo(channel + UUID.randomUUID().toString().replace("-", ""));
            created.setAmountCent(order.getPayableCent());
            created.setState("PENDING");
            created.setExpiresAt(order.getExpiresAt());
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            try {
                requestMapper.insert(created);
            } catch (DuplicateKeyException duplicate) {
                PaymentRequest raced = requestMapper.selectOne(Wrappers.<PaymentRequest>lambdaQuery()
                        .eq(PaymentRequest::getOrderId, orderId)
                        .eq(PaymentRequest::getChannel, channel));
                if (raced == null) {
                    throw new BusinessException(ErrorCode.ORDER_SYNCING, "支付单创建中，请稍后重试");
                }
                return toResponse(raced);
            }
            return toResponse(created);
        });
        if (response == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "支付单创建未产生结果");
        }
        return response;
    }

    public TradeOrder closeByUser(Long userId, Long orderId) {
        TradeOrder result = transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            TradeOrder order = orderMapper.selectForUpdate(orderId);
            if (order == null || !order.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
            }
            if ("PAID".equals(order.getState())) {
                throw new BusinessException(ErrorCode.CONFLICT, "订单已支付，不能直接关闭");
            }
            if ("WAIT_PAY".equals(order.getState())) {
                orderMapper.markClosing(orderId, now, now);
            } else if (!"CLOSING".equals(order.getState()) && !"CLOSED".equals(order.getState())) {
                throw new BusinessException(ErrorCode.CONFLICT, "订单当前不可取消");
            }
            closeRequests(order, now);
            return orderMapper.selectById(orderId);
        });
        if (result == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "订单关闭未产生结果");
        }
        return result;
    }

    /**
     * 仅开发场景面板使用；真实运营关闭仍走用户取消或到期任务。
     */
    public TradeOrder closeBySystem(Long orderId) {
        TradeOrder result = transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            TradeOrder order = orderMapper.selectForUpdate(orderId);
            if (order == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
            }
            if ("WAIT_PAY".equals(order.getState())) {
                orderMapper.markClosing(orderId, now, now);
            }
            closeRequests(order, now);
            return orderMapper.selectById(orderId);
        });
        if (result == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "订单关闭未产生结果");
        }
        return result;
    }

    public int expireDueOrders(int limit) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<TradeOrder> due = orderMapper.selectList(Wrappers.<TradeOrder>lambdaQuery()
                .eq(TradeOrder::getState, "WAIT_PAY")
                .le(TradeOrder::getExpiresAt, now)
                .orderByAsc(TradeOrder::getExpiresAt)
                .last("LIMIT " + Math.min(100, Math.max(1, limit))));
        int closed = 0;
        for (TradeOrder candidate : due) {
            Boolean changed = transactionTemplate.execute(status -> {
                LocalDateTime current = LocalDateTime.now(ZoneOffset.UTC);
                TradeOrder order = orderMapper.selectForUpdate(candidate.getId());
                if (order == null || !"WAIT_PAY".equals(order.getState())
                        || order.getExpiresAt().isAfter(current)) {
                    return false;
                }
                orderMapper.markClosing(order.getId(), current, current);
                closeRequests(order, current);
                return true;
            });
            if (Boolean.TRUE.equals(changed)) {
                closed++;
            }
        }
        return closed;
    }

    public int retryUnknown(int limit) {
        List<PaymentRequest> unknown = requestMapper.selectList(Wrappers.<PaymentRequest>lambdaQuery()
                .eq(PaymentRequest::getState, "UNKNOWN")
                .orderByAsc(PaymentRequest::getUpdatedAt)
                .last("LIMIT " + Math.min(100, Math.max(1, limit))));
        int handled = 0;
        for (PaymentRequest candidate : unknown) {
            Boolean changed = transactionTemplate.execute(status -> {
                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                PaymentRequest request = requestMapper.selectForUpdate(candidate.getId());
                if (request == null || !"UNKNOWN".equals(request.getState())) {
                    return false;
                }
                PaymentPort.CloseResult close = paymentPort.close(request);
                if ("CLOSED".equals(close.state())) {
                    requestMapper.updateState(request.getId(), "CLOSED", now);
                    TradeOrder order = orderMapper.selectForUpdate(request.getOrderId());
                    if (order != null && !"PAID".equals(order.getState())) {
                        orderMapper.markClosed(order.getId(), "CLOSED", now);
                        pendingCourseMapper.deleteByOrderId(order.getId());
                    }
                    return true;
                }
                requestMapper.updateState(request.getId(), "UNKNOWN", now);
                return false;
            });
            if (Boolean.TRUE.equals(changed)) {
                handled++;
            }
        }
        return handled;
    }

    private void closeRequests(TradeOrder order, LocalDateTime now) {
        PaymentRequest request = requestMapper.selectOne(Wrappers.<PaymentRequest>lambdaQuery()
                .eq(PaymentRequest::getOrderId, order.getId())
                .orderByAsc(PaymentRequest::getId)
                .last("LIMIT 1"));
        if (request == null || "CLOSED".equals(request.getState())) {
            orderMapper.markClosed(order.getId(), "CLOSED", now);
            pendingCourseMapper.deleteByOrderId(order.getId());
            return;
        }
        PaymentPort.CloseResult close = paymentPort.close(request);
        if ("CLOSED".equals(close.state())) {
            requestMapper.updateState(request.getId(), "CLOSED", now);
            orderMapper.markClosed(order.getId(), "CLOSED", now);
            pendingCourseMapper.deleteByOrderId(order.getId());
        } else {
            requestMapper.updateState(request.getId(), "UNKNOWN", now);
        }
    }

    private PaymentInitiateResponse toResponse(PaymentRequest request) {
        PaymentPort.CreatedPayment created = paymentPort.create(request);
        return new PaymentInitiateResponse(request.getId(), created.payAction(), paymentPort.channel(),
                request.getState(), request.getExpiresAt());
    }
}