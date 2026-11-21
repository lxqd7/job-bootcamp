package com.jobbootcamp.trade.messaging;

import com.jobbootcamp.contracts.event.EventEnvelope;
import com.jobbootcamp.trade.domain.OutboxEvent;
import com.jobbootcamp.trade.infrastructure.OutboxEventMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 交易事件投递器；支付事实已先落库，发送失败只影响异步开通，不影响已支付状态。
 */
@Service
public class OutboxRelay {

    private static final int MAX_ATTEMPTS = 10;

    private final OutboxEventMapper outboxMapper;
    private final EventTransport transport;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final int leaseSeconds;

    public OutboxRelay(OutboxEventMapper outboxMapper,
                       EventTransport transport,
                       PlatformTransactionManager transactionManager,
                       @Value("${app.outbox.batch-size:20}") int batchSize,
                       @Value("${app.outbox.lease-seconds:30}") int leaseSeconds) {
        this.outboxMapper = outboxMapper;
        this.transport = transport;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
    }

    public RelayResult relayOnce() {
        return relayOnce(batchSize);
    }

    public RelayResult relayOnce(int limit) {
        int safeLimit = Math.min(100, Math.max(1, limit));
        List<Claimed> claimed = transactionTemplate.execute(status -> claim(safeLimit));
        if (claimed == null || claimed.isEmpty()) {
            return new RelayResult(0, 0, 0);
        }
        int sent = 0;
        int failed = 0;
        for (Claimed item : claimed) {
            OutboxEvent event = item.event();
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            try {
                transport.publish(toEnvelope(event));
                outboxMapper.markSent(event.getId(), item.leaseToken(), now);
                sent++;
            } catch (EventTransportException e) {
                scheduleRetry(event, item.leaseToken(), e.getCode(), now);
                failed++;
            } catch (RuntimeException e) {
                scheduleRetry(event, item.leaseToken(), "PUBLISH_FAILED", now);
                failed++;
            }
        }
        return new RelayResult(claimed.size(), sent, failed);
    }

    private List<Claimed> claim(int limit) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<OutboxEvent> candidates = outboxMapper.selectClaimable(now, limit);
        List<Claimed> claimed = new ArrayList<>();
        LocalDateTime leaseUntil = now.plusSeconds(Math.max(5, leaseSeconds));
        for (OutboxEvent event : candidates) {
            String token = UUID.randomUUID().toString();
            if (outboxMapper.claim(event.getId(), token, leaseUntil, now) == 1) {
                claimed.add(new Claimed(event, token));
            }
        }
        return claimed;
    }

    private void scheduleRetry(OutboxEvent event, String leaseToken, String errorCode, LocalDateTime now) {
        int attempt = event.getAttempt() == null ? 1 : event.getAttempt() + 1;
        String status = attempt >= MAX_ATTEMPTS ? "NEEDS_ATTENTION" : "PENDING";
        LocalDateTime nextRetry = now.plusSeconds(backoffSeconds(attempt));
        outboxMapper.markRetry(event.getId(), leaseToken, status, errorCode, nextRetry, now);
    }

    private long backoffSeconds(int attempt) {
        return switch (attempt) {
            case 1 -> 5;
            case 2 -> 30;
            case 3 -> 120;
            case 4 -> 600;
            default -> 1800;
        };
    }

    private EventEnvelope toEnvelope(OutboxEvent event) {
        return new EventEnvelope(event.getEventId(), event.getType(), 1, event.getAggregateId(),
                event.getAggregateVersion(), event.getOccurredAt().toInstant(ZoneOffset.UTC),
                event.getTraceId(), event.getPayload());
    }

    public record RelayResult(int claimed, int sent, int failed) {
    }

    private record Claimed(OutboxEvent event, String leaseToken) {
    }
}