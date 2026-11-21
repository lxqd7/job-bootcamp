package com.jobbootcamp.trade.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbootcamp.trade.application.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 消费 learning 的 grant 回执，回填订单课程来源并结束 fulfillment；重复回执幂等。
 */
@Component
@ConditionalOnProperty(name = "app.outbox.transport", havingValue = "rabbit")
public class OrderGrantListener {

    private static final Logger log = LoggerFactory.getLogger(OrderGrantListener.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final OrderService orderService;

    public OrderGrantListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = RabbitTopologyConfig.GRANT_ACK_QUEUE)
    public void onGranted(Message message) throws Exception {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        JsonNode root = JSON.readTree(payload);
        long orderId = root.path("orderId").asLong(0);
        long userId = root.path("userId").asLong(0);
        long sourceId = root.path("sourceId").asLong(0);
        JsonNode grantsNode = root.path("grants");
        if (orderId <= 0 || userId <= 0 || sourceId <= 0 || !grantsNode.isArray()) {
            throw new IllegalArgumentException("order-granted payload missing required fields");
        }
        List<OrderService.GrantAck> grants = new ArrayList<>();
        for (JsonNode grant : grantsNode) {
            grants.add(new OrderService.GrantAck(grant.path("courseId").asLong(0),
                    grant.path("grantId").asLong(0)));
        }
        orderService.markGranted(orderId, userId, sourceId, grants);
        log.info("trade order fulfillment updated order={} grants={}", orderId, grants.size());
    }
}