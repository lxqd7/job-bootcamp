package com.jobbootcamp.contracts.event;

import java.time.Instant;

/**
 * 跨服务事件外壳。payload 保留原始 JSON，避免公共模块绑定具体序列化实现。
 */
public record EventEnvelope(
        String eventId,
        String type,
        int schemaVersion,
        String aggregateId,
        long aggregateVersion,
        Instant occurredAt,
        String traceId,
        String payloadJson
) {
}