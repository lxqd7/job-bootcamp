package com.jobbootcamp.trade.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobbootcamp.trade.domain.PaymentRequest;
import com.jobbootcamp.trade.domain.TradeOrder;
import com.jobbootcamp.trade.domain.dto.AdminOrderItem;
import com.jobbootcamp.trade.domain.dto.PageResponse;
import com.jobbootcamp.trade.infrastructure.PaymentRequestMapper;
import com.jobbootcamp.trade.infrastructure.TradeOrderMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminOrderService {

    private final TradeOrderMapper orderMapper;
    private final PaymentRequestMapper paymentRequestMapper;

    public AdminOrderService(TradeOrderMapper orderMapper, PaymentRequestMapper paymentRequestMapper) {
        this.orderMapper = orderMapper;
        this.paymentRequestMapper = paymentRequestMapper;
    }

    public PageResponse<AdminOrderItem> list(String state, int page, int pageSize) {
        long pageNumber = Math.max(1, page);
        long size = Math.min(100, Math.max(1, pageSize));
        IPage<TradeOrder> result = orderMapper.selectPage(new Page<>(pageNumber, size),
                Wrappers.<TradeOrder>lambdaQuery()
                        .eq(state != null && !state.isBlank(), TradeOrder::getState, state)
                        .orderByDesc(TradeOrder::getCreatedAt)
                        .orderByDesc(TradeOrder::getId));
        List<AdminOrderItem> items = result.getRecords().stream().map(order -> {
            PaymentRequest request = paymentRequestMapper.selectOne(Wrappers.<PaymentRequest>lambdaQuery()
                    .eq(PaymentRequest::getOrderId, order.getId())
                    .orderByDesc(PaymentRequest::getId)
                    .last("LIMIT 1"));
            return new AdminOrderItem(order.getId(), order.getOrderNo(), order.getUserId(),
                    order.getState(), order.getFulfillmentState(), order.getPayableCent(),
                    order.getCreatedAt(), order.getExpiresAt(), order.getPaidAt(),
                    request == null ? null : request.getId(),
                    request == null ? null : request.getState());
        }).toList();
        return new PageResponse<>(items, result.getTotal(), pageNumber, size);
    }
}