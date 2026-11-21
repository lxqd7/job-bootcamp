package com.jobbootcamp.marketing.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.CouponLockLine;
import com.jobbootcamp.contracts.internal.CouponLockPrepareRequest;
import com.jobbootcamp.contracts.internal.CouponLockResponse;
import com.jobbootcamp.contracts.internal.CouponLockStateRequest;
import com.jobbootcamp.contracts.internal.PricingCalculateResponse;
import com.jobbootcamp.contracts.internal.PricingCouponAllocation;
import com.jobbootcamp.contracts.internal.PricingCouponPlan;
import com.jobbootcamp.marketing.domain.CouponLock;
import com.jobbootcamp.marketing.domain.PricingJob;
import com.jobbootcamp.marketing.domain.UserCoupon;
import com.jobbootcamp.marketing.infrastructure.CouponLockMapper;
import com.jobbootcamp.marketing.infrastructure.PricingJobMapper;
import com.jobbootcamp.marketing.infrastructure.UserCouponMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 订单券锁的记录服务。prepare 按券 ID 排序锁行并一次全成或全失败；
 * USE/RELEASE 只接受本订单的 LOCKED 记录，状态更新和用户券状态在同一事务内完成。
 */
@Service
public class CouponLockService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final CouponLockMapper lockMapper;
    private final UserCouponMapper userCouponMapper;
    private final PricingJobMapper jobMapper;

    public CouponLockService(CouponLockMapper lockMapper,
                             UserCouponMapper userCouponMapper,
                             PricingJobMapper jobMapper) {
        this.lockMapper = lockMapper;
        this.userCouponMapper = userCouponMapper;
        this.jobMapper = jobMapper;
    }

    @Transactional
    public CouponLockResponse prepare(CouponLockPrepareRequest request) {
        validatePrepare(request);
        List<CouponLock> existing = locks(request.orderId(), true);
        if (!existing.isEmpty()) {
            return existingResponse(existing, request);
        }

        PricingJob job = jobMapper.selectById(request.quoteId());
        if (job == null || !request.userId().equals(job.getUserId())
                || !"READY".equals(job.getStatus())
                || !Objects.equals(job.getResultHash(), request.pricingHash())
                || job.getResultJson() == null) {
            throw new BusinessException(ErrorCode.QUOTE_CHANGED, "定价快照已变化，请重新确认");
        }
        List<CouponLockLine> lines = plan(job);
        List<Long> expectedIds = lines.stream().map(CouponLockLine::userCouponId).sorted().toList();
        List<Long> actualIds = request.couponIds().stream().distinct().sorted().toList();
        if (!expectedIds.equals(actualIds)) {
            throw new BusinessException(ErrorCode.COUPON_COMBINATION_INVALID, "下单优惠券与报价方案不一致");
        }
        validateSameSource(lines);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<UserCoupon> coupons = userCouponMapper.selectList(Wrappers.<UserCoupon>lambdaQuery()
                .in(UserCoupon::getId, actualIds)
                .eq(UserCoupon::getUserId, request.userId())
                .orderByAsc(UserCoupon::getId)
                .last("FOR UPDATE"));
        if (coupons.size() != actualIds.size()) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "所选优惠券不属于当前用户");
        }
        for (UserCoupon coupon : coupons) {
            if (!"AVAILABLE".equals(coupon.getStatus())
                    || coupon.getValidFrom() == null || coupon.getValidUntil() == null
                    || coupon.getValidFrom().isAfter(now) || !coupon.getValidUntil().isAfter(now)) {
                throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "所选优惠券不可用或已过期");
            }
        }

        String planJson = writeJson(lines);
        for (UserCoupon coupon : coupons) {
            int updated = userCouponMapper.update(null, Wrappers.<UserCoupon>lambdaUpdate()
                    .eq(UserCoupon::getId, coupon.getId())
                    .eq(UserCoupon::getStatus, "AVAILABLE")
                    .set(UserCoupon::getStatus, "LOCKED")
                    .set(UserCoupon::getLockedOrderId, request.orderId())
                    .set(UserCoupon::getUpdatedAt, now)
                    .setSql("revision = revision + 1"));
            if (updated != 1) {
                throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "优惠券已被其他订单占用");
            }
            CouponLock lock = new CouponLock();
            lock.setOrderId(request.orderId());
            lock.setUserId(request.userId());
            lock.setUserCouponId(coupon.getId());
            lock.setQuoteId(request.quoteId());
            lock.setPricingHash(request.pricingHash());
            lock.setPlanJson(planJson);
            lock.setState("LOCKED");
            lock.setCreatedAt(now);
            lock.setUpdatedAt(now);
            lock.setRevision(0L);
            lockMapper.insert(lock);
        }
        return new CouponLockResponse(request.orderId(), "LOCKED", request.pricingHash(), lines);
    }

    @Transactional
    public CouponLockResponse use(CouponLockStateRequest request) {
        if (!"PAID".equals(request.confirmedOrderState())) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "只有已支付订单可以核销优惠券");
        }
        List<CouponLock> locks = locks(request.orderId(), true);
        if (locks.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单没有锁券记录");
        }
        verifyLocks(locks, request);
        String state = state(locks);
        if ("USED".equals(state)) {
            return response(locks);
        }
        if (!"LOCKED".equals(state)) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "已释放的优惠券不能核销");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (CouponLock lock : locks) {
            int updated = userCouponMapper.update(null, Wrappers.<UserCoupon>lambdaUpdate()
                    .eq(UserCoupon::getId, lock.getUserCouponId())
                    .eq(UserCoupon::getStatus, "LOCKED")
                    .eq(UserCoupon::getLockedOrderId, request.orderId())
                    .set(UserCoupon::getStatus, "USED")
                    .set(UserCoupon::getUsedOrderId, request.orderId())
                    .set(UserCoupon::getLockedOrderId, null)
                    .set(UserCoupon::getUpdatedAt, now)
                    .setSql("revision = revision + 1"));
            if (updated != 1) {
                throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "优惠券状态已变化");
            }
            lock.setState("USED");
            lock.setUpdatedAt(now);
            lock.setRevision(lock.getRevision() == null ? 1 : lock.getRevision() + 1);
            lockMapper.updateById(lock);
        }
        return new CouponLockResponse(request.orderId(), "USED", request.pricingHash(), plan(locks));
    }

    @Transactional
    public CouponLockResponse release(CouponLockStateRequest request) {
        if ("PAID".equals(request.confirmedOrderState())) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "已支付订单不能释放优惠券");
        }
        List<CouponLock> locks = locks(request.orderId(), true);
        if (locks.isEmpty()) {
            return new CouponLockResponse(request.orderId(), "NONE", request.pricingHash(), List.of());
        }
        verifyLocks(locks, request);
        String state = state(locks);
        if ("RELEASED".equals(state)) {
            return response(locks);
        }
        if (!"LOCKED".equals(state)) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "已核销的优惠券不能释放");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (CouponLock lock : locks) {
            // 释放只改状态，不延长或重算原有效期；过期券之后仍不能再次锁定。
            int updated = userCouponMapper.update(null, Wrappers.<UserCoupon>lambdaUpdate()
                    .eq(UserCoupon::getId, lock.getUserCouponId())
                    .eq(UserCoupon::getStatus, "LOCKED")
                    .eq(UserCoupon::getLockedOrderId, request.orderId())
                    .set(UserCoupon::getStatus, "AVAILABLE")
                    .set(UserCoupon::getLockedOrderId, null)
                    .set(UserCoupon::getUpdatedAt, now)
                    .setSql("revision = revision + 1"));
            if (updated != 1) {
                throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "优惠券状态已变化");
            }
            lock.setState("RELEASED");
            lock.setUpdatedAt(now);
            lock.setRevision(lock.getRevision() == null ? 1 : lock.getRevision() + 1);
            lockMapper.updateById(lock);
        }
        return new CouponLockResponse(request.orderId(), "RELEASED", request.pricingHash(), plan(locks));
    }

    public List<CouponLock> locks(Long orderId, boolean forUpdate) {
        return lockMapper.selectList(Wrappers.<CouponLock>lambdaQuery()
                .eq(CouponLock::getOrderId, orderId)
                .orderByAsc(CouponLock::getId)
                .last(forUpdate ? "FOR UPDATE" : ""));
    }

    private CouponLockResponse existingResponse(List<CouponLock> locks, CouponLockPrepareRequest request) {
        verifyLocks(locks, new CouponLockStateRequest(request.orderId(), request.userId(),
                request.pricingHash(), "LOCKED"));
        List<Long> existingIds = locks.stream().map(CouponLock::getUserCouponId).sorted().toList();
        List<Long> requestIds = request.couponIds().stream().distinct().sorted().toList();
        if (!existingIds.equals(requestIds)
                || locks.stream().anyMatch(lock -> !Objects.equals(lock.getQuoteId(), request.quoteId()))) {
            throw new BusinessException(ErrorCode.QUOTE_CHANGED, "同一订单不能更换报价或优惠券组合");
        }
        return response(locks);
    }

    private void verifyLocks(List<CouponLock> locks, CouponLockStateRequest request) {
        for (CouponLock lock : locks) {
            if (!request.userId().equals(lock.getUserId())
                    || !Objects.equals(request.pricingHash(), lock.getPricingHash())) {
                throw new BusinessException(ErrorCode.QUOTE_CHANGED, "锁券快照与订单不一致");
            }
        }
    }

    private String state(List<CouponLock> locks) {
        String first = locks.get(0).getState();
        for (CouponLock lock : locks) {
            if (!first.equals(lock.getState())) {
                throw new BusinessException(ErrorCode.DATA_INTEGRITY, "订单券状态不一致");
            }
        }
        return first;
    }

    private CouponLockResponse response(List<CouponLock> locks) {
        return new CouponLockResponse(locks.get(0).getOrderId(), state(locks),
                locks.get(0).getPricingHash(), plan(locks));
    }

    private List<CouponLockLine> plan(List<CouponLock> locks) {
        try {
            return List.of(JSON.readValue(locks.get(0).getPlanJson(), CouponLockLine[].class));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "锁券计划快照损坏");
        }
    }

    private List<CouponLockLine> plan(PricingJob job) {
        try {
            PricingCalculateResponse result = JSON.readValue(job.getResultJson(), PricingCalculateResponse.class);
            List<CouponLockLine> lines = new ArrayList<>();
            for (PricingCouponPlan plan : result.couponPlan()) {
                List<PricingCouponAllocation> allocation = plan.allocation() == null
                        ? List.of() : plan.allocation();
                lines.add(new CouponLockLine(plan.userCouponId(), plan.applyOrder(), plan.baseCent(),
                        plan.discountCent(), allocation));
            }
            return lines;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "定价计划快照损坏");
        }
    }

    private void validateSameSource(List<CouponLockLine> lines) {
        // 定价阶段已做同来源约束，这里再次校验，防止锁券接口被单独调用时绕过。
        Map<Long, CouponLockLine> byId = new LinkedHashMap<>();
        for (CouponLockLine line : lines) {
            byId.put(line.userCouponId(), line);
        }
        List<UserCoupon> coupons = userCouponMapper.selectList(Wrappers.<UserCoupon>lambdaQuery()
                .in(UserCoupon::getId, byId.keySet()));
        HashSet<String> groups = new HashSet<>();
        for (UserCoupon coupon : coupons) {
            String key = coupon.getSourceType() + "#" + coupon.getSourceGroupId();
            if (!groups.add(key)) {
                throw new BusinessException(ErrorCode.COUPON_COMBINATION_INVALID, "同一来源不能使用多张券");
            }
        }
    }

    private void validatePrepare(CouponLockPrepareRequest request) {
        if (request == null || request.orderId() == null || request.userId() == null
                || request.quoteId() == null || request.quoteId().isBlank()
                || request.pricingHash() == null || request.pricingHash().length() != 64
                || request.couponIds() == null || request.couponIds().isEmpty()
                || request.couponIds().size() > 3
                || new HashSet<>(request.couponIds()).size() != request.couponIds().size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "锁券请求参数不合法");
        }
    }

    private String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "锁券计划序列化失败");
        }
    }
}