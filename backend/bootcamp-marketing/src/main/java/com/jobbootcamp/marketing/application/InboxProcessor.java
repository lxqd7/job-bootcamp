package com.jobbootcamp.marketing.application;

import com.jobbootcamp.contracts.event.EventEnvelope;
import com.jobbootcamp.marketing.domain.InboxEventRow;
import com.jobbootcamp.marketing.infrastructure.InboxEventMapper;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 消费端幂等：eventId 与业务写入在同一事务内提交；重复 eventId 直接返回 false，
 * 未完成的事务不会留下可抢占的已处理标记。
 * 同一 JVM 内对同一 eventId 串行化，避免大量相同幂等请求同时撞唯一索引；
 * 数据库死锁作为可重试冲突处理，而不是向外返回 500。
 */
@Service
public class InboxProcessor {

    private final InboxEventMapper inboxMapper;
    private final TransactionTemplate transactionTemplate;
    private final ConcurrentHashMap<String, Object> eventLocks = new ConcurrentHashMap<>();

    public InboxProcessor(InboxEventMapper inboxMapper, PlatformTransactionManager transactionManager) {
        this.inboxMapper = inboxMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 领域写入与 inbox 记录同一事务；重复 eventId 不再执行 handler。
     */
    public boolean process(String consumer, EventEnvelope event, String businessKey,
                           Consumer<EventEnvelope> handler) {
        String lockKey = consumer + '\0' + event.eventId();
        Object lock = eventLocks.computeIfAbsent(lockKey, ignored -> new Object());
        try {
            synchronized (lock) {
                return processOnce(consumer, event, businessKey, handler);
            }
        } finally {
            eventLocks.remove(lockKey, lock);
        }
    }

    private boolean processOnce(String consumer, EventEnvelope event, String businessKey,
                                Consumer<EventEnvelope> handler) {
        for (int attempt = 1; attempt <= 5; attempt++) {
            if (inboxMapper.select(consumer, event.eventId()) != null) {
                return false;
            }
            try {
                Boolean executed = transactionTemplate.execute(status -> {
                    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                    try {
                        inboxMapper.insertProcessing(consumer, event.eventId(), businessKey,
                                sha256(event.payloadJson()), event.payloadJson(), now);
                    } catch (DataIntegrityViolationException e) {
                        throw new InboxDuplicateException();
                    }
                    handler.accept(event);
                    inboxMapper.markProcessed(consumer, event.eventId(), now);
                    return true;
                });
                return Boolean.TRUE.equals(executed);
            } catch (InboxDuplicateException e) {
                return false;
            } catch (DeadlockLoserDataAccessException | CannotAcquireLockException e) {
                if (attempt == 5) {
                    throw e;
                }
                try {
                    Thread.sleep(20L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("inbox retry interrupted", interrupted);
                }
            }
        }
        return false;
    }

    public InboxEventRow find(String consumer, String eventId) {
        return inboxMapper.select(consumer, eventId);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static final class InboxDuplicateException extends RuntimeException {
    }
}