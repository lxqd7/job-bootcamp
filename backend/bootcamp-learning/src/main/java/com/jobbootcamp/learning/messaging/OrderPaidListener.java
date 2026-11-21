package com.jobbootcamp.learning.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbootcamp.learning.application.EntitlementService;
import com.jobbootcamp.learning.application.PurchaseCourse;
import com.jobbootcamp.learning.application.PurchaseGrantResult;
import com.jobbootcamp.learning.application.RoutePurchaseMember;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 消费 trade 的支付成功事件，按订单建立 COURSE_ORDER 来源并回传 grant 明细。
 * 授权与回传 ack 都是幂等的；ack 发送失败会抛异常让 RabbitMQ 重投，不能静默丢开通结果。
 */
@Component
@ConditionalOnProperty(name = "app.outbox.transport", havingValue = "rabbit")
public class OrderPaidListener {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final EntitlementService entitlementService;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;

    public OrderPaidListener(EntitlementService entitlementService,
                             RabbitTemplate rabbitTemplate,
                             @Value("${app.outbox.exchange:bc.domain.v1}") String exchange) {
        this.entitlementService = entitlementService;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    @RabbitListener(queues = RabbitTopologyConfig.ORDER_PAID_QUEUE)
    public void onPaid(Message message) throws Exception {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        JsonNode root = JSON.readTree(payload);
        long orderId = requiredLong(root, "orderId");
        long userId = requiredLong(root, "userId");
        String paidAtText = requiredText(root, "paidAt");
        LocalDateTime paidAt = parsePaidAt(paidAtText);
        PurchaseGrantResult result;
        if ("ROUTE".equals(root.path("productType").asText("COURSE"))) {
            long routeId = requiredLong(root, "routeId");
            JsonNode membersNode = root.path("members");
            if (!membersNode.isArray() || membersNode.isEmpty()) {
                throw new IllegalArgumentException("route payment payload has no members");
            }
            List<RoutePurchaseMember> members = new ArrayList<>();
            for (JsonNode member : membersNode) {
                members.add(new RoutePurchaseMember(requiredLong(member, "courseId"),
                        member.path("courseVersion").asLong(0),
                        member.path("epochId").isNumber() ? member.path("epochId").asLong() : null,
                        member.path("free").asBoolean(false),
                        member.path("newPaid").asBoolean(false)));
            }
            result = entitlementService.grantRoutePurchase(orderId, userId, paidAt, routeId, members);
        } else {
            JsonNode coursesNode = root.path("courses");
            if (!coursesNode.isArray() || coursesNode.isEmpty()) {
                throw new IllegalArgumentException("payment-confirmed payload has no courses");
            }
            List<PurchaseCourse> courses = new ArrayList<>();
            for (JsonNode course : coursesNode) {
                courses.add(new PurchaseCourse(requiredLong(course, "courseId"),
                        course.path("courseVersion").asLong(0)));
            }
            result = entitlementService.grantPurchase(orderId, userId, paidAt, courses);
        }
        publishGranted(orderId, userId, result);
    }

    private LocalDateTime parsePaidAt(String text) {
        // 兼容历史事件的时间格式：新事件带 Z，旧事件可能是 LocalDateTime 字符串。
        try {
            return LocalDateTime.ofInstant(Instant.parse(text), ZoneOffset.UTC);
        } catch (RuntimeException e) {
            return LocalDateTime.parse(text);
        }
    }

    private void publishGranted(long orderId, long userId, PurchaseGrantResult result) throws Exception {
        String eventId = UUID.randomUUID().toString();
        StringBuilder grants = new StringBuilder("[");
        for (int i = 0; i < result.grants().size(); i++) {
            if (i > 0) {
                grants.append(',');
            }
            PurchaseGrantResult.Grant grant = result.grants().get(i);
            grants.append("{\"courseId\":").append(grant.courseId())
                    .append(",\"grantId\":").append(grant.grantId()).append('}');
        }
        grants.append(']');
        String payload = "{\"orderId\":\"" + orderId
                + "\",\"userId\":\"" + userId
                + "\",\"sourceId\":\"" + result.sourceId()
                + "\",\"grants\":" + grants + "}";
        Message message = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setContentEncoding("UTF-8")
                .setMessageId(eventId)
                .setType("learning.order-granted.v1")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        CorrelationData correlation = new CorrelationData(eventId);
        rabbitTemplate.send(exchange, "learning.order-granted.v1", message, correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
        if (confirm == null || !confirm.isAck()) {
            throw new IllegalStateException("order-granted event was not confirmed by RabbitMQ");
        }
    }

    private long requiredLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isNumber()) {
            throw new IllegalArgumentException("missing numeric field " + field);
        }
        return value.asLong();
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("missing text field " + field);
        }
        return value.asText();
    }
}