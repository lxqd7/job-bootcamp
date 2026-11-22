package com.jobbootcamp.trade.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobbootcamp.trade.domain.Refund;
import com.jobbootcamp.trade.domain.dto.AdminRefundItem;
import com.jobbootcamp.trade.domain.dto.PageResponse;
import com.jobbootcamp.trade.infrastructure.RefundMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminRefundService {

    private final RefundMapper refundMapper;

    public AdminRefundService(RefundMapper refundMapper) {
        this.refundMapper = refundMapper;
    }

    public PageResponse<AdminRefundItem> list(String state, int page, int pageSize) {
        long pageNumber = Math.max(1, page);
        long size = Math.min(100, Math.max(1, pageSize));
        IPage<Refund> result = refundMapper.selectPage(new Page<>(pageNumber, size),
                Wrappers.<Refund>lambdaQuery()
                        .eq(state != null && !state.isBlank(), Refund::getState, state)
                        .orderByDesc(Refund::getUpdatedAt)
                        .orderByDesc(Refund::getId));
        List<AdminRefundItem> items = result.getRecords().stream()
                .map(refund -> new AdminRefundItem(refund.getId(), refund.getRefundNo(),
                        refund.getOrderId(), refund.getUserId(), refund.getKind(), refund.getState(),
                        refund.getAmountCent(), refund.getProgressN(), refund.getProgressD(),
                        refund.getChannelRefundNo(), refund.getAcceptedAt(), refund.getUpdatedAt()))
                .toList();
        return new PageResponse<>(items, result.getTotal(), pageNumber, size);
    }
}