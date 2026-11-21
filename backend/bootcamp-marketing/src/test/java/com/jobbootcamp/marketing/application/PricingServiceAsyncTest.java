package com.jobbootcamp.marketing.application;

import com.jobbootcamp.contracts.internal.PricingCalculateRequest;
import com.jobbootcamp.contracts.internal.PricingCalculateResponse;
import com.jobbootcamp.contracts.internal.PricingCouponSelection;
import com.jobbootcamp.contracts.internal.PricingCourseAmount;
import com.jobbootcamp.marketing.domain.CouponRule;
import com.jobbootcamp.marketing.domain.PricingJob;
import com.jobbootcamp.marketing.domain.UserCoupon;
import com.jobbootcamp.marketing.infrastructure.CouponRuleMapper;
import com.jobbootcamp.marketing.infrastructure.PricingJobMapper;
import com.jobbootcamp.marketing.infrastructure.UserCouponMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PricingServiceAsyncTest {

    @Test
    void slowCalculationReturnsCalculatingThenRecoversReadyResultIdempotently() throws Exception {
        UserCouponMapper couponMapper = mock(UserCouponMapper.class);
        CouponRuleMapper ruleMapper = mock(CouponRuleMapper.class);
        PricingJobMapper jobMapper = mock(PricingJobMapper.class);
        Map<String, PricingJob> jobs = new ConcurrentHashMap<>();

        when(jobMapper.insert(any(PricingJob.class))).thenAnswer(invocation -> {
            PricingJob job = invocation.getArgument(0);
            jobs.put(job.getQuoteId(), job);
            return 1;
        });
        when(jobMapper.selectById(anyString())).thenAnswer(invocation -> jobs.get(invocation.getArgument(0)));
        when(jobMapper.finish(anyString(), anyString(), anyString(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    PricingJob job = jobs.get(invocation.getArgument(0));
                    if (job == null || !Objects.equals(job.getLeaseToken(), invocation.getArgument(1))) {
                        return 0;
                    }
                    job.setStatus(invocation.getArgument(2));
                    job.setResultJson(invocation.getArgument(3));
                    job.setResultHash(invocation.getArgument(4));
                    job.setErrorCode(invocation.getArgument(5));
                    job.setErrorMessage(invocation.getArgument(6));
                    job.setUpdatedAt(invocation.getArgument(7));
                    job.setLeaseToken(null);
                    job.setLeaseUntil(null);
                    return 1;
                });

        UserCoupon coupon = new UserCoupon();
        coupon.setId(1L);
        coupon.setUserId(7L);
        coupon.setRuleId(1L);
        coupon.setSourceGroupId(1L);
        coupon.setValidFrom(LocalDateTime.now().minusDays(1));
        coupon.setValidUntil(LocalDateTime.now().plusDays(1));
        coupon.setStatus("AVAILABLE");
        when(couponMapper.selectList(any())).thenReturn(List.of(coupon));

        CouponRule rule = new CouponRule();
        rule.setId(1L);
        rule.setType("FULL_REDUCTION");
        rule.setScope("ALL");
        rule.setThresholdCent(0L);
        rule.setAmountCent(200L);
        when(ruleMapper.selectById(any())).thenAnswer(invocation -> {
            Thread.sleep(60);
            return rule;
        });

        PricingService service = new PricingService(couponMapper, ruleMapper, jobMapper, 1);
        PricingCalculateRequest request = new PricingCalculateRequest(UUID.randomUUID().toString(), 7L,
                "COURSE", 101L, null, 1000, 0,
                List.of(new PricingCourseAmount(101L, 1000L, List.of(10L))),
                new PricingCouponSelection("AUTO", List.of()));

        PricingCalculateResponse first = service.calculate(request);
        assertThat(first.status()).isEqualTo("CALCULATING");

        PricingCalculateResponse ready = null;
        for (int attempt = 0; attempt < 100; attempt++) {
            ready = service.status(request.quoteId(), 7L);
            if ("READY".equals(ready.status())) {
                break;
            }
            Thread.sleep(20);
        }
        assertThat(ready).isNotNull();
        assertThat(ready.status()).isEqualTo("READY");
        assertThat(ready.discountCent()).isEqualTo(200);
        assertThat(ready.payableCent()).isEqualTo(800);

        PricingCalculateResponse replay = service.calculate(request);
        assertThat(replay.status()).isEqualTo("READY");
        assertThat(replay.pricingHash()).isEqualTo(ready.pricingHash());
        verify(ruleMapper, times(1)).selectById(any());
    }

    @Test
    void expiredLeaseIsReclaimedAndRecoveredOnceByAnotherRead() throws Exception {
        UserCouponMapper couponMapper = mock(UserCouponMapper.class);
        CouponRuleMapper ruleMapper = mock(CouponRuleMapper.class);
        PricingJobMapper jobMapper = mock(PricingJobMapper.class);
        Map<String, PricingJob> jobs = new ConcurrentHashMap<>();

        when(jobMapper.selectById(anyString())).thenAnswer(invocation -> jobs.get(invocation.getArgument(0)));
        when(jobMapper.claimLease(anyString(), anyString(), any(), any())).thenAnswer(invocation -> {
            PricingJob job = jobs.get(invocation.getArgument(0));
            if (job == null || !"CALCULATING".equals(job.getStatus())
                    || (job.getLeaseUntil() != null && job.getLeaseUntil().isAfter(invocation.getArgument(3)))) {
                return 0;
            }
            job.setLeaseToken(invocation.getArgument(1));
            job.setLeaseUntil(invocation.getArgument(2));
            job.setAttempts(job.getAttempts() == null ? 1 : job.getAttempts() + 1);
            return 1;
        });
        when(jobMapper.finish(anyString(), anyString(), anyString(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    PricingJob job = jobs.get(invocation.getArgument(0));
                    if (job == null || !Objects.equals(job.getLeaseToken(), invocation.getArgument(1))) {
                        return 0;
                    }
                    job.setStatus(invocation.getArgument(2));
                    job.setResultJson(invocation.getArgument(3));
                    job.setResultHash(invocation.getArgument(4));
                    job.setLeaseToken(null);
                    job.setLeaseUntil(null);
                    job.setUpdatedAt(invocation.getArgument(7));
                    return 1;
                });

        UserCoupon coupon = new UserCoupon();
        coupon.setId(1L);
        coupon.setUserId(7L);
        coupon.setRuleId(1L);
        coupon.setSourceType("ACTIVITY");
        coupon.setSourceGroupId(1L);
        coupon.setValidFrom(LocalDateTime.now().minusDays(1));
        coupon.setValidUntil(LocalDateTime.now().plusDays(1));
        coupon.setStatus("AVAILABLE");
        when(couponMapper.selectList(any())).thenReturn(List.of(coupon));

        CouponRule rule = new CouponRule();
        rule.setId(1L);
        rule.setType("NO_THRESHOLD");
        rule.setScope("ALL");
        rule.setThresholdCent(0L);
        rule.setAmountCent(200L);
        when(ruleMapper.selectById(any())).thenReturn(rule);

        PricingCalculateRequest request = new PricingCalculateRequest(UUID.randomUUID().toString(), 7L,
                "COURSE", 101L, null, 1000, 0,
                List.of(new PricingCourseAmount(101L, 1000L, List.of(10L))),
                new PricingCouponSelection("AUTO", List.of()));
        PricingJob job = new PricingJob();
        job.setQuoteId(request.quoteId());
        job.setUserId(7L);
        job.setStatus("CALCULATING");
        job.setRequestJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request));
        job.setAttempts(1);
        job.setLeaseToken("dead-worker");
        job.setLeaseUntil(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        jobs.put(job.getQuoteId(), job);

        PricingService service = new PricingService(couponMapper, ruleMapper, jobMapper, 500);
        assertThat(service.status(request.quoteId(), 7L).status()).isIn("CALCULATING", "READY");

        PricingCalculateResponse ready = null;
        for (int attempt = 0; attempt < 100; attempt++) {
            ready = service.status(request.quoteId(), 7L);
            if ("READY".equals(ready.status())) {
                break;
            }
            Thread.sleep(20);
        }
        assertThat(ready).isNotNull();
        assertThat(ready.status()).isEqualTo("READY");
        assertThat(ready.discountCent()).isEqualTo(200);
        assertThat(job.getAttempts()).isEqualTo(2);
        assertThat(job.getResultHash()).isEqualTo(ready.pricingHash());
        verify(jobMapper, times(1)).claimLease(anyString(), anyString(), any(), any());
    }
}