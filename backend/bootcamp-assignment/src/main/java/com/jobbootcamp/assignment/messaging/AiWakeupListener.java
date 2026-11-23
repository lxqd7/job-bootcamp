package com.jobbootcamp.assignment.messaging;

import com.jobbootcamp.assignment.application.AiReviewWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.wakeup-enabled", havingValue = "true")
public class AiWakeupListener {

    private static final Logger log = LoggerFactory.getLogger(AiWakeupListener.class);

    private final AiReviewWorker worker;

    public AiWakeupListener(AiReviewWorker worker) {
        this.worker = worker;
    }

    @RabbitListener(queues = "${app.ai.wakeup.queue:bc.assignment.review-requested.v1}")
    public void onQueued(Message message) {
        try {
            worker.wake();
        } catch (RuntimeException e) {
            // 消息只是唤醒；处理失败时让扫描继续兜底，避免无限重投。
            log.warn("AI wakeup handling failed: {}", e.getClass().getSimpleName());
        }
    }
}