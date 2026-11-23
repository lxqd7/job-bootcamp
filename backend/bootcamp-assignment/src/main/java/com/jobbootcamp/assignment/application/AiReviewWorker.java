package com.jobbootcamp.assignment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbootcamp.assignment.domain.AiFeedback;
import com.jobbootcamp.assignment.domain.AiTask;
import com.jobbootcamp.assignment.domain.AssignmentVersion;
import com.jobbootcamp.assignment.domain.Submission;
import com.jobbootcamp.assignment.infrastructure.AiAttemptMapper;
import com.jobbootcamp.assignment.infrastructure.AiFeedbackMapper;
import com.jobbootcamp.assignment.infrastructure.AiTaskMapper;
import com.jobbootcamp.assignment.infrastructure.AiUserSlotMapper;
import com.jobbootcamp.assignment.infrastructure.AssignmentVersionMapper;
import com.jobbootcamp.assignment.infrastructure.SubmissionMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 点评任务执行器：任务表是事实源，扫描兜底；MQ 只负责尽快唤醒。
 * 领取时写 90 秒租约并每 20 秒续租，完成/失败必须带当前 leaseToken；
 * 租约过期可被其他 worker 领回，旧 worker 的迟到结果会被丢弃。
 */
@Component
public class AiReviewWorker {

    private static final Logger log = LoggerFactory.getLogger(AiReviewWorker.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AiTaskMapper taskMapper;
    private final AiFeedbackMapper feedbackMapper;
    private final SubmissionMapper submissionMapper;
    private final AssignmentVersionMapper versionMapper;
    private final AiUserSlotMapper slotMapper;
    private final AiAttemptMapper attemptMapper;
    private final AiReviewEngine engine;
    private final TransactionTemplate transactionTemplate;
    private final int concurrency;
    private final long leaseSeconds;
    private final long renewMs;
    private final int maxCalls;

    private final ExecutorService executor;
    private final ScheduledExecutorService leaseScheduler;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final Object pollLock = new Object();

    public AiReviewWorker(AiTaskMapper taskMapper,
                          AiFeedbackMapper feedbackMapper,
                          SubmissionMapper submissionMapper,
                          AssignmentVersionMapper versionMapper,
                          AiUserSlotMapper slotMapper,
                          AiAttemptMapper attemptMapper,
                          AiReviewEngine engine,
                          PlatformTransactionManager transactionManager,
                          @Value("${app.ai.worker-concurrency:4}") int concurrency,
                          @Value("${app.ai.lease-seconds:90}") long leaseSeconds,
                          @Value("${app.ai.lease-renew-ms:20000}") long renewMs,
                          @Value("${app.ai.max-calls:6}") int maxCalls) {
        this.taskMapper = taskMapper;
        this.feedbackMapper = feedbackMapper;
        this.submissionMapper = submissionMapper;
        this.versionMapper = versionMapper;
        this.slotMapper = slotMapper;
        this.attemptMapper = attemptMapper;
        this.engine = engine;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.concurrency = Math.max(1, concurrency);
        this.leaseSeconds = Math.max(5, leaseSeconds);
        this.renewMs = Math.max(100, renewMs);
        this.maxCalls = Math.max(1, maxCalls);
        this.executor = Executors.newFixedThreadPool(this.concurrency, runnable -> {
            Thread thread = new Thread(runnable, "ai-review-worker");
            thread.setDaemon(true);
            return thread;
        });
        this.leaseScheduler = Executors.newScheduledThreadPool(Math.max(2, this.concurrency), runnable -> {
            Thread thread = new Thread(runnable, "ai-review-lease");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PostConstruct
    void start() {
        // executor 在构造器创建，这里只用于显式生命周期日志。
        log.info("AI review worker ready: concurrency={}, lease={}s, renew={}ms", concurrency, leaseSeconds, renewMs);
    }

    /**
     * 定时扫描与 MQ 唤醒共用同一入口；重复调用是安全的。
     */
    @Scheduled(fixedDelayString = "${app.ai.worker-poll-ms:2000}")
    public void poll() {
        synchronized (pollLock) {
            recoverDeadlines();
            recoverExpiredLeases();
            int free = concurrency - inFlight.get();
            for (int i = 0; i < free; i += 1) {
                Claim claim = claim();
                if (claim == null) {
                    break;
                }
                inFlight.incrementAndGet();
                try {
                    executor.submit(() -> {
                        try {
                            process(claim);
                        } catch (RuntimeException e) {
                            log.error("AI review worker failed for task {}", claim.taskId(), e);
                        } finally {
                            inFlight.decrementAndGet();
                        }
                    });
                } catch (RuntimeException rejected) {
                    inFlight.decrementAndGet();
                    log.warn("AI review executor rejected task {}", claim.taskId());
                    break;
                }
            }
        }
    }

    public void wake() {
        poll();
    }

    private Claim claim() {
        String token = UUID.randomUUID().toString();
        LocalDateTime now = now();
        LocalDateTime leaseUntil = now.plusSeconds(leaseSeconds);
        Long taskId = transactionTemplate.execute(status -> {
            Long candidate = taskMapper.selectClaimableId(now);
            if (candidate == null) {
                return null;
            }
            taskMapper.markClaimed(candidate, token, leaseUntil, now);
            return candidate;
        });
        return taskId == null ? null : new Claim(taskId, token);
    }

    private void process(Claim claim) {
        AiTask task = taskMapper.selectById(claim.taskId());
        if (!owned(task, claim)) {
            return;
        }
        Submission submission = submissionMapper.selectById(task.getSubmissionId());
        if (submission == null) {
            fail(claim, "AI_UNAVAILABLE");
            return;
        }
        AssignmentVersion version = versionMapper.selectById(submission.getVersionId());
        if (version == null) {
            fail(claim, "AI_UNAVAILABLE");
            return;
        }
        LeaseKeeper keeper = new LeaseKeeper(claim);
        try {
            AiReviewEngine.ReviewFeedback feedback = engine.generate(task, submission, version);
            if (keeper.lost()) {
                log.warn("AI task {} lease was lost before result; stale result discarded", claim.taskId());
                return;
            }
            complete(claim, feedback);
        } catch (AiLeaseLostException e) {
            log.warn("AI task {} lease lost while engine was running; result discarded", claim.taskId());
            return;
        } catch (AiEngineException e) {
            if (Thread.currentThread().isInterrupted()) {
                log.info("AI task {} interrupted by shutdown; lease will expire for recovery", claim.taskId());
                return;
            }
            if (keeper.lost()) {
                log.warn("AI task {} lease was lost after engine error; stale failure discarded", claim.taskId());
                return;
            }
            log.warn("AI task {} engine failed: safeCode={}, reason={}",
                    claim.taskId(), e.safeCode(), e.getMessage());
            fail(claim, e.safeCode());
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                log.info("AI task {} interrupted by shutdown; lease will expire for recovery", claim.taskId());
                return;
            }
            if (keeper.lost()) {
                log.warn("AI task {} lease was lost after engine error; stale failure discarded", claim.taskId());
                return;
            }
            log.warn("AI task {} engine failed: {}", claim.taskId(), e.getClass().getSimpleName());
            fail(claim, "AI_UNAVAILABLE");
        } finally {
            keeper.close();
        }
    }

    private void complete(Claim claim, AiReviewEngine.ReviewFeedback feedback) {
        Boolean applied = transactionTemplate.execute(status -> {
            AiTask task = taskMapper.selectForUpdate(claim.taskId());
            if (!owned(task, claim)) {
                log.warn("AI task {} completion rejected: lease no longer owned", claim.taskId());
                return false;
            }
            LocalDateTime now = now();
            if (!now.isBefore(task.getDeadlineAt())) {
                log.warn("AI task {} result arrived after deadline; failing", claim.taskId());
                failLocked(task, "AI_TIMEOUT", now);
                return false;
            }
            AiFeedback entity = new AiFeedback();
            entity.setTaskId(task.getId());
            entity.setVerdict(feedback.verdict());
            entity.setSummary(feedback.summary());
            entity.setIssues(write(feedback.issues()));
            entity.setSuggestions(write(feedback.suggestions()));
            entity.setLimitations(write(feedback.limitations()));
            entity.setIsMock(feedback.mock());
            entity.setCreatedAt(now);
            feedbackMapper.insert(entity);

            Submission submission = submissionMapper.selectById(task.getSubmissionId());
            if (submission != null) {
                submission.setReviewState("GRADED");
                submissionMapper.updateById(submission);
            }
            task.setState("SUCCEEDED");
            task.setSafeErrorCode(null);
            task.setLeaseToken(null);
            task.setLeaseUntil(null);
            task.setUpdatedAt(now);
            taskMapper.updateById(task);
            slotMapper.release(task.getUserId(), task.getId(), now);
            return true;
        });
        if (Boolean.TRUE.equals(applied)) {
            log.info("AI task {} succeeded", claim.taskId());
        }
    }

    private void fail(Claim claim, String code) {
        transactionTemplate.executeWithoutResult(status -> {
            AiTask task = taskMapper.selectForUpdate(claim.taskId());
            if (!owned(task, claim)) {
                log.warn("AI task {} failure rejected: lease no longer owned", claim.taskId());
                return;
            }
            failLocked(task, code, now());
        });
    }

    private void recoverDeadlines() {
        LocalDateTime now = now();
        for (Long taskId : taskMapper.selectExpiredDeadlineIds(now, 50)) {
            transactionTemplate.executeWithoutResult(status -> {
                AiTask task = taskMapper.selectForUpdate(taskId);
                if (task == null || terminal(task.getState()) || task.getDeadlineAt().isAfter(now)) {
                    return;
                }
                log.warn("AI task {} exceeded 10 minute deadline; releasing slot", taskId);
                failLocked(task, "AI_TIMEOUT", now);
            });
        }
    }

    /**
     * 进程崩溃后租约自然过期；重新排队前检查出站调用预算，PLANNED 调用也计入已发生。
     */
    private void recoverExpiredLeases() {
        LocalDateTime now = now();
        for (Long taskId : taskMapper.selectExpiredLeaseIds(now, 50)) {
            transactionTemplate.executeWithoutResult(status -> {
                AiTask task = taskMapper.selectForUpdate(taskId);
                if (task == null || !"RUNNING".equals(task.getState())
                        || task.getLeaseUntil() == null || !task.getLeaseUntil().isBefore(now)) {
                    return;
                }
                int calls = Math.max(attemptMapper.countByTask(taskId),
                        task.getCallCount() == null ? 0 : task.getCallCount());
                if (calls >= maxCalls) {
                    log.warn("AI task {} lease expired with exhausted call budget", taskId);
                    failLocked(task, "AI_UNAVAILABLE", now);
                } else {
                    log.warn("AI task {} lease expired; requeueing", taskId);
                    taskMapper.requeueExpiredLease(taskId, now);
                }
            });
        }
    }

    private void failLocked(AiTask task, String code, LocalDateTime now) {
        task.setState("FAILED");
        task.setSafeErrorCode(code);
        task.setLeaseToken(null);
        task.setLeaseUntil(null);
        task.setUpdatedAt(now);
        taskMapper.updateById(task);
        Submission submission = submissionMapper.selectById(task.getSubmissionId());
        if (submission != null) {
            submission.setReviewState("FAILED");
            submissionMapper.updateById(submission);
        }
        slotMapper.release(task.getUserId(), task.getId(), now);
    }

    private boolean owned(AiTask task, Claim claim) {
        return task != null && "RUNNING".equals(task.getState())
                && claim.token().equals(task.getLeaseToken());
    }

    private boolean terminal(String state) {
        return "SUCCEEDED".equals(state) || "FAILED".equals(state);
    }

    private String write(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("AI feedback serialization failed", e);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
        leaseScheduler.shutdownNow();
    }

    private record Claim(Long taskId, String token) {
    }

    private final class LeaseKeeper implements AutoCloseable {

        private final Claim claim;
        private final AtomicBoolean lost = new AtomicBoolean();
        private final ScheduledFuture<?> future;

        private LeaseKeeper(Claim claim) {
            this.claim = claim;
            this.future = leaseScheduler.scheduleAtFixedRate(this::renew, renewMs, renewMs, TimeUnit.MILLISECONDS);
        }

        private void renew() {
            if (lost.get()) {
                return;
            }
            try {
                LocalDateTime now = now();
                int updated = taskMapper.renewLease(claim.taskId(), claim.token(),
                        now.plusSeconds(leaseSeconds), now);
                if (updated == 0) {
                    lost.set(true);
                    log.warn("AI task {} lease lost; result will be discarded", claim.taskId());
                }
            } catch (RuntimeException e) {
                log.warn("AI task {} lease renewal failed: {}", claim.taskId(), e.getClass().getSimpleName());
            }
        }

        private boolean lost() {
            return lost.get();
        }

        @Override
        public void close() {
            future.cancel(false);
        }
    }
}