package com.jobbootcamp.marketing.application;

import com.jobbootcamp.marketing.domain.SignRecord;
import com.jobbootcamp.marketing.infrastructure.SignRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class SignCacheService {

    private static final Logger log = LoggerFactory.getLogger(SignCacheService.class);

    private final StringRedisTemplate redis;
    private final SignRecordMapper signMapper;
    private final boolean enabled;
    private final String prefix;

    public SignCacheService(StringRedisTemplate redis,
                            SignRecordMapper signMapper,
                            @Value("${app.redis.enabled:true}") boolean enabled,
                            @Value("${app.redis.key-prefix:bc:dev:}") String prefix) {
        this.redis = redis;
        this.signMapper = signMapper;
        this.enabled = enabled;
        this.prefix = prefix;
    }

    public void mark(long userId, LocalDate date) {
        if (!enabled) {
            return;
        }
        try {
            redis.opsForValue().setBit(key(userId, YearMonth.from(date)), date.getDayOfMonth() - 1L, true);
        } catch (RuntimeException e) {
            log.warn("sign bitmap update skipped: {}", e.getMessage());
        }
    }

    public void ensure(long userId, YearMonth month) {
        if (!enabled) {
            return;
        }
        try {
            if (Boolean.TRUE.equals(redis.hasKey(key(userId, month)))) {
                return;
            }
            rebuild(userId, month);
        } catch (RuntimeException e) {
            log.warn("sign bitmap rebuild skipped: {}", e.getMessage());
        }
    }

    public void rebuild(long userId, YearMonth month) {
        if (!enabled) {
            return;
        }
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        List<SignRecord> records = signMapper.selectBetween(userId, from, to);
        String key = key(userId, month);
        try {
            redis.delete(key);
            if (records.isEmpty()) {
                return;
            }
            byte[] rawKey = key.getBytes(StandardCharsets.UTF_8);
            redis.executePipelined((RedisCallback<Object>) connection -> {
                for (SignRecord record : records) {
                    connection.stringCommands().setBit(rawKey, record.getBusinessDate().getDayOfMonth() - 1L, true);
                }
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("sign bitmap rebuild failed: {}", e.getMessage());
        }
    }

    private String key(long userId, YearMonth month) {
        return prefix + "sign:" + userId + ":" + month;
    }
}