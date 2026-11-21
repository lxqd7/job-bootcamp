package com.jobbootcamp.marketing.application;

import com.jobbootcamp.contracts.internal.PricingCalculateRequest;
import com.jobbootcamp.contracts.internal.PricingCalculateResponse;
import com.jobbootcamp.contracts.internal.PricingCouponAllocation;
import com.jobbootcamp.contracts.internal.PricingCouponPlan;
import com.jobbootcamp.contracts.internal.PricingCouponSelection;
import com.jobbootcamp.contracts.internal.PricingCourseAmount;
import com.jobbootcamp.marketing.domain.CouponRule;
import com.jobbootcamp.marketing.domain.UserCoupon;
import com.jobbootcamp.marketing.infrastructure.CouponRuleMapper;
import com.jobbootcamp.marketing.infrastructure.PricingJobMapper;
import com.jobbootcamp.marketing.infrastructure.UserCouponMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 独立小穷举 oracle：不调用 PricingService 的搜索代码，单独枚举全部券序列，
 * 用于核对自动选券、手选排列、最低实付、同活动互斥、分摊和并列规则。
 */
class PricingServiceOracleTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);

    private final UserCouponMapper couponMapper = mock(UserCouponMapper.class);
    private final CouponRuleMapper ruleMapper = mock(CouponRuleMapper.class);
    private final PricingJobMapper jobMapper = mock(PricingJobMapper.class);
    private final PricingService service = new PricingService(couponMapper, ruleMapper, jobMapper);
    private final Map<Long, LocalDateTime> expiries = new HashMap<>();

    {
        when(jobMapper.selectById(anyString())).thenReturn(null);
        when(jobMapper.insert(any(com.jobbootcamp.marketing.domain.PricingJob.class))).thenReturn(1);
    }

    @Test
    void discountOrderMattersAndAutomaticPicksBestSequence() {
        List<ScenarioCoupon> coupons = List.of(
                coupon(1, "FULL_REDUCTION", "ALL", null, 10000, 3000, null, null, 1, 1),
                coupon(2, "DISCOUNT", "ALL", null, 0, null, 9000, 5000, 2, 2));
        stub(coupons);

        PricingCalculateResponse result = service.calculate(request("AUTO", List.of(), 20000,
                List.of(new PricingCourseAmount(101L, 20000L, List.of(1L, 10L))), null));

        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.discountCent()).isEqualTo(5000);
        assertThat(result.payableCent()).isEqualTo(15000);
        assertThat(planIds(result)).containsExactly(2L, 1L);
        assertThat(result.couponPlan().get(0).baseCent()).isEqualTo(20000);
        assertThat(result.couponPlan().get(0).discountCent()).isEqualTo(2000);
        assertThat(result.couponPlan().get(1).baseCent()).isEqualTo(18000);
        assertThat(result.couponPlan().get(1).discountCent()).isEqualTo(3000);
    }

    @Test
    void minimumPayableLeavesOneYuanAndAllocationSumsExactly() {
        List<ScenarioCoupon> coupons = List.of(
                coupon(1, "FULL_REDUCTION", "ALL", null, 0, 5000, null, null, 1, 1));
        stub(coupons);

        PricingCalculateResponse result = service.calculate(request("AUTO", List.of(), 3000,
                List.of(new PricingCourseAmount(101L, 1001L, List.of(10L)),
                        new PricingCourseAmount(102L, 1999L, List.of(20L))), null));

        assertThat(result.discountCent()).isEqualTo(2900);
        assertThat(result.payableCent()).isEqualTo(100);
        long allocated = result.couponPlan().get(0).allocation().stream()
                .mapToLong(PricingCouponAllocation::discountCent).sum();
        assertThat(allocated).isEqualTo(2900);
        assertThat(result.couponPlan().get(0).allocation())
                .extracting(PricingCouponAllocation::courseId)
                .containsExactly(101L, 102L);
    }

    @Test
    void categoryScopeOnlyUsesMatchingCourseAndSameGroupIsExclusive() {
        List<ScenarioCoupon> coupons = List.of(
                coupon(1, "FULL_REDUCTION", "CATEGORY", 10L, 0, 5000, null, null, 1, 1),
                coupon(2, "FULL_REDUCTION", "ALL", null, 0, 2000, null, null, 2, 2),
                coupon(3, "FULL_REDUCTION", "ALL", null, 0, 1000, null, null, 2, 3));
        stub(coupons);

        PricingCalculateResponse result = service.calculate(request("AUTO", List.of(), 10000,
                List.of(new PricingCourseAmount(101L, 3333L, List.of(1L, 10L)),
                        new PricingCourseAmount(102L, 6667L, List.of(1L, 20L))), null));

        assertThat(result.discountCent()).isEqualTo(5333);
        assertThat(planIds(result)).containsExactly(1L, 2L);
        Map<Long, Long> allocated = allocation(result.couponPlan().get(0));
        assertThat(allocated).containsOnlyKeys(101L);
        assertThat(allocated.get(101L)).isEqualTo(3333);
        assertThat(allocation(result.couponPlan().get(1)))
                .containsExactly(Map.entry(102L, 2000L));
    }

    @Test
    void manualUsesOnlySelectedCouponsAndChoosesBestPermutation() {
        List<ScenarioCoupon> coupons = List.of(
                coupon(1, "FULL_REDUCTION", "ALL", null, 10000, 3000, null, null, 1, 1),
                coupon(2, "DISCOUNT", "ALL", null, 0, null, 9000, 5000, 2, 2),
                coupon(3, "FULL_REDUCTION", "ALL", null, 0, 500, null, null, 3, 3));
        stub(coupons);

        PricingCalculateResponse result = service.calculate(request("MANUAL", List.of(1L, 2L), 20000,
                List.of(new PricingCourseAmount(101L, 20000L, List.of(1L, 10L))), null));

        assertThat(result.status()).isEqualTo("READY");
        assertThat(planIds(result)).containsExactly(2L, 1L);
        assertThat(result.discountCent()).isEqualTo(5000);
    }

    @Test
    void manualRejectsCombinationWhenOneCouponCannotApply() {
        List<ScenarioCoupon> coupons = List.of(
                coupon(1, "FULL_REDUCTION", "ALL", null, 9000, 3000, null, null, 1, 1),
                coupon(2, "FULL_REDUCTION", "ALL", null, 9000, 3000, null, null, 2, 2));
        stub(coupons);

        PricingCalculateResponse result = service.calculate(request("MANUAL", List.of(1L, 2L), 10000,
                List.of(new PricingCourseAmount(101L, 10000L, List.of(10L))), null));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("COUPON_COMBINATION_INVALID");
    }

    @Test
    void randomSmallScenariosMatchIndependentExhaustiveOracle() {
        Random random = new Random(20261121);
        for (int round = 0; round < 40; round++) {
            int courseCount = 1 + random.nextInt(3);
            List<Long> courseIds = new ArrayList<>();
            List<PricingCourseAmount> amounts = new ArrayList<>();
            Map<Long, List<Long>> categories = new LinkedHashMap<>();
            long total = 0;
            for (int index = 0; index < courseCount; index++) {
                long courseId = 101 + index;
                long remaining = 500 + random.nextInt(12000);
                List<Long> categoryIds = List.of(1L, 10L + index);
                courseIds.add(courseId);
                categories.put(courseId, categoryIds);
                amounts.add(new PricingCourseAmount(courseId, remaining, categoryIds));
                total += remaining;
            }
            boolean route = random.nextBoolean();
            Long routeId = route ? 900L : null;

            List<ScenarioCoupon> coupons = new ArrayList<>();
            int couponCount = 3 + random.nextInt(4);
            for (int index = 0; index < couponCount; index++) {
                long id = index + 1;
                boolean discountType = random.nextBoolean();
                String scope = switch (random.nextInt(4)) {
                    case 0 -> "ALL";
                    case 1 -> "CATEGORY";
                    case 2 -> "COURSE";
                    default -> "ROUTE";
                };
                Long target = switch (scope) {
                    case "CATEGORY" -> 10L + random.nextInt(courseCount);
                    case "COURSE" -> courseIds.get(random.nextInt(courseCount));
                    case "ROUTE" -> routeId;
                    default -> null;
                };
                long threshold = random.nextInt(3) == 0 ? 3000 : 0;
                long group = index % 3 == 1 ? 50 : id;
                coupons.add(coupon(id, discountType ? "DISCOUNT" : "FULL_REDUCTION", scope, target, threshold,
                        discountType ? null : 500 + random.nextInt(4500),
                        discountType ? 8000 + random.nextInt(1800) : null,
                        discountType ? 500 + random.nextInt(4500) : null,
                        group, id));
            }
            stub(coupons);

            PricingCalculateRequest autoRequest = request("AUTO", List.of(), total, amounts, routeId);
            PricingCalculateResponse auto = service.calculate(autoRequest);
            OraclePlan expectedAuto = oracle(coupons, courseIds, amounts, routeId, null);
            assertThat(auto.status()).as("round " + round).isEqualTo("READY");
            assertEquivalent(auto, expectedAuto, "AUTO round " + round);

            List<Long> manualIds = coupons.stream().limit(2).map(ScenarioCoupon::id).toList();
            PricingCalculateRequest manualRequest = request("MANUAL", manualIds, total, amounts, routeId);
            PricingCalculateResponse manual = service.calculate(manualRequest);
            OraclePlan expectedManual = oracle(coupons, courseIds, amounts, routeId, manualIds);
            if (expectedManual == null) {
                assertThat(manual.status()).as("manual round " + round).isEqualTo("FAILED");
            } else {
                assertThat(manual.status()).as("manual round " + round).isEqualTo("READY");
                assertEquivalent(manual, expectedManual, "MANUAL round " + round);
            }
        }
    }

    private void assertEquivalent(PricingCalculateResponse actual, OraclePlan expected, String message) {
        assertThat(actual.discountCent()).as(message).isEqualTo(expected.discount());
        assertThat(actual.payableCent()).as(message).isEqualTo(expected.payable());
        assertThat(planIds(actual)).as(message).isEqualTo(expected.steps().stream()
                .map(OracleStep::couponId).toList());
        for (int index = 0; index < expected.steps().size(); index++) {
            OracleStep step = expected.steps().get(index);
            PricingCouponPlan plan = actual.couponPlan().get(index);
            assertThat(plan.applyOrder()).as(message).isEqualTo(index + 1);
            assertThat(plan.baseCent()).as(message).isEqualTo(step.base());
            assertThat(plan.discountCent()).as(message).isEqualTo(step.discount());
            Map<Long, Long> expectedAllocation = new LinkedHashMap<>();
            for (Map.Entry<Long, Long> entry : step.allocation().entrySet()) {
                if (entry.getValue() > 0) {
                    expectedAllocation.put(entry.getKey(), entry.getValue());
                }
            }
            assertThat(allocation(plan)).as(message).isEqualTo(expectedAllocation);
            assertThat(plan.allocation().stream().mapToLong(PricingCouponAllocation::discountCent).sum())
                    .as(message).isEqualTo(step.discount());
        }
    }

    private List<Long> planIds(PricingCalculateResponse response) {
        return response.couponPlan().stream().map(PricingCouponPlan::userCouponId).toList();
    }

    private Map<Long, Long> allocation(PricingCouponPlan plan) {
        Map<Long, Long> result = new LinkedHashMap<>();
        for (PricingCouponAllocation item : plan.allocation()) {
            result.put(item.courseId(), item.discountCent());
        }
        return result;
    }

    private void stub(List<ScenarioCoupon> coupons) {
        List<UserCoupon> userCoupons = new ArrayList<>();
        Map<Long, CouponRule> rules = new HashMap<>();
        expiries.clear();
        for (ScenarioCoupon value : coupons) {
            userCoupons.add(value.userCoupon());
            rules.put(value.rule().getId(), value.rule());
            expiries.put(value.id(), value.userCoupon().getValidUntil());
        }
        when(couponMapper.selectList(any())).thenReturn(userCoupons);
        when(ruleMapper.selectById(any())).thenAnswer(invocation -> rules.get(invocation.getArgument(0)));
    }

    private PricingCalculateRequest request(String mode, List<Long> ids, long total,
                                            List<PricingCourseAmount> amounts, Long routeId) {
        return new PricingCalculateRequest(UUID.randomUUID().toString(), 7L,
                routeId == null ? "COURSE" : "ROUTE", routeId == null ? 101L : routeId,
                routeId, total, 0, amounts, new PricingCouponSelection(mode, ids));
    }

    private ScenarioCoupon coupon(long id, String type, String scope, Long target, long threshold,
                                  Integer amount, Integer payRateBps, Integer maxDiscount, long group, long days) {
        CouponRule rule = new CouponRule();
        rule.setId(id);
        rule.setRuleKey("rule-" + id);
        rule.setVersionNo(1);
        rule.setType(type);
        rule.setScope(scope);
        rule.setTargetId(target);
        rule.setThresholdCent(threshold);
        rule.setAmountCent(amount == null ? null : amount.longValue());
        rule.setPayRateBps(payRateBps);
        rule.setMaxDiscountCent(maxDiscount == null ? null : maxDiscount.longValue());
        rule.setIssued(true);

        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setId(id);
        userCoupon.setUserId(7L);
        userCoupon.setRuleId(id);
        userCoupon.setSourceGroupId(group);
        userCoupon.setValidFrom(NOW.minusDays(1));
        userCoupon.setValidUntil(NOW.plusDays(days));
        userCoupon.setStatus("AVAILABLE");
        return new ScenarioCoupon(userCoupon, rule);
    }

    private OraclePlan oracle(List<ScenarioCoupon> coupons, List<Long> courseIds,
                              List<PricingCourseAmount> amounts, Long routeId, List<Long> manualIds) {
        OraclePlan[] best = {manualIds == null ? new OraclePlan(List.of(), 0, total(amounts)) : null};
        List<ScenarioCoupon> pool = manualIds == null ? coupons
                : coupons.stream().filter(coupon -> manualIds.contains(coupon.id())).toList();
        if (manualIds != null && (pool.size() != manualIds.size() || new HashSet<>(manualIds).size() != manualIds.size())) {
            return null;
        }
        enumerate(pool, manualIds != null, routeId, courseIds, amounts, new ArrayList<>(), new HashSet<>(),
                new HashSet<>(), best);
        return best[0];
    }

    private void enumerate(List<ScenarioCoupon> pool, boolean exactSize, Long routeId, List<Long> courseIds,
                           List<PricingCourseAmount> amounts, List<ScenarioCoupon> sequence,
                           Set<Long> usedIds, Set<Long> usedGroups, OraclePlan[] best) {
        if (exactSize && sequence.size() == pool.size()) {
            OraclePlan plan = simulate(sequence, courseIds, amounts, routeId);
            if (plan != null && (best[0] == null || better(plan, best[0]))) {
                best[0] = plan;
            }
            return;
        }
        if (!exactSize) {
            OraclePlan plan = simulate(sequence, courseIds, amounts, routeId);
            if (plan != null && (best[0] == null || better(plan, best[0]))) {
                best[0] = plan;
            }
            if (sequence.size() == 3) {
                return;
            }
        }
        for (ScenarioCoupon candidate : pool) {
            if (usedIds.contains(candidate.id()) || usedGroups.contains(candidate.group())) {
                continue;
            }
            usedIds.add(candidate.id());
            usedGroups.add(candidate.group());
            sequence.add(candidate);
            enumerate(pool, exactSize, routeId, courseIds, amounts, sequence, usedIds, usedGroups, best);
            sequence.remove(sequence.size() - 1);
            usedIds.remove(candidate.id());
            usedGroups.remove(candidate.group());
        }
    }

    private OraclePlan simulate(List<ScenarioCoupon> sequence, List<Long> courseIds,
                                List<PricingCourseAmount> amounts, Long routeId) {
        Map<Long, Long> remaining = new LinkedHashMap<>();
        Map<Long, List<Long>> categories = new LinkedHashMap<>();
        long total = 0;
        for (PricingCourseAmount amount : amounts) {
            remaining.put(amount.courseId(), amount.remainingCent());
            categories.put(amount.courseId(), amount.categoryIds());
            total += amount.remainingCent();
        }
        List<OracleStep> steps = new ArrayList<>();
        long discountTotal = 0;
        for (ScenarioCoupon coupon : sequence) {
            List<Long> eligible = new ArrayList<>();
            long base = 0;
            for (Long courseId : courseIds) {
                long value = remaining.get(courseId);
                if (value <= 0) {
                    continue;
                }
                boolean match = switch (coupon.rule().getScope()) {
                    case "ALL" -> true;
                    case "COURSE" -> courseId.equals(coupon.rule().getTargetId());
                    case "CATEGORY" -> categories.get(courseId).contains(coupon.rule().getTargetId());
                    case "ROUTE" -> routeId != null && routeId.equals(coupon.rule().getTargetId());
                    default -> false;
                };
                if (match) {
                    eligible.add(courseId);
                    base += value;
                }
            }
            if (base <= 0 || base < coupon.rule().getThresholdCent()) {
                return null;
            }
            long expected = "DISCOUNT".equals(coupon.rule().getType())
                    ? Math.min(base * (10000L - coupon.rule().getPayRateBps()) / 10000L,
                    coupon.rule().getMaxDiscountCent())
                    : coupon.rule().getAmountCent();
            long remainingTotal = remaining.values().stream().mapToLong(Long::longValue).sum();
            long discount = Math.min(expected, Math.min(base, remainingTotal - 100));
            if (discount <= 0) {
                return null;
            }
            Map<Long, Long> allocation = split(discount, eligible, remaining);
            for (Long courseId : eligible) {
                remaining.put(courseId, Math.max(0, remaining.get(courseId) - allocation.get(courseId)));
            }
            steps.add(new OracleStep(coupon.id(), base, discount, allocation));
            discountTotal += discount;
        }
        long payable = remaining.values().stream().mapToLong(Long::longValue).sum();
        if (!sequence.isEmpty() && payable < 100) {
            return null;
        }
        return new OraclePlan(steps, discountTotal, payable);
    }

    private Map<Long, Long> split(long discount, List<Long> eligible, Map<Long, Long> remaining) {
        long sum = eligible.stream().mapToLong(remaining::get).sum();
        Map<Long, Long> result = new LinkedHashMap<>();
        long[] base = new long[eligible.size()];
        long[] rest = new long[eligible.size()];
        long allocated = 0;
        for (int index = 0; index < eligible.size(); index++) {
            long value = remaining.get(eligible.get(index));
            base[index] = discount * value / sum;
            rest[index] = discount * value % sum;
            result.put(eligible.get(index), base[index]);
            allocated += base[index];
        }
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < eligible.size(); index++) {
            order.add(index);
        }
        order.sort(Comparator.comparingLong((Integer index) -> rest[index]).reversed()
                .thenComparingLong(index -> eligible.get(index)));
        for (int index = 0; index < discount - allocated; index++) {
            Long courseId = eligible.get(order.get(index));
            result.put(courseId, result.get(courseId) + 1);
        }
        return result;
    }

    private boolean better(OraclePlan candidate, OraclePlan current) {
        if (candidate.discount() != current.discount()) {
            return candidate.discount() > current.discount();
        }
        if (candidate.steps().size() != current.steps().size()) {
            return candidate.steps().size() < current.steps().size();
        }
        List<LocalDateTime> left = candidate.steps().stream()
                .map(step -> expiryOf(step.couponId())).sorted().toList();
        List<LocalDateTime> right = current.steps().stream()
                .map(step -> expiryOf(step.couponId())).sorted().toList();
        for (int index = 0; index < left.size(); index++) {
            int compare = left.get(index).compareTo(right.get(index));
            if (compare != 0) {
                return compare < 0;
            }
        }
        for (int index = 0; index < candidate.steps().size(); index++) {
            int compare = candidate.steps().get(index).couponId()
                    .compareTo(current.steps().get(index).couponId());
            if (compare != 0) {
                return compare < 0;
            }
        }
        return false;
    }

    private LocalDateTime expiryOf(Long couponId) {
        return expiries.get(couponId);
    }

    private long total(List<PricingCourseAmount> amounts) {
        return amounts.stream().mapToLong(PricingCourseAmount::remainingCent).sum();
    }

    private record ScenarioCoupon(UserCoupon userCoupon, CouponRule rule) {
        private Long id() {
            return userCoupon.getId();
        }

        private long group() {
            return userCoupon.getSourceGroupId();
        }
    }

    private record OracleStep(Long couponId, long base, long discount, Map<Long, Long> allocation) {
    }

    private record OraclePlan(List<OracleStep> steps, long discount, long payable) {
    }
}