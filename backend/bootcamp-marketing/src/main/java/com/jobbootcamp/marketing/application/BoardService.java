package com.jobbootcamp.marketing.application;

import com.jobbootcamp.marketing.domain.BoardRow;
import com.jobbootcamp.marketing.domain.dto.BoardItemView;
import com.jobbootcamp.marketing.domain.dto.BoardResponse;
import com.jobbootcamp.marketing.infrastructure.BoardMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 月榜投影。Redis ZSet 用负分实现同分时 userId 升序；缓存带 MySQL revision，
 * 读失败或数据缺失时回退 MySQL，缓存只做加速，不作为积分事实来源。
 */
@Service
public class BoardService {

    private static final Logger log = LoggerFactory.getLogger(BoardService.class);
    private static final int MIN_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final BoardMapper boardMapper;
    private final ProfileSyncService profileSyncService;
    private final StringRedisTemplate redis;
    private final boolean redisEnabled;
    private final String prefix;

    public BoardService(BoardMapper boardMapper,
                        ProfileSyncService profileSyncService,
                        StringRedisTemplate redis,
                        @Value("${app.redis.enabled:true}") boolean redisEnabled,
                        @Value("${app.redis.key-prefix:bc:dev:}") String prefix) {
        this.boardMapper = boardMapper;
        this.profileSyncService = profileSyncService;
        this.redis = redis;
        this.redisEnabled = redisEnabled;
        this.prefix = prefix;
    }

    public BoardResponse monthly(String month, int page, int pageSize, long viewerId) {
        YearMonth yearMonth = parseMonth(month);
        String normalized = yearMonth.toString();
        int safePage = Math.max(1, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(MIN_PAGE_SIZE, pageSize));

        List<Long> participantIds = boardMapper.selectParticipantIds(normalized);
        profileSyncService.sync(participantIds);

        CachedBoard cached = readCache(normalized, safePage, safeSize);
        List<BoardItemView> items;
        long total;
        if (cached != null) {
            items = cached.items();
            total = cached.total();
        } else {
            int offset = (safePage - 1) * safeSize;
            items = boardMapper.selectPage(normalized, safeSize, offset).stream()
                    .map(row -> new BoardItemView(row.getNickname(), row.getScore(), row.getRankNo().intValue()))
                    .toList();
            total = boardMapper.count(normalized);
        }

        long myScore = boardMapper.myScore(normalized, viewerId);
        Integer myRank = myScore > 0
                ? (int) boardMapper.myRank(normalized, myScore, viewerId)
                : null;
        return new BoardResponse(normalized, items, total, safePage, safeSize, myRank);
    }

    public boolean rebuild(String month) {
        String normalized = parseMonth(month).toString();
        profileSyncService.sync(boardMapper.selectParticipantIds(normalized));
        return rebuildCache(normalized);
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(java.time.ZoneId.of("Asia/Shanghai"));
        }
        try {
            return YearMonth.parse(month);
        } catch (RuntimeException e) {
            throw new com.jobbootcamp.common.error.BusinessException(
                    com.jobbootcamp.common.error.ErrorCode.VALIDATION_FAILED, "月份格式应为 YYYY-MM");
        }
    }

    private CachedBoard readCache(String month, int page, int pageSize) {
        if (!redisEnabled) {
            return null;
        }
        String rankKey = rankKey(month);
        try {
            if (!Boolean.TRUE.equals(redis.hasKey(rankKey))) {
                if (!rebuildCache(month) || !Boolean.TRUE.equals(redis.hasKey(rankKey))) {
                    return null;
                }
            }
            long dbRevision = boardMapper.cacheRevision(month);
            String expectedRevision = dbRevision + ":" + boardMapper.profileRevision();
            String cachedRevision = redis.opsForValue().get(revisionKey(month));
            if (cachedRevision == null || !cachedRevision.equals(expectedRevision)) {
                if (!rebuildCache(month) || !Boolean.TRUE.equals(redis.hasKey(rankKey))) {
                    return null;
                }
            }
            Long total = redis.opsForZSet().zCard(rankKey);
            if (total == null || total == 0) {
                return null;
            }
            long start = (long) (page - 1) * pageSize;
            long end = start + pageSize - 1;
            Set<ZSetOperations.TypedTuple<String>> tuples = redis.opsForZSet().rangeWithScores(rankKey, start, end);
            if (tuples == null || tuples.isEmpty()) {
                return new CachedBoard(List.of(), total);
            }
            List<BoardItemView> items = new ArrayList<>();
            int index = 0;
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                String member = tuple.getValue();
                long userId = member == null ? 0 : Long.parseLong(member);
                Object nickname = redis.opsForHash().get(namesKey(month), member);
                long score = tuple.getScore() == null ? 0 : Math.round(-tuple.getScore());
                items.add(new BoardItemView(
                        nickname == null ? "学员" + userId : nickname.toString(),
                        score, (int) start + index + 1));
                index++;
            }
            return new CachedBoard(items, total);
        } catch (RuntimeException e) {
            log.warn("board cache read failed, falling back to MySQL: {}", e.getMessage());
            return null;
        }
    }

    private boolean rebuildCache(String month) {
        if (!redisEnabled) {
            return false;
        }
        List<BoardRow> rows = boardMapper.selectAll(month);
        String rankKey = rankKey(month);
        String namesKey = namesKey(month);
        String revisionKey = revisionKey(month);
        try {
            redis.delete(List.of(rankKey, namesKey, revisionKey));
            if (rows.isEmpty()) {
                return true;
            }
            Map<String, Double> scores = new LinkedHashMap<>();
            Map<String, String> names = new LinkedHashMap<>();
            for (BoardRow row : rows) {
                String member = String.format("%020d", row.getUserId());
                scores.put(member, -row.getScore().doubleValue());
                names.put(member, row.getNickname());
            }
            ZSetOperations<String, String> zset = redis.opsForZSet();
            scores.forEach((member, score) -> zset.add(rankKey, member, score));
            redis.opsForHash().putAll(namesKey, names);
            redis.opsForValue().set(revisionKey,
                    boardMapper.cacheRevision(month) + ":" + boardMapper.profileRevision());
            return true;
        } catch (RuntimeException e) {
            log.warn("board cache rebuild failed: {}", e.getMessage());
            return false;
        }
    }

    private String rankKey(String month) {
        return prefix + "board:" + month + ":rank";
    }

    private String namesKey(String month) {
        return prefix + "board:" + month + ":names";
    }

    private String revisionKey(String month) {
        return prefix + "board:" + month + ":revision";
    }

    private record CachedBoard(List<BoardItemView> items, long total) {
    }
}