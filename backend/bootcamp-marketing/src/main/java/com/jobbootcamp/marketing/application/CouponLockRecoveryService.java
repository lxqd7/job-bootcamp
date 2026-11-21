package com.jobbootcamp.marketing.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.contracts.internal.CouponLockStateRequest;
import com.jobbootcamp.contracts.internal.OrderStatusBatchResponse;
import com.jobbootcamp.marketing.domain.CouponLock;
import com.jobbootcamp.marketing.infrastructure.CouponLockMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 锁券对账：只处理超过宽限期的 LOCKED 记录，先向交易读取订单状态，再决定核销或释放。
 * CLOSING/WAIT_PAY 不动，避免把支付结果未知的订单提前释放。
 */
@Service
public class CouponLockRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(CouponLockRecoveryService.class);
    private static final int GRACE_SECONDS = 120;

    private final CouponLockMapper lockMapper;
    private final CouponLockService lockService;
    private final TradeOrderStatusClient tradeClient;

    public CouponLockRecoveryService(CouponLockMapper lockMapper,
                                     CouponLockService lockService,
                                     TradeOrderStatusClient tradeClient) {
        this.lockMapper = lockMapper;
        this.lockService = lockService;
        this.tradeClient = tradeClient;
    }

    @Scheduled(fixedDelayString = "${app.coupon.recovery-poll-ms:30000}")
    public void poll() {
        try {
            int handled = recover(50);
            if (handled > 0) {
                log.info("coupon lock recovery handled={}", handled);
            }
        } catch (RuntimeException e) {
            log.warn("coupon lock recovery failed: {}", e.getMessage());
        }
    }

    public int recover(int limit) {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(GRACE_SECONDS);
        List<CouponLock> candidates = lockMapper.selectList(Wrappers.<CouponLock>lambdaQuery()
                .eq(CouponLock::getState, "LOCKED")
                .le(CouponLock::getUpdatedAt, cutoff)
                .orderByAsc(CouponLock::getUpdatedAt)
                .last("LIMIT " + Math.min(200, Math.max(1, limit * 3))));
        Map<Long, CouponLock> byOrder = new LinkedHashMap<>();
        for (CouponLock lock : candidates) {
            byOrder.putIfAbsent(lock.getOrderId(), lock);
            if (byOrder.size() >= Math.min(100, Math.max(1, limit))) {
                break;
            }
        }
        if (byOrder.isEmpty()) {
            return 0;
        }
        OrderStatusBatchResponse response = tradeClient.statuses(new ArrayList<>(byOrder.keySet()));
        Map<Long, String> states = new LinkedHashMap<>();
        if (response != null && response.orders() != null) {
            for (OrderStatusBatchResponse.OrderStatusItem item : response.orders()) {
                states.put(item.orderId(), item.state());
            }
        }
        int handled = 0;
        for (Map.Entry<Long, CouponLock> entry : byOrder.entrySet()) {
            String state = states.get(entry.getKey());
            CouponLock lock = entry.getValue();
            if ("PAID".equals(state)) {
                lockService.use(new CouponLockStateRequest(lock.getOrderId(), lock.getUserId(),
                        lock.getPricingHash(), "PAID"));
                handled++;
            } else if ("CLOSED".equals(state) || state == null) {
                // 订单查询成功但没有该订单，说明建单事务已回滚；释放孤儿锁。
                lockService.release(new CouponLockStateRequest(lock.getOrderId(), lock.getUserId(),
                        lock.getPricingHash(), "CLOSED"));
                handled++;
            }
        }
        return handled;
    }
}