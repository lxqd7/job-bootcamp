package com.jobbootcamp.marketing.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.PricingCalculateRequest;
import com.jobbootcamp.contracts.internal.PricingCalculateResponse;
import com.jobbootcamp.contracts.internal.PricingCouponAllocation;
import com.jobbootcamp.contracts.internal.PricingCouponPlan;
import com.jobbootcamp.contracts.internal.PricingCourseAmount;
import com.jobbootcamp.marketing.domain.CouponRule;
import com.jobbootcamp.marketing.domain.PricingJob;
import com.jobbootcamp.marketing.domain.UserCoupon;
import com.jobbootcamp.marketing.infrastructure.CouponRuleMapper;
import com.jobbootcamp.marketing.infrastructure.PricingJobMapper;
import com.jobbootcamp.marketing.infrastructure.UserCouponMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 优惠券定价：以当前已抵扣后的课程余额为基数，枚举最多 3 张券的所有顺序。
 * 报价计算超过 500ms 不返回局部结果，而是落 pricing_job 由 trade 轮询；结果按 quoteId 幂等恢复。
 */
@Service
public class PricingService {

    private static final long DEFAULT_WAIT_MS = 500;
    private static final long LEASE_SECONDS = 30;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);

    private final UserCouponMapper userCouponMapper;
    private final CouponRuleMapper ruleMapper;
    private final PricingJobMapper jobMapper;
    private final long waitMs;

    @Autowired
    public PricingService(UserCouponMapper userCouponMapper,
                          CouponRuleMapper ruleMapper,
                          PricingJobMapper jobMapper) {
        this(userCouponMapper, ruleMapper, jobMapper, DEFAULT_WAIT_MS);
    }

    PricingService(UserCouponMapper userCouponMapper,
                   CouponRuleMapper ruleMapper,
                   PricingJobMapper jobMapper,
                   long waitMs) {
        this.userCouponMapper = userCouponMapper;
        this.ruleMapper = ruleMapper;
        this.jobMapper = jobMapper;
        this.waitMs = waitMs;
    }

    public PricingCalculateResponse calculate(PricingCalculateRequest request) {
        validateRequest(request);
        PricingJob existing = jobMapper.selectById(request.quoteId());
        if (existing != null) {
            claimRecovery(existing);
            return fromJob(jobMapper.selectById(request.quoteId()));
        }
        String mode = request.couponSelection() == null
                ? "NONE" : request.couponSelection().mode().toUpperCase(Locale.ROOT);
        if ("NONE".equals(mode)) {
            return emptyResponse(request);
        }

        LocalDateTime now = now();
        String token = UUID.randomUUID().toString();
        PricingJob job = new PricingJob();
        job.setQuoteId(request.quoteId());
        job.setUserId(request.userId());
        job.setStatus("CALCULATING");
        job.setRequestJson(writeJson(request));
        job.setAttempts(1);
        job.setLeaseToken(token);
        job.setLeaseUntil(now.plusSeconds(LEASE_SECONDS));
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        try {
            jobMapper.insert(job);
        } catch (DuplicateKeyException duplicate) {
            PricingJob duplicateJob = jobMapper.selectById(request.quoteId());
            if (duplicateJob == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "定价任务创建冲突");
            }
            claimRecovery(duplicateJob);
            return fromJob(jobMapper.selectById(request.quoteId()));
        }

        CompletableFuture<PricingCalculateResponse> future =
                CompletableFuture.supplyAsync(() -> computeAndFinish(request, token), EXECUTOR);
        try {
            return future.get(waitMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException timeout) {
            return calculatingResponse(request);
        } catch (Exception error) {
            return failureResponse(request, error);
        }
    }

    public PricingCalculateResponse status(String quoteId, Long userId) {
        PricingJob job = jobMapper.selectById(quoteId);
        if (job == null || !job.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "定价任务不存在");
        }
        if (claimRecovery(job)) {
            job = jobMapper.selectById(quoteId);
        }
        return fromJob(job);
    }

    /**
     * CALCULATING 任务的租约过期后，任何一次读取都可以抢占并异步重算；
     * 条件更新保证同一任务只会有一个恢复者，旧 worker 的 finish 会因 token 不匹配而失效。
     */
    private boolean claimRecovery(PricingJob job) {
        if (!"CALCULATING".equals(job.getStatus())) {
            return false;
        }
        LocalDateTime now = now();
        if (job.getLeaseUntil() != null && job.getLeaseUntil().isAfter(now)) {
            return false;
        }
        String token = UUID.randomUUID().toString();
        if (jobMapper.claimLease(job.getQuoteId(), token, now.plusSeconds(LEASE_SECONDS), now) != 1) {
            return false;
        }
        PricingCalculateRequest request = readRequest(job);
        if (request == null) {
            jobMapper.finish(job.getQuoteId(), token, "FAILED", null, null,
                    ErrorCode.INTERNAL_ERROR.name(), "定价任务恢复失败", now);
            return true;
        }
        CompletableFuture.runAsync(() -> computeAndFinish(request, token), EXECUTOR);
        return true;
    }

    private PricingCalculateResponse computeAndFinish(PricingCalculateRequest request, String token) {
        try {
            PricingCalculateResponse response = compute(request);
            finish(request, token, response);
            return response;
        } catch (Throwable error) {
            PricingCalculateResponse response = failureResponse(request, error);
            finish(request, token, response);
            return response;
        }
    }

    private PricingCalculateResponse compute(PricingCalculateRequest request) {
        try {
            List<CourseState> states = new ArrayList<>();
            for (PricingCourseAmount course : request.courses()) {
                states.add(new CourseState(course.courseId(), course.remainingCent() == null ? 0 : course.remainingCent(),
                        course.categoryIds() == null ? List.of() : course.categoryIds()));
            }
            long baseRemaining = states.stream().mapToLong(CourseState::remaining).sum();
            String mode = request.couponSelection() == null
                    ? "NONE" : request.couponSelection().mode().toUpperCase(Locale.ROOT);
            List<Candidate> candidates = candidates(request);
            if ("NONE".equals(mode) || candidates.isEmpty()) {
                return response(request, List.of(), 0);
            }
            List<Candidate> selected;
            if ("MANUAL".equals(mode)) {
                selected = candidatesForManual(request, candidates);
            } else {
                selected = null;
            }
            Plan best = selected == null ? bestAutomatic(request, candidates) : bestManual(request, selected);
            if (best == null) {
                return failureResponse(request, new BusinessException(ErrorCode.COUPON_COMBINATION_INVALID,
                        "所选优惠券无法形成有效方案"));
            }
            return response(request, best.lines(), best.discount());
        } catch (RuntimeException error) {
            return failureResponse(request, error);
        }
    }

    private List<Candidate> candidates(PricingCalculateRequest request) {
        LocalDateTime now = now();
        List<Candidate> result = new ArrayList<>();
        for (UserCoupon coupon : userCouponMapper.selectList(Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getUserId, request.userId())
                .eq(UserCoupon::getStatus, "AVAILABLE")
                .le(UserCoupon::getValidFrom, now)
                .gt(UserCoupon::getValidUntil, now)
                .orderByAsc(UserCoupon::getValidUntil)
                .orderByAsc(UserCoupon::getId))) {
            CouponRule rule = ruleMapper.selectById(coupon.getRuleId());
            if (rule != null) {
                result.add(new Candidate(coupon, rule));
            }
        }
        return result;
    }

    private List<Candidate> candidatesForManual(PricingCalculateRequest request, List<Candidate> candidates) {
        List<Long> ids = request.couponSelection() == null || request.couponSelection().userCouponIds() == null
                ? List.of() : request.couponSelection().userCouponIds();
        if (ids.isEmpty() || ids.size() > 3 || new HashSet<>(ids).size() != ids.size()) {
            throw new BusinessException(ErrorCode.COUPON_COMBINATION_INVALID, "手选券数量或去重不合法");
        }
        Map<Long, Candidate> byId = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            byId.put(candidate.coupon().getId(), candidate);
        }
        List<Candidate> selected = new ArrayList<>();
        for (Long id : ids) {
            Candidate candidate = byId.get(id);
            if (candidate == null) {
                throw new BusinessException(ErrorCode.COUPON_COMBINATION_INVALID, "所选券不可用或已过期");
            }
            selected.add(candidate);
        }
        return selected;
    }

    private Plan bestAutomatic(PricingCalculateRequest request, List<Candidate> candidates) {
        Plan[] best = {new Plan(List.of(), 0, List.of())};
        search(request, candidates, new ArrayList<>(), new HashSet<>(), new HashSet<>(), best);
        return best[0];
    }

    private Plan bestManual(PricingCalculateRequest request, List<Candidate> selected) {
        Plan[] best = {null};
        searchManual(request, selected, new ArrayList<>(), new HashSet<>(), new HashSet<>(), best);
        if (best[0] == null) {
            throw new BusinessException(ErrorCode.COUPON_COMBINATION_INVALID, "所选券无法按任何顺序生效");
        }
        return best[0];
    }

    private void search(PricingCalculateRequest request, List<Candidate> candidates,
                        List<Candidate> sequence, Set<Long> usedCoupons, Set<String> usedGroups, Plan[] best) {
        Evaluation evaluation = evaluate(request, sequence);
        if (evaluation != null && better(evaluation.plan(), best[0])) {
            best[0] = evaluation.plan();
        }
        if (sequence.size() >= 3) {
            return;
        }
        for (Candidate candidate : candidates) {
            String groupKey = groupKey(candidate);
            if (usedCoupons.contains(candidate.coupon().getId())
                    || usedGroups.contains(groupKey)) {
                continue;
            }
            usedCoupons.add(candidate.coupon().getId());
            usedGroups.add(groupKey);
            sequence.add(candidate);
            search(request, candidates, sequence, usedCoupons, usedGroups, best);
            sequence.remove(sequence.size() - 1);
            usedCoupons.remove(candidate.coupon().getId());
            usedGroups.remove(groupKey);
        }
    }

    private void searchManual(PricingCalculateRequest request, List<Candidate> candidates,
                              List<Candidate> sequence, Set<Long> usedCoupons, Set<String> usedGroups,
                              Plan[] best) {
        if (sequence.size() == candidates.size()) {
            Evaluation evaluation = evaluate(request, sequence);
            if (evaluation != null && (best[0] == null || better(evaluation.plan(), best[0]))) {
                best[0] = evaluation.plan();
            }
            return;
        }
        for (Candidate candidate : candidates) {
            String groupKey = groupKey(candidate);
            if (usedCoupons.contains(candidate.coupon().getId())
                    || usedGroups.contains(groupKey)) {
                continue;
            }
            usedCoupons.add(candidate.coupon().getId());
            usedGroups.add(groupKey);
            sequence.add(candidate);
            searchManual(request, candidates, sequence, usedCoupons, usedGroups, best);
            sequence.remove(sequence.size() - 1);
            usedCoupons.remove(candidate.coupon().getId());
            usedGroups.remove(groupKey);
        }
    }

    private String groupKey(Candidate candidate) {
        return candidate.coupon().getSourceType() + "#" + candidate.coupon().getSourceGroupId();
    }

    private Evaluation evaluate(PricingCalculateRequest request, List<Candidate> sequence) {
        List<CourseState> states = new ArrayList<>();
        for (PricingCourseAmount course : request.courses()) {
            states.add(new CourseState(course.courseId(), course.remainingCent() == null ? 0 : course.remainingCent(),
                    course.categoryIds() == null ? List.of() : course.categoryIds()));
        }
        List<PricingCouponPlan> plans = new ArrayList<>();
        long totalDiscount = 0;
        for (int index = 0; index < sequence.size(); index++) {
            Candidate candidate = sequence.get(index);
            List<CourseState> eligible = eligible(candidate.rule(), states, request.routeId());
            long base = eligible.stream().mapToLong(CourseState::remaining).sum();
            if (base <= 0 || base < candidate.rule().getThresholdCent()) {
                return null;
            }
            long expected = expectedDiscount(candidate.rule(), base);
            long totalRemaining = states.stream().mapToLong(CourseState::remaining).sum();
            long discount = Math.min(expected, Math.min(base, totalRemaining - 100));
            if (discount <= 0) {
                return null;
            }
            Map<Long, Long> allocation = allocate(discount, eligible);
            for (CourseState course : eligible) {
                course.remaining(course.remaining() - allocation.getOrDefault(course.courseId(), 0L));
            }
            List<PricingCouponAllocation> allocations = new ArrayList<>();
            for (Map.Entry<Long, Long> entry : allocation.entrySet()) {
                if (entry.getValue() > 0) {
                    allocations.add(new PricingCouponAllocation(entry.getKey(), entry.getValue()));
                }
            }
            plans.add(new PricingCouponPlan(candidate.coupon().getId(), index + 1, base, discount, allocations));
            totalDiscount += discount;
        }
        long payable = states.stream().mapToLong(CourseState::remaining).sum();
        if (payable < 100 && sequence.size() > 0) {
            return null;
        }
        return new Evaluation(new Plan(sequence, totalDiscount, plans));
    }

    private List<CourseState> eligible(CouponRule rule, List<CourseState> states, Long routeId) {
        List<CourseState> result = new ArrayList<>();
        for (CourseState state : states) {
            if (state.remaining() <= 0) {
                continue;
            }
            boolean match = switch (rule.getScope()) {
                case "ALL" -> true;
                case "COURSE" -> state.courseId().equals(rule.getTargetId());
                case "CATEGORY" -> state.categoryIds().contains(rule.getTargetId());
                case "ROUTE" -> routeId != null && routeId.equals(rule.getTargetId());
                default -> false;
            };
            if (match) {
                result.add(state);
            }
        }
        return result;
    }

    private long expectedDiscount(CouponRule rule, long base) {
        if ("DISCOUNT".equals(rule.getType())) {
            long expected = base * (10000L - rule.getPayRateBps()) / 10000L;
            return Math.min(expected, rule.getMaxDiscountCent());
        }
        return rule.getAmountCent() == null ? 0 : rule.getAmountCent();
    }

    private Map<Long, Long> allocate(long total, List<CourseState> courses) {
        long sum = courses.stream().mapToLong(CourseState::remaining).sum();
        Map<Long, Long> result = new LinkedHashMap<>();
        if (sum <= 0) {
            return result;
        }
        long[] base = new long[courses.size()];
        long[] remainder = new long[courses.size()];
        long allocated = 0;
        BigInteger totalValue = BigInteger.valueOf(total);
        BigInteger sumValue = BigInteger.valueOf(sum);
        for (int i = 0; i < courses.size(); i++) {
            BigInteger product = totalValue.multiply(BigInteger.valueOf(courses.get(i).remaining()));
            base[i] = product.divide(sumValue).longValueExact();
            remainder[i] = product.remainder(sumValue).longValueExact();
            allocated += base[i];
            result.put(courses.get(i).courseId(), base[i]);
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < courses.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator
                .comparingLong((Integer i) -> remainder[i]).reversed()
                .thenComparingLong(i -> courses.get(i).courseId()));
        for (int i = 0; i < total - allocated; i++) {
            result.put(courses.get(order.get(i)).courseId(),
                    result.get(courses.get(order.get(i)).courseId()) + 1);
        }
        return result;
    }

    private boolean better(Plan candidate, Plan current) {
        if (candidate.discount() != current.discount()) {
            return candidate.discount() > current.discount();
        }
        if (candidate.sequence().size() != current.sequence().size()) {
            return candidate.sequence().size() < current.sequence().size();
        }
        List<LocalDateTime> leftTimes = candidate.sequence().stream()
                .map(item -> item.coupon().getValidUntil()).sorted().toList();
        List<LocalDateTime> rightTimes = current.sequence().stream()
                .map(item -> item.coupon().getValidUntil()).sorted().toList();
        for (int i = 0; i < leftTimes.size(); i++) {
            int compare = leftTimes.get(i).compareTo(rightTimes.get(i));
            if (compare != 0) {
                return compare < 0;
            }
        }
        for (int i = 0; i < candidate.sequence().size(); i++) {
            int compare = candidate.sequence().get(i).coupon().getId()
                    .compareTo(current.sequence().get(i).coupon().getId());
            if (compare != 0) {
                return compare < 0;
            }
        }
        return false;
    }

    private PricingCalculateResponse response(PricingCalculateRequest request, List<PricingCouponPlan> plans,
                                               long discount) {
        long payable = request.totalCent() - request.creditCent() - discount;
        return new PricingCalculateResponse(request.quoteId(), "READY", pricingHash(request, plans),
                request.totalCent(), request.creditCent(), discount, payable, plans, null, null);
    }

    private PricingCalculateResponse emptyResponse(PricingCalculateRequest request) {
        return response(request, List.of(), 0);
    }

    private PricingCalculateResponse calculatingResponse(PricingCalculateRequest request) {
        return new PricingCalculateResponse(request.quoteId(), "CALCULATING", null,
                request.totalCent(), request.creditCent(), 0,
                request.totalCent() - request.creditCent(), List.of(), null, null);
    }

    private PricingCalculateResponse failureResponse(PricingCalculateRequest request, Throwable error) {
        String code = error instanceof BusinessException business
                ? business.errorCode().name() : ErrorCode.INTERNAL_ERROR.name();
        String message = error instanceof BusinessException ? error.getMessage() : "优惠计算失败";
        return new PricingCalculateResponse(request.quoteId(), "FAILED", null,
                request.totalCent(), request.creditCent(), 0,
                request.totalCent() - request.creditCent(), List.of(), code, message);
    }

    private void finish(PricingCalculateRequest request, String token, PricingCalculateResponse response) {
        jobMapper.finish(request.quoteId(), token, response.status(), writeJson(response),
                response.pricingHash(), response.errorCode(), response.errorMessage(), now());
    }

    private PricingCalculateRequest readRequest(PricingJob job) {
        try {
            return JSON.readValue(job.getRequestJson(), PricingCalculateRequest.class);
        } catch (Exception error) {
            return null;
        }
    }

    private PricingCalculateResponse fromJob(PricingJob job) {
        if ("CALCULATING".equals(job.getStatus())) {
            return new PricingCalculateResponse(job.getQuoteId(), "CALCULATING", null, 0, 0, 0, 0,
                    List.of(), null, null);
        }
        if (job.getResultJson() != null) {
            try {
                return JSON.readValue(job.getResultJson(), PricingCalculateResponse.class);
            } catch (Exception ignored) {
            }
        }
        return new PricingCalculateResponse(job.getQuoteId(), "FAILED", null, 0, 0, 0, 0,
                List.of(), job.getErrorCode(), job.getErrorMessage());
    }

    private String pricingHash(PricingCalculateRequest request, List<PricingCouponPlan> plans) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("quoteId", request.quoteId());
            root.put("courses", request.courses());
            root.put("selection", request.couponSelection());
            root.put("totalCent", request.totalCent());
            root.put("creditCent", request.creditCent());
            root.put("plans", plans);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(writeJson(root).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("pricing hash failed", e);
        }
    }

    private void validateRequest(PricingCalculateRequest request) {
        if (request == null || request.quoteId() == null || request.quoteId().isBlank()
                || request.userId() == null || request.userId() <= 0
                || request.courses() == null || request.courses().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "定价请求参数不合法");
        }
    }

    private String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("pricing json failed", e);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private record Candidate(UserCoupon coupon, CouponRule rule) {
    }

    private static final class CourseState {
        private final Long courseId;
        private final List<Long> categoryIds;
        private long remaining;

        private CourseState(Long courseId, long remaining, List<Long> categoryIds) {
            this.courseId = courseId;
            this.remaining = remaining;
            this.categoryIds = categoryIds;
        }

        private Long courseId() { return courseId; }
        private List<Long> categoryIds() { return categoryIds; }
        private long remaining() { return remaining; }
        private void remaining(long value) { this.remaining = Math.max(0, value); }
    }

    private record Evaluation(Plan plan) {
    }

    private record Plan(List<Candidate> sequence, long discount, List<PricingCouponPlan> lines) {
    }
}