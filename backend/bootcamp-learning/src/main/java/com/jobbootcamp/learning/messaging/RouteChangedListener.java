package com.jobbootcamp.learning.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbootcamp.learning.application.EntitlementService;
import com.jobbootcamp.learning.application.RouteEpochChange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 消费路线版本变更：新增 epoch 给已有路线资格用户补发课程，移除 epoch 只关闭投影。
 * 事件重复由 grant/epoch 唯一键吸收，不删除既有课程 grant。
 */
@Component
@ConditionalOnProperty(name = "app.outbox.transport", havingValue = "rabbit")
public class RouteChangedListener {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final EntitlementService entitlementService;

    public RouteChangedListener(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @RabbitListener(queues = RabbitTopologyConfig.ROUTE_QUEUE)
    public void onRouteChanged(Message message) throws Exception {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        JsonNode root = JSON.readTree(payload);
        long routeId = root.path("routeId").asLong(0);
        long version = root.path("version").asLong(0);
        if (routeId <= 0 || version <= 0) {
            throw new IllegalArgumentException("route-changed payload missing routeId/version");
        }
        LocalDateTime eventAt = parseTime(root.path("effectiveAt").asText(null));
        List<RouteEpochChange> added = new ArrayList<>();
        JsonNode addedNode = root.path("addedEpochs");
        if (addedNode.isArray()) {
            for (JsonNode item : addedNode) {
                added.add(new RouteEpochChange(
                        item.path("courseId").asLong(0),
                        item.path("epochId").asLong(0),
                        parseTime(item.path("joinedAt").asText(null)),
                        item.path("isFree").asBoolean(false),
                        version));
            }
        }
        List<Long> removed = new ArrayList<>();
        JsonNode removedNode = root.path("removedEpochIds");
        if (removedNode.isArray()) {
            for (JsonNode item : removedNode) {
                if (item.isNumber()) {
                    removed.add(item.asLong());
                } else if (item.isTextual()) {
                    removed.add(Long.valueOf(item.asText()));
                }
            }
        }
        entitlementService.applyRouteChange(routeId, version, added, removed,
                eventAt == null ? LocalDateTime.now(ZoneOffset.UTC) : eventAt);
    }

    private LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(text), ZoneOffset.UTC);
        } catch (RuntimeException e) {
            return LocalDateTime.parse(text);
        }
    }
}