package com.jobbootcamp.assignment.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "app.ai.wakeup-enabled", havingValue = "true")
public class AiWakeupPublisher {

    private static final Logger log = LoggerFactory.getLogger(AiWakeupPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public AiWakeupPublisher(RabbitTemplate rabbitTemplate,
                             @Value("${app.ai.wakeup.exchange:bc.domain.v1}") String exchange,
                             @Value("${app.ai.wakeup.routing-key:assignment.review-requested.v1}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public void publish(Long taskId, Long submissionId, Long userId) {
        try {
            String payload = "{\"taskId\":" + taskId + ",\"submissionId\":" + submissionId
                    + ",\"userId\":" + userId + "}";
            Message message = MessageBuilder
                    .withBody(payload.getBytes(StandardCharsets.UTF_8))
                    .setContentType("application/json")
                    .setContentEncoding("UTF-8")
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .build();
            rabbitTemplate.send(exchange, routingKey, message);
        } catch (AmqpException e) {
            // 唤醒消息不是事实源；发布失败由定时扫描补上。
            log.warn("AI wakeup publish failed for task {}; scan will retry", taskId);
        }
    }
}