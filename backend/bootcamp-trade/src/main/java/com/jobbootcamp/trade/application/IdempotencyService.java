package com.jobbootcamp.trade.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.trade.domain.IdempotencyRecord;
import com.jobbootcamp.trade.infrastructure.IdempotencyMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class IdempotencyService {

    public static final String OPERATION_CREATE_ORDER = "CREATE_ORDER";

    private final IdempotencyMapper mapper;

    public IdempotencyService(IdempotencyMapper mapper) {
        this.mapper = mapper;
    }

    public IdempotencyRecord find(Long userId, String key) {
        return mapper.selectOne(Wrappers.<IdempotencyRecord>lambdaQuery()
                .eq(IdempotencyRecord::getActorId, userId)
                .eq(IdempotencyRecord::getOperation, OPERATION_CREATE_ORDER)
                .eq(IdempotencyRecord::getRequestKey, key));
    }

    public Claim claim(Long userId, String key, String requestHash) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        IdempotencyRecord record = new IdempotencyRecord();
        record.setActorId(userId);
        record.setOperation(OPERATION_CREATE_ORDER);
        record.setRequestKey(key);
        record.setRequestHash(requestHash);
        record.setStatus("IN_PROGRESS");
        record.setExpiresAt(now.plusDays(1));
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        try {
            mapper.insert(record);
            return new Claim(record.getId(), true, null);
        } catch (DuplicateKeyException duplicate) {
            return awaitExisting(userId, key, requestHash);
        }
    }

    public void complete(Long recordId, Long orderId) {
        mapper.complete(recordId, String.valueOf(orderId), "{\"orderId\":" + orderId + "}",
                LocalDateTime.now(ZoneOffset.UTC));
    }

    public void fail(Long recordId) {
        mapper.fail(recordId, LocalDateTime.now(ZoneOffset.UTC));
    }

    private Claim awaitExisting(Long userId, String key, String requestHash) {
        for (int attempt = 0; attempt < 100; attempt++) {
            IdempotencyRecord existing = find(userId, key);
            if (existing == null) {
                throw new BusinessException(ErrorCode.ORDER_SYNCING, "订单创建状态同步中，请稍后重试");
            }
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new BusinessException(ErrorCode.CONFLICT, "该幂等键已用于不同的下单请求");
            }
            if ("COMPLETED".equals(existing.getStatus())) {
                return new Claim(existing.getId(), false, existing.getResourceId());
            }
            if ("FAILED".equals(existing.getStatus())) {
                int reclaimed = mapper.reclaim(existing.getId(), LocalDateTime.now(ZoneOffset.UTC));
                if (reclaimed == 1) {
                    return new Claim(existing.getId(), true, null);
                }
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.ORDER_SYNCING, "订单创建被中断，请稍后重试");
            }
        }
        throw new BusinessException(ErrorCode.ORDER_SYNCING, "订单创建处理中，请稍后重试");
    }

    public record Claim(Long recordId, boolean owner, String resourceId) {
    }
}