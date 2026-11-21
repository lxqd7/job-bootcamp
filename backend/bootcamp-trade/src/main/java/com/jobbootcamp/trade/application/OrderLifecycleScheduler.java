package com.jobbootcamp.trade.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单到期/关闭未知重试任务；它只推进 CLOSING/CLOSED，不把未确认收款当成已支付。
 */
@Component
public class OrderLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderLifecycleScheduler.class);

    private final PaymentService paymentService;

    public OrderLifecycleScheduler(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelayString = "${app.payment.lifecycle-poll-ms:30000}")
    public void poll() {
        try {
            int closed = paymentService.expireDueOrders(20);
            int retried = paymentService.retryUnknown(20);
            if (closed > 0 || retried > 0) {
                log.info("trade lifecycle closed={} unknownRetried={}", closed, retried);
            }
        } catch (RuntimeException e) {
            log.warn("trade lifecycle poll failed: {}", e.getMessage());
        }
    }
}