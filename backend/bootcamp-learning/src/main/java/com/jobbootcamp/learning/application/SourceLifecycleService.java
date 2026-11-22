package com.jobbootcamp.learning.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.SourceLifecycleRequest;
import com.jobbootcamp.contracts.internal.SourceLifecycleResponse;
import com.jobbootcamp.learning.domain.EntitlementSource;
import com.jobbootcamp.learning.domain.OutboxEvent;
import com.jobbootcamp.learning.domain.RefundIntent;
import com.jobbootcamp.learning.infrastructure.EntitlementSourceMapper;
import com.jobbootcamp.learning.infrastructure.OutboxEventMapper;
import com.jobbootcamp.learning.infrastructure.RefundIntentMapper;
import com.jobbootcamp.learning.infrastructure.UserGuardMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * 学习侧来源生命周期：渠道退款成功后撤销，明确关闭/资格失败后恢复。
 * 只接受本订单的 FROZEN 来源，并用 lifecycle_version 做 fencing，旧命令不能覆盖新状态。
 */
@Service
public class SourceLifecycleService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final EntitlementSourceMapper sourceMapper;
    private final RefundIntentMapper intentMapper;
    private final UserGuardMapper guardMapper;
    private final OutboxEventMapper outboxMapper;
    private final TransactionTemplate transactionTemplate;

    public SourceLifecycleService(EntitlementSourceMapper sourceMapper,
                                  RefundIntentMapper intentMapper,
                                  UserGuardMapper guardMapper,
                                  OutboxEventMapper outboxMapper,
                                  PlatformTransactionManager transactionManager) {
        this.sourceMapper = sourceMapper;
        this.intentMapper = intentMapper;
        this.guardMapper = guardMapper;
        this.outboxMapper = outboxMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public SourceLifecycleResponse revoke(SourceLifecycleRequest request) {
        return transactionTemplate.execute(status -> revokeInTransaction(request));
    }

    public SourceLifecycleResponse restore(SourceLifecycleRequest request) {
        return transactionTemplate.execute(status -> restoreInTransaction(request));
    }

    private SourceLifecycleResponse revokeInTransaction(SourceLifecycleRequest request) {
        guardMapper.selectForUpdate(request.userId());
        EntitlementSource source = source(request);
        RefundIntent intent = intent(request);
        if ("REVOKED".equals(source.getState())) {
            return new SourceLifecycleResponse(source.getId(), source.getState(), source.getLifecycleVersion());
        }
        if (!"FROZEN".equals(source.getState())) {
            throw new BusinessException(ErrorCode.CONFLICT, "来源当前不可撤销");
        }
        verifyVersion(source, request.expectedSourceVersion());
        source.setState("REVOKED");
        source.setLifecycleVersion(source.getLifecycleVersion() + 1);
        source.setUpdatedAt(now());
        sourceMapper.updateById(source);
        intentMapper.updateState(intent.getId(), "COMPLETED", null, null, now());
        writeEvent("learning.revocation-completed.v1", request, source);
        return new SourceLifecycleResponse(source.getId(), source.getState(), source.getLifecycleVersion());
    }

    private SourceLifecycleResponse restoreInTransaction(SourceLifecycleRequest request) {
        guardMapper.selectForUpdate(request.userId());
        EntitlementSource source = source(request);
        RefundIntent intent = intent(request);
        if ("ACTIVE".equals(source.getState())) {
            return new SourceLifecycleResponse(source.getId(), source.getState(), source.getLifecycleVersion());
        }
        // REVOKED 不能通过恢复命令复活；只有仍处于 FROZEN 的申请可以恢复。
        if (!"FROZEN".equals(source.getState())) {
            throw new BusinessException(ErrorCode.CONFLICT, "来源当前不可恢复");
        }
        verifyVersion(source, request.expectedSourceVersion());
        source.setState("ACTIVE");
        source.setLifecycleVersion(source.getLifecycleVersion() + 1);
        source.setUpdatedAt(now());
        sourceMapper.updateById(source);
        intentMapper.updateState(intent.getId(), "CLOSED", "CHANNEL_CLOSED", null, now());
        writeEvent("learning.restore-completed.v1", request, source);
        return new SourceLifecycleResponse(source.getId(), source.getState(), source.getLifecycleVersion());
    }

    private EntitlementSource source(SourceLifecycleRequest request) {
        if (request == null || request.userId() == null || request.orderId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "来源生命周期参数不合法");
        }
        EntitlementSource source = sourceMapper.selectOne(Wrappers.<EntitlementSource>lambdaQuery()
                .eq(EntitlementSource::getUserId, request.userId())
                .eq(EntitlementSource::getOrderId, request.orderId())
                .in(EntitlementSource::getType, List.of("COURSE_ORDER", "ROUTE_ORDER")));
        if (source == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单来源不存在");
        }
        return source;
    }

    private RefundIntent intent(SourceLifecycleRequest request) {
        RefundIntent intent = intentMapper.selectById(request.intentId());
        if (intent == null || !intent.getUserId().equals(request.userId())
                || !intent.getOrderId().equals(request.orderId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "退款申请不存在");
        }
        return intent;
    }

    private void verifyVersion(EntitlementSource source, Long expected) {
        if (expected != null && !expected.equals(source.getLifecycleVersion())) {
            throw new BusinessException(ErrorCode.CONFLICT, "来源生命周期版本已变化");
        }
    }

    private void writeEvent(String type, SourceLifecycleRequest request, EntitlementSource source) {
        try {
            String payload = JSON.writeValueAsString(new SourceEvent(request.refundId(), request.intentId(),
                    request.orderId(), request.userId(), source.getId(), source.getState(),
                    source.getLifecycleVersion(), now().toInstant(ZoneOffset.UTC).toString()));
            OutboxEvent event = new OutboxEvent();
            event.setEventId(UUID.randomUUID().toString());
            event.setType(type);
            event.setAggregateId(String.valueOf(request.refundId()));
            event.setAggregateVersion(source.getLifecycleVersion());
            event.setOccurredAt(now());
            event.setPayload(payload);
            event.setStatus("PENDING");
            event.setAttempt(0);
            event.setNextRetryAt(now());
            event.setCreatedAt(now());
            event.setUpdatedAt(now());
            outboxMapper.insert(event);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "来源生命周期事件序列化失败");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private record SourceEvent(Long refundId, Long intentId, Long orderId, Long userId,
                               Long sourceId, String state, Long sourceVersion, String occurredAt) {
    }
}