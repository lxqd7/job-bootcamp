package com.jobbootcamp.marketing.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.TraceContext;
import com.jobbootcamp.marketing.domain.OutboxEvent;
import com.jobbootcamp.marketing.domain.PointsAccount;
import com.jobbootcamp.marketing.domain.PointsDayQuota;
import com.jobbootcamp.marketing.domain.PointsLedger;
import com.jobbootcamp.marketing.domain.SignRecord;
import com.jobbootcamp.marketing.domain.dto.PageResponse;
import com.jobbootcamp.marketing.domain.dto.PointsAwardResult;
import com.jobbootcamp.marketing.domain.dto.PointsLedgerView;
import com.jobbootcamp.marketing.domain.dto.PointsSummary;
import com.jobbootcamp.marketing.domain.dto.SignResult;
import com.jobbootcamp.marketing.domain.dto.SignStatus;
import com.jobbootcamp.marketing.domain.dto.TestAccountState;
import com.jobbootcamp.marketing.infrastructure.OutboxEventMapper;
import com.jobbootcamp.marketing.infrastructure.PointsAccountMapper;
import com.jobbootcamp.marketing.infrastructure.PointsDayQuotaMapper;
import com.jobbootcamp.marketing.infrastructure.PointsLedgerMapper;
import com.jobbootcamp.marketing.infrastructure.PointsMonthMapper;
import com.jobbootcamp.marketing.infrastructure.SignRecordMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class PointsService {

    public static final String TYPE_VIDEO = "VIDEO";
    public static final String TYPE_SIGN = "SIGN";
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final String PROJECTION_EVENT = "marketing.projection-changed.v1";

    private final PointsAccountMapper accountMapper;
    private final PointsLedgerMapper ledgerMapper;
    private final PointsDayQuotaMapper dayQuotaMapper;
    private final PointsMonthMapper monthMapper;
    private final OutboxEventMapper outboxEventMapper;
    private final SignRecordMapper signMapper;
    private final SignCacheService signCacheService;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate rowTemplate;
    private final int dailyVideoCap;

    public PointsService(PointsAccountMapper accountMapper,
                         PointsLedgerMapper ledgerMapper,
                         PointsDayQuotaMapper dayQuotaMapper,
                         PointsMonthMapper monthMapper,
                         OutboxEventMapper outboxEventMapper,
                         SignRecordMapper signMapper,
                         SignCacheService signCacheService,
                         PlatformTransactionManager transactionManager,
                         @Value("${app.points.daily-video-cap:50}") int dailyVideoCap) {
        this.accountMapper = accountMapper;
        this.ledgerMapper = ledgerMapper;
        this.dayQuotaMapper = dayQuotaMapper;
        this.monthMapper = monthMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.signMapper = signMapper;
        this.signCacheService = signCacheService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.rowTemplate = new TransactionTemplate(transactionManager);
        this.rowTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.dailyVideoCap = dailyVideoCap;
    }

    public PointsAwardResult awardVideo(long userId, long lessonId, int points, Instant occurredAt) {
        if (userId <= 0 || lessonId <= 0 || points <= 0 || points > dailyVideoCap) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "积分奖励参数不合法");
        }
        String businessKey = userId + ":" + lessonId;
        PointsLedger existing = findLedger(TYPE_VIDEO, businessKey);
        if (existing != null) {
            return toResult(existing, true);
        }
        LocalDate businessDate = occurredAt.atZone(SHANGHAI).toLocalDate();
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        // 账户/日额度行先用独立事务建好，避免并发首次创建与加锁交叉死锁。
        rowTemplate.executeWithoutResult(status -> {
            accountMapper.insertIfAbsent(userId, nowUtc);
            dayQuotaMapper.insertIfAbsent(userId, businessDate, nowUtc);
        });
        try {
            return transactionTemplate.execute(status ->
                    awardVideoInTransaction(userId, points, occurredAt, businessKey));
        } catch (LedgerDuplicateException e) {
            PointsLedger duplicate = findLedger(TYPE_VIDEO, businessKey);
            if (duplicate == null) {
                throw e;
            }
            return toResult(duplicate, true);
        }
    }

    private PointsAwardResult awardVideoInTransaction(long userId, int points,
                                                      Instant occurredAt, String businessKey) {
        PointsLedger existing = findLedger(TYPE_VIDEO, businessKey);
        if (existing != null) {
            return toResult(existing, true);
        }
        LocalDate businessDate = occurredAt.atZone(SHANGHAI).toLocalDate();
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);

        PointsAccount account = accountMapper.selectForUpdate(userId);
        PointsLedger raced = findLedger(TYPE_VIDEO, businessKey);
        if (raced != null) {
            return toResult(raced, true);
        }
        PointsDayQuota quota = dayQuotaMapper.selectForUpdate(userId, businessDate);
        int awarded = PointsMath.dailyAward(points, quota.getAwardedPoints(), dailyVideoCap);
        String resultCode = null;
        if (awarded == 0) {
            resultCode = "SKIPPED_DAILY_CAP";
        } else {
            dayQuotaMapper.addAwarded(userId, businessDate, awarded, nowUtc);
        }
        return applyReward(account, userId, TYPE_VIDEO, businessKey, awarded,
                businessDate, occurredAt, resultCode, nowUtc);
    }

    public SignResult signIn(long userId) {
        LocalDate today = LocalDate.now(SHANGHAI);
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        rowTemplate.executeWithoutResult(status -> accountMapper.insertIfAbsent(userId, nowUtc));
        SignResult result;
        try {
            result = transactionTemplate.execute(status -> signInTransaction(userId, today, nowUtc));
        } catch (SignDuplicateException e) {
            result = currentSignResult(userId, today);
        }
        if (result != null && !result.alreadySigned()) {
            signCacheService.mark(userId, today);
        }
        return result;
    }

    private SignResult signInTransaction(long userId, LocalDate today, LocalDateTime nowUtc) {
        PointsAccount account = accountMapper.selectForUpdate(userId);
        SignRecord existing = signMapper.select(userId, today);
        if (existing != null) {
            return toSignResult(existing, true, account);
        }
        LocalDate yesterday = today.minusDays(1);
        SignRecord previous = YearMonth.from(yesterday).equals(YearMonth.from(today))
                ? signMapper.select(userId, yesterday)
                : null;
        int continuousDays = previous == null ? 1 : previous.getContinuousDays() + 1;
        int bonus = PointsMath.signBonus(continuousDays);
        int base = 1;
        int total = base + bonus;

        SignRecord record = new SignRecord();
        record.setUserId(userId);
        record.setBusinessDate(today);
        record.setContinuousDays(continuousDays);
        record.setBasePoints(base);
        record.setBonusPoints(bonus);
        record.setOccurredAt(nowUtc);
        record.setCreatedAt(nowUtc);
        try {
            signMapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new SignDuplicateException();
        }

        PointsAwardResult award = applyReward(account, userId, TYPE_SIGN,
                userId + ":" + today, total, today, nowUtc.toInstant(ZoneOffset.UTC), null, nowUtc);
        return new SignResult(today, base, bonus, total, continuousDays, false,
                award.available(), award.debt(), award.monthScore());
    }

    public SignStatus signStatus(long userId, String requestedMonth) {
        YearMonth month;
        try {
            month = requestedMonth == null || requestedMonth.isBlank()
                    ? YearMonth.now(SHANGHAI)
                    : YearMonth.parse(requestedMonth);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "月份格式应为 YYYY-MM");
        }
        signCacheService.ensure(userId, month);
        LocalDate today = LocalDate.now(SHANGHAI);
        SignRecord record = signMapper.select(userId, today);
        List<SignRecord> monthRecords = signMapper.selectBetween(userId, month.atDay(1), month.atEndOfMonth());
        List<Integer> days = monthRecords.stream()
                .map(item -> item.getBusinessDate().getDayOfMonth())
                .toList();
        boolean currentMonth = YearMonth.from(today).equals(month);
        PointsAccount account = accountMapper.selectById(userId);
        Long score = monthMapper.selectScore(month.toString(), userId);
        SignRecord todayRecord = currentMonth ? record : null;
        return new SignStatus(month.toString(), today,
                todayRecord != null,
                todayRecord == null ? 0 : todayRecord.getContinuousDays(),
                todayRecord == null ? 0 : todayRecord.getBasePoints() + todayRecord.getBonusPoints(),
                monthRecords.size(), days,
                account == null ? 0 : account.getAvailable(),
                account == null ? 0 : account.getDebt(),
                score == null ? 0 : score);
    }

    private PointsAwardResult applyReward(PointsAccount account, long userId, String type,
                                           String businessKey, int awarded, LocalDate businessDate,
                                           Instant occurredAt, String resultCode, LocalDateTime nowUtc) {
        String businessMonth = YearMonth.from(businessDate).toString();
        LocalDateTime occurredUtc = LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC);
        long availableBefore = account.getAvailable();
        long debtBefore = account.getDebt();
        long available = availableBefore;
        long debt = debtBefore;
        if (awarded > 0) {
            PointsMath.DebtOffset offset = PointsMath.offsetDebt(debtBefore, awarded);
            available = availableBefore + offset.balanceAdded();
            debt = debtBefore - offset.debtReduced();
            accountMapper.updateBalances(userId, available, debt, nowUtc);
            monthMapper.addScore(businessMonth, userId, awarded);
        }

        PointsLedger ledger = new PointsLedger();
        ledger.setUserId(userId);
        ledger.setType(type);
        ledger.setBusinessKey(businessKey);
        ledger.setRewardPoints((long) awarded);
        ledger.setBalanceDelta(available - availableBefore);
        ledger.setDebtDelta(debt - debtBefore);
        ledger.setRankDelta((long) awarded);
        ledger.setBalanceAfter(available);
        ledger.setDebtAfter(debt);
        ledger.setBusinessDate(businessDate);
        ledger.setBusinessMonth(businessMonth);
        ledger.setOccurredAt(occurredUtc);
        ledger.setResultCode(resultCode);
        ledger.setCreatedAt(nowUtc);
        try {
            ledgerMapper.insert(ledger);
        } catch (DuplicateKeyException e) {
            throw new LedgerDuplicateException();
        }

        if (awarded > 0) {
            writeProjectionEvent(userId, businessMonth, ledger.getId(), nowUtc);
        }
        long monthScore = monthMapper.selectScore(businessMonth, userId);
        return new PointsAwardResult(ledger.getId(), false, awarded, available, debt,
                monthScore, businessDate, businessMonth, resultCode);
    }

    private void writeProjectionEvent(long userId, String month, long ledgerId, LocalDateTime nowUtc) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setType(PROJECTION_EVENT);
        event.setAggregateId(Long.toString(userId));
        event.setAggregateVersion(ledgerId);
        event.setOccurredAt(nowUtc);
        event.setTraceId(TraceContext.currentTraceId());
        event.setPayload("{\"userId\":\"" + userId
                + "\",\"month\":\"" + month
                + "\",\"revision\":\"" + ledgerId
                + "\",\"entityType\":\"POINTS\",\"entityId\":\"" + ledgerId + "\"}");
        event.setStatus("PENDING");
        event.setAttempt(0);
        event.setNextRetryAt(nowUtc);
        event.setCreatedAt(nowUtc);
        event.setUpdatedAt(nowUtc);
        outboxEventMapper.insert(event);
    }

    public PointsSummary summary(long userId) {
        PointsAccount account = accountMapper.selectById(userId);
        String month = YearMonth.now(SHANGHAI).toString();
        Long score = monthMapper.selectScore(month, userId);
        return new PointsSummary(
                account == null ? 0 : account.getAvailable(),
                account == null ? 0 : account.getDebt(),
                score == null ? 0 : score);
    }

    public PointsAwardResult findVideoResult(long userId, long lessonId) {
        PointsLedger ledger = findLedger(TYPE_VIDEO, userId + ":" + lessonId);
        return ledger == null ? null : toResult(ledger, true);
    }

    public TestAccountState testState(long userId) {
        PointsSummary summary = summary(userId);
        long ledgerCount = ledgerMapper.selectCount(Wrappers.<PointsLedger>lambdaQuery()
                .eq(PointsLedger::getUserId, userId));
        List<OutboxEvent> events = outboxEventMapper.selectList(Wrappers.<OutboxEvent>lambdaQuery()
                .eq(OutboxEvent::getAggregateId, Long.toString(userId)));
        long pending = events.stream().filter(event -> "PENDING".equals(event.getStatus())).count();
        long maxAttempt = events.stream().mapToLong(event -> event.getAttempt() == null ? 0 : event.getAttempt()).max().orElse(0);
        String errorCode = events.stream()
                .map(OutboxEvent::getErrorCode)
                .filter(code -> code != null)
                .findFirst()
                .orElse(null);
        return new TestAccountState(summary.available(), summary.debt(), summary.currentMonthScore(),
                ledgerCount, pending, maxAttempt, errorCode);
    }

    public void setDebtForTest(long userId, long debt) {
        transactionTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            accountMapper.insertIfAbsent(userId, now);
            PointsAccount account = accountMapper.selectForUpdate(userId);
            accountMapper.updateBalances(userId, account.getAvailable(), debt, now);
        });
    }

    public void seedSignDaysForTest(long userId, int days, LocalDate endDate) {
        if (days < 1 || days > 60) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "测试签到天数不合法");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (int i = 0; i < days; i++) {
            LocalDate date = endDate.minusDays(days - 1L - i);
            SignRecord record = new SignRecord();
            record.setUserId(userId);
            record.setBusinessDate(date);
            record.setContinuousDays(i + 1);
            record.setBasePoints(1);
            record.setBonusPoints(0);
            record.setOccurredAt(now);
            record.setCreatedAt(now);
            try {
                signMapper.insert(record);
            } catch (DuplicateKeyException ignored) {
                // 测试夹具重复执行时保留已有记录
            }
        }
    }

    public void setMonthScoreForTest(long userId, String month, long score) {
        YearMonth.parse(month);
        monthMapper.setScore(month, userId, Math.max(0, score));
    }

    public void rebuildSignCacheForTest(long userId, String month) {
        signCacheService.rebuild(userId, YearMonth.parse(month));
    }

    public PageResponse<PointsLedgerView> ledger(long userId, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        Page<PointsLedger> query = new Page<>(safePage, safeSize);
        Page<PointsLedger> result = ledgerMapper.selectPage(query, Wrappers.<PointsLedger>lambdaQuery()
                .eq(PointsLedger::getUserId, userId)
                .orderByDesc(PointsLedger::getId));
        List<PointsLedgerView> items = result.getRecords().stream()
                .map(ledger -> new PointsLedgerView(
                        ledger.getId(), ledger.getType(), ledger.getRewardPoints(),
                        ledger.getBalanceDelta(), ledger.getDebtDelta(), ledger.getRankDelta(),
                        ledger.getBalanceAfter(), ledger.getDebtAfter(), ledger.getBusinessDate(),
                        ledger.getBusinessMonth(), ledger.getOccurredAt(), ledger.getResultCode()))
                .toList();
        return new PageResponse<>(items, result.getTotal(), result.getCurrent(), result.getSize());
    }

    private SignResult currentSignResult(long userId, LocalDate date) {
        PointsAccount account = accountMapper.selectById(userId);
        SignRecord record = signMapper.select(userId, date);
        if (record == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "签到记录已变化，请刷新后重试");
        }
        return toSignResult(record, true, account);
    }

    private SignResult toSignResult(SignRecord record, boolean duplicate, PointsAccount account) {
        Long score = monthMapper.selectScore(YearMonth.from(record.getBusinessDate()).toString(),
                record.getUserId());
        return new SignResult(record.getBusinessDate(), record.getBasePoints(), record.getBonusPoints(),
                record.getBasePoints() + record.getBonusPoints(), record.getContinuousDays(), duplicate,
                account == null ? 0 : account.getAvailable(),
                account == null ? 0 : account.getDebt(),
                score == null ? 0 : score);
    }

    private PointsLedger findLedger(String type, String businessKey) {
        return ledgerMapper.selectOne(Wrappers.<PointsLedger>lambdaQuery()
                .eq(PointsLedger::getType, type)
                .eq(PointsLedger::getBusinessKey, businessKey));
    }

    private PointsAwardResult toResult(PointsLedger ledger, boolean duplicate) {
        Long score = monthMapper.selectScore(ledger.getBusinessMonth(), ledger.getUserId());
        return new PointsAwardResult(ledger.getId(), duplicate, ledger.getRewardPoints(),
                ledger.getBalanceAfter(), ledger.getDebtAfter(), score == null ? 0 : score,
                ledger.getBusinessDate(), ledger.getBusinessMonth(), ledger.getResultCode());
    }

    private static final class LedgerDuplicateException extends RuntimeException {
    }

    private static final class SignDuplicateException extends RuntimeException {
    }
}