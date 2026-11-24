package com.jobbootcamp.marketing.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.marketing.domain.CouponRule;
import com.jobbootcamp.marketing.domain.ExchangeProject;
import com.jobbootcamp.marketing.domain.ExchangeRecord;
import com.jobbootcamp.marketing.domain.UserCoupon;
import com.jobbootcamp.marketing.domain.dto.CouponRuleView;
import com.jobbootcamp.marketing.domain.dto.ExchangeProjectStudentView;
import com.jobbootcamp.marketing.domain.dto.ExchangeRecordView;
import com.jobbootcamp.marketing.domain.dto.ExchangeResult;
import com.jobbootcamp.marketing.domain.dto.PageResponse;
import com.jobbootcamp.marketing.domain.dto.PointsSpendResult;
import com.jobbootcamp.marketing.infrastructure.AcquisitionCounterMapper;
import com.jobbootcamp.marketing.infrastructure.AcquisitionMapper;
import com.jobbootcamp.marketing.infrastructure.CouponRuleMapper;
import com.jobbootcamp.marketing.infrastructure.ExchangeProjectMapper;
import com.jobbootcamp.marketing.infrastructure.ExchangeRecordMapper;
import com.jobbootcamp.marketing.infrastructure.UserCouponMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 常驻积分兑换：项目行锁串行同一项目的库存/限兑判断，账户锁串行扣分；
 * 扣分、库存、累计计数、发券和兑换记录在同一本地事务内，任一失败全部回滚。
 */
@Service
public class ExchangeService {

    private static final String EXCHANGE = "EXCHANGE";
    private static final String ONLINE = "ONLINE";
    private static final String AVAILABLE = "AVAILABLE";

    private final ExchangeProjectMapper projectMapper;
    private final CouponRuleMapper ruleMapper;
    private final UserCouponMapper userCouponMapper;
    private final AcquisitionMapper acquisitionMapper;
    private final AcquisitionCounterMapper counterMapper;
    private final ExchangeRecordMapper recordMapper;
    private final PointsService pointsService;
    private final TransactionTemplate transactionTemplate;

    public ExchangeService(ExchangeProjectMapper projectMapper,
                           CouponRuleMapper ruleMapper,
                           UserCouponMapper userCouponMapper,
                           AcquisitionMapper acquisitionMapper,
                           AcquisitionCounterMapper counterMapper,
                           ExchangeRecordMapper recordMapper,
                           PointsService pointsService,
                           PlatformTransactionManager transactionManager) {
        this.projectMapper = projectMapper;
        this.ruleMapper = ruleMapper;
        this.userCouponMapper = userCouponMapper;
        this.acquisitionMapper = acquisitionMapper;
        this.counterMapper = counterMapper;
        this.recordMapper = recordMapper;
        this.pointsService = pointsService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ExchangeResult exchange(Long userId, String role, Long projectId,
                                   Long expectedRevision, String requestKey) {
        requireStudent(role);
        String key = normalizeKey(requestKey);
        ExchangeRecord existing = recordMapper.findByUserAndRequestKey(userId, key);
        if (existing != null) {
            return existingResult(existing, projectId);
        }
        try {
            ExchangeResult result = transactionTemplate.execute(status ->
                    exchangeInTransaction(userId, projectId, expectedRevision, key));
            if (result == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "兑换未产生结果");
            }
            return result;
        } catch (DuplicateKeyException e) {
            ExchangeRecord raced = recordMapper.findByUserAndRequestKey(userId, key);
            if (raced == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "兑换请求正在处理，请稍后重试");
            }
            return existingResult(raced, projectId);
        }
    }

    public PageResponse<ExchangeProjectStudentView> listAvailable(Long userId, String role,
                                                                 int page, int pageSize) {
        requireStudent(role);
        IPage<ExchangeProject> result = projectMapper.selectPage(
                new Page<>(Math.max(1, page), Math.min(100, Math.max(1, pageSize))),
                Wrappers.<ExchangeProject>lambdaQuery()
                        .eq(ExchangeProject::getState, ONLINE)
                        .orderByDesc(ExchangeProject::getId));
        LocalDateTime now = now();
        return new PageResponse<>(result.getRecords().stream()
                .map(project -> studentView(userId, project, now)).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    public PageResponse<ExchangeRecordView> myExchanges(Long userId, String role, int page, int pageSize) {
        requireStudent(role);
        IPage<ExchangeRecord> result = recordMapper.selectPage(
                new Page<>(Math.max(1, page), Math.min(100, Math.max(1, pageSize))),
                Wrappers.<ExchangeRecord>lambdaQuery()
                        .eq(ExchangeRecord::getUserId, userId)
                        .orderByDesc(ExchangeRecord::getId));
        return new PageResponse<>(result.getRecords().stream().map(this::recordView).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    private ExchangeProjectStudentView studentView(Long userId, ExchangeProject project, LocalDateTime now) {
        CouponRule rule = ruleMapper.selectById(project.getRuleId());
        int remaining = Math.max(0, project.getStockTotal() - project.getStockUsed());
        int count = counterMapper.selectCount(userId, EXCHANGE, project.getId());
        boolean canExchange = now.isBefore(project.getCloseAt()) && !now.isBefore(project.getOpenAt())
                && remaining > 0 && count < project.getPerUserLimit();
        return new ExchangeProjectStudentView(project.getId(), project.getName(),
                rule == null ? null : toRuleView(rule), project.getPointsPrice(), remaining,
                project.getPerUserLimit(), count, canExchange, project.getOpenAt(),
                project.getCloseAt(), project.getRevision());
    }

    private ExchangeRecordView recordView(ExchangeRecord record) {
        ExchangeProject project = projectMapper.selectById(record.getProjectId());
        UserCoupon coupon = userCouponMapper.selectById(record.getUserCouponId());
        CouponRule rule = coupon == null ? null : ruleMapper.selectById(coupon.getRuleId());
        return new ExchangeRecordView(record.getId(), record.getProjectId(),
                project == null ? "兑换项目已删除" : project.getName(), record.getProjectRevision(),
                record.getPointsSpent(), record.getUserCouponId(),
                rule == null ? null : toRuleView(rule),
                coupon == null ? null : coupon.getStatus(),
                coupon == null ? null : coupon.getValidFrom(),
                coupon == null ? null : coupon.getValidUntil(),
                record.getBalanceAfter(), record.getCreatedAt());
    }

    private CouponRuleView toRuleView(CouponRule rule) {
        return new CouponRuleView(rule.getId(), rule.getRuleKey(), rule.getVersionNo(), rule.getType(),
                rule.getScope(), rule.getTargetId(), rule.getThresholdCent(), rule.getAmountCent(),
                rule.getPayRateBps(), rule.getMaxDiscountCent(), rule.getValidityType(),
                rule.getValidFrom(), rule.getValidUntil(), rule.getValidDays(),
                Boolean.TRUE.equals(rule.getIssued()), rule.getRevision());
    }

    private void requireStudent(String role) {
        if (!"STUDENT".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有学生可以兑换积分");
        }
    }

    private ExchangeResult exchangeInTransaction(Long userId, Long projectId,
                                                 Long expectedRevision, String key) {
        ExchangeProject project = projectMapper.selectForUpdate(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "兑换项目不存在");
        }
        ExchangeRecord existing = recordMapper.findByUserAndRequestKey(userId, key);
        if (existing != null) {
            return existingResult(existing, projectId);
        }
        if (expectedRevision == null || !expectedRevision.equals(project.getRevision())) {
            throw new BusinessException(ErrorCode.EXCHANGE_CHANGED, "兑换配置已变化，请重新确认");
        }
        LocalDateTime now = now();
        if (!ONLINE.equals(project.getState()) || now.isBefore(project.getOpenAt())
                || !now.isBefore(project.getCloseAt())) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "兑换项目当前不可兑换");
        }
        if (project.getStockUsed() >= project.getStockTotal()) {
            throw new BusinessException(ErrorCode.STOCK_EXHAUSTED, "兑换库存不足");
        }
        CouponRule rule = ruleMapper.selectForUpdate(project.getRuleId());
        if (rule == null) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "兑换券规则不可用");
        }
        LocalDateTime validFrom;
        LocalDateTime validUntil;
        if ("FIXED".equals(rule.getValidityType())) {
            validFrom = rule.getValidFrom() == null ? now : rule.getValidFrom();
            validUntil = rule.getValidUntil();
            if (validUntil == null || !validUntil.isAfter(now)) {
                throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "兑换券已过有效期");
            }
        } else if ("DAYS".equals(rule.getValidityType())) {
            validFrom = now;
            validUntil = now.plusDays(Math.max(1, rule.getValidDays() == null ? 1 : rule.getValidDays()));
        } else {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "兑换券有效期配置不合法");
        }

        PointsSpendResult spend = pointsService.spendForExchange(userId, key, project.getPointsPrice());

        counterMapper.insertIfAbsent(userId, EXCHANGE, projectId, now);
        Integer count = counterMapper.selectForUpdate(userId, EXCHANGE, projectId);
        if (count == null || count >= project.getPerUserLimit()) {
            throw new BusinessException(ErrorCode.ACQUISITION_LIMIT, "已达到该项目累计兑换上限");
        }

        project.setStockUsed(project.getStockUsed() + 1);
        project.setUpdatedAt(now);
        projectMapper.updateById(project);
        counterMapper.increment(userId, EXCHANGE, projectId, now);

        UserCoupon coupon = new UserCoupon();
        coupon.setUserId(userId);
        coupon.setRuleId(rule.getId());
        coupon.setSourceType(EXCHANGE);
        coupon.setSourceGroupId(projectId);
        coupon.setSourceItemId(projectId);
        coupon.setValidFrom(validFrom);
        coupon.setValidUntil(validUntil);
        coupon.setStatus(AVAILABLE);
        coupon.setCreatedAt(now);
        coupon.setUpdatedAt(now);
        coupon.setRevision(0L);
        userCouponMapper.insert(coupon);
        if (!Boolean.TRUE.equals(rule.getIssued())) {
            ruleMapper.markIssued(rule.getId(), now);
        }
        acquisitionMapper.insert(userId, EXCHANGE, projectId, coupon.getId(), key, now);

        ExchangeRecord record = new ExchangeRecord();
        record.setUserId(userId);
        record.setProjectId(projectId);
        record.setProjectRevision(project.getRevision());
        record.setRequestKey(key);
        record.setPointsSpent(project.getPointsPrice());
        record.setUserCouponId(coupon.getId());
        record.setLedgerId(spend.ledgerId());
        record.setBalanceAfter(spend.available());
        record.setCreatedAt(now);
        recordMapper.insert(record);
        return toResult(record);
    }

    private ExchangeResult existingResult(ExchangeRecord record, Long requestedProjectId) {
        if (!record.getProjectId().equals(requestedProjectId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "幂等键已用于其他兑换项目");
        }
        return toResult(record);
    }

    private ExchangeResult toResult(ExchangeRecord record) {
        return new ExchangeResult(record.getId(), record.getProjectId(), record.getProjectRevision(),
                record.getPointsSpent(), record.getUserCouponId(), record.getBalanceAfter(), record.getCreatedAt());
    }

    private String normalizeKey(String requestKey) {
        if (requestKey == null || requestKey.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String key = requestKey.trim();
        if (key.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key 不能超过 64 字符");
        }
        return key;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}