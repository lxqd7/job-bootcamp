package com.jobbootcamp.trade.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.contracts.internal.OrderStatusBatchRequest;
import com.jobbootcamp.contracts.internal.OrderStatusBatchResponse;
import com.jobbootcamp.trade.domain.TradeOrder;
import com.jobbootcamp.trade.infrastructure.TradeOrderMapper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@RestController
@RequestMapping("/int/v1/orders")
public class InternalOrderController {

    private final TradeOrderMapper orderMapper;

    public InternalOrderController(TradeOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @PostMapping("/status-batch")
    public OrderStatusBatchResponse statusBatch(@RequestBody OrderStatusBatchRequest request) {
        if (request == null || request.orderIds() == null || request.orderIds().isEmpty()
                || request.orderIds().size() > 100
                || new HashSet<>(request.orderIds()).size() != request.orderIds().size()) {
            return new OrderStatusBatchResponse(List.of());
        }
        List<TradeOrder> orders = orderMapper.selectList(Wrappers.<TradeOrder>lambdaQuery()
                .in(TradeOrder::getId, request.orderIds()));
        List<OrderStatusBatchResponse.OrderStatusItem> items = new ArrayList<>();
        for (TradeOrder order : orders) {
            items.add(new OrderStatusBatchResponse.OrderStatusItem(order.getId(), order.getState()));
        }
        return new OrderStatusBatchResponse(items);
    }
}