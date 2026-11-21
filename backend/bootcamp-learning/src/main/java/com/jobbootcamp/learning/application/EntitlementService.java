package com.jobbootcamp.learning.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.contracts.internal.FreeClaimCommand;
import com.jobbootcamp.contracts.internal.FreeClaimResult;
import com.jobbootcamp.learning.domain.CourseGrant;
import com.jobbootcamp.learning.domain.EntitlementSource;
import com.jobbootcamp.learning.domain.FreeClaimReceipt;
import com.jobbootcamp.learning.domain.UserGuard;
import com.jobbootcamp.contracts.internal.AccessCheckResponse;
import com.jobbootcamp.learning.domain.dto.MyCourseItem;
import com.jobbootcamp.learning.domain.dto.MyCourseRow;
import com.jobbootcamp.learning.domain.dto.MyRouteItem;
import com.jobbootcamp.learning.domain.dto.PageResponse;
import com.jobbootcamp.learning.infrastructure.CourseGrantMapper;
import com.jobbootcamp.learning.infrastructure.EntitlementQueryMapper;
import com.jobbootcamp.learning.infrastructure.EntitlementSourceMapper;
import com.jobbootcamp.learning.infrastructure.FreeClaimReceiptMapper;
import com.jobbootcamp.learning.infrastructure.RouteEntitlementMapper;
import com.jobbootcamp.learning.infrastructure.UserGuardMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习权益来源账本。来源按 (user,type,sourceKey) 唯一，课程 grant 按 (source,course) 唯一；
 * 所有授予/重放都先锁 user_guard，保证同一用户的 sequence 和来源状态线性推进。
 */
@Service
public class EntitlementService {

    private final UserGuardMapper guardMapper;
    private final EntitlementSourceMapper sourceMapper;
    private final CourseGrantMapper grantMapper;
    private final RouteEntitlementMapper routeMapper;
    private final FreeClaimReceiptMapper receiptMapper;
    private final EntitlementQueryMapper queryMapper;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate rowTemplate;

    public EntitlementService(UserGuardMapper guardMapper,
                              EntitlementSourceMapper sourceMapper,
                              CourseGrantMapper grantMapper,
                              RouteEntitlementMapper routeMapper,
                              FreeClaimReceiptMapper receiptMapper,
                              EntitlementQueryMapper queryMapper,
                              PlatformTransactionManager transactionManager) {
        this.guardMapper = guardMapper;
        this.sourceMapper = sourceMapper;
        this.grantMapper = grantMapper;
        this.routeMapper = routeMapper;
        this.receiptMapper = receiptMapper;
        this.queryMapper = queryMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.rowTemplate = new TransactionTemplate(transactionManager);
        this.rowTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public FreeClaimResult grantFreeClaim(FreeClaimCommand command) {
        if (command.claimId() == null || command.claimId().isBlank()
                || command.userId() == null || command.userId() <= 0
                || command.courseId() == null || command.courseId() <= 0
                || command.courseVersion() == null || command.acceptedAt() == null) {
            throw new com.jobbootcamp.common.error.BusinessException(
                    com.jobbootcamp.common.error.ErrorCode.VALIDATION_FAILED, "免费领取命令参数不合法");
        }
        FreeClaimReceipt existing = receiptMapper.selectById(command.claimId());
        if (existing != null) {
            return new FreeClaimResult(command.claimId(), String.valueOf(command.courseId()),
                    String.valueOf(existing.getSourceId()), "FULFILLED", true);
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        rowTemplate.executeWithoutResult(status -> guardMapper.insertIfAbsent(command.userId(), now));
        return transactionTemplate.execute(status -> grantInTransaction(command, now));
    }

    private FreeClaimResult grantInTransaction(FreeClaimCommand command, LocalDateTime now) {
        UserGuard guard = guardMapper.selectForUpdate(command.userId());
        FreeClaimReceipt receipt = receiptMapper.selectById(command.claimId());
        if (receipt != null) {
            return new FreeClaimResult(command.claimId(), String.valueOf(command.courseId()),
                    String.valueOf(receipt.getSourceId()), "FULFILLED", true);
        }
        String sourceKey = String.valueOf(command.courseId());
        EntitlementSource source = sourceMapper.selectOne(Wrappers.<EntitlementSource>lambdaQuery()
                .eq(EntitlementSource::getUserId, command.userId())
                .eq(EntitlementSource::getType, "FREE")
                .eq(EntitlementSource::getSourceKey, sourceKey));
        boolean alreadyGranted = source != null;
        LocalDateTime acceptedAt = LocalDateTime.ofInstant(command.acceptedAt(), ZoneOffset.UTC);
        if (source == null) {
            long sequence = guard.getSequence() + 1;
            guardMapper.updateSequence(command.userId(), sequence, now);
            source = new EntitlementSource();
            source.setUserId(command.userId());
            source.setType("FREE");
            source.setSourceKey(sourceKey);
            source.setAcquiredAt(acceptedAt);
            source.setState("ACTIVE");
            source.setLifecycleVersion(1L);
            source.setCreatedAt(now);
            source.setUpdatedAt(now);
            sourceMapper.insert(source);

            CourseGrant grant = new CourseGrant();
            grant.setUserId(command.userId());
            grant.setCourseId(command.courseId());
            grant.setSourceId(source.getId());
            grant.setGrantedAt(acceptedAt);
            grant.setGrantSequence(sequence);
            grantMapper.insert(grant);
        } else {
            CourseGrant grant = grantMapper.selectOne(Wrappers.<CourseGrant>lambdaQuery()
                    .eq(CourseGrant::getSourceId, source.getId())
                    .eq(CourseGrant::getCourseId, command.courseId()));
            if (grant == null) {
                long sequence = guard.getSequence() + 1;
                guardMapper.updateSequence(command.userId(), sequence, now);
                CourseGrant created = new CourseGrant();
                created.setUserId(command.userId());
                created.setCourseId(command.courseId());
                created.setSourceId(source.getId());
                created.setGrantedAt(acceptedAt);
                created.setGrantSequence(sequence);
                grantMapper.insert(created);
            }
        }

        FreeClaimReceipt created = new FreeClaimReceipt();
        created.setClaimId(command.claimId());
        created.setUserId(command.userId());
        created.setCourseId(command.courseId());
        created.setSourceId(source.getId());
        created.setCreatedAt(now);
        receiptMapper.insert(created);
        return new FreeClaimResult(command.claimId(), String.valueOf(command.courseId()),
                String.valueOf(source.getId()), "FULFILLED", alreadyGranted);
    }

    public PageResponse<MyCourseItem> myCourses(long userId, int page, int pageSize) {
        Map<Long, MyCourseItem> unique = new LinkedHashMap<>();
        for (MyCourseRow row : queryMapper.selectActiveCourses(userId)) {
            unique.putIfAbsent(row.getCourseId(), new MyCourseItem(
                    String.valueOf(row.getCourseId()), String.valueOf(row.getSourceId()),
                    row.getSourceType(), row.getGrantedAt()));
        }
        return page(new ArrayList<>(unique.values()), page, pageSize);
    }

    public PageResponse<MyRouteItem> myRoutes(long userId, int page, int pageSize) {
        List<MyRouteItem> items = queryMapper.selectActiveRoutes(userId).stream()
                .map(row -> new MyRouteItem(String.valueOf(row.getRouteId()),
                        String.valueOf(row.getSourceId()),
                        Boolean.TRUE.equals(row.getFutureCourses()), row.getAcquiredAt()))
                .toList();
        return page(items, page, pageSize);
    }

    public AccessCheckResponse checkAccess(long userId, long courseId) {
        Long sourceId = grantMapper.selectActiveSource(userId, courseId);
        return new AccessCheckResponse(sourceId != null,
                sourceId == null ? null : String.valueOf(sourceId));
    }

    public PurchaseGrantResult grantPurchase(Long orderId, Long userId, LocalDateTime paidAt,
                                             List<PurchaseCourse> courses) {
        if (orderId == null || orderId <= 0 || userId == null || userId <= 0
                || paidAt == null || courses == null || courses.isEmpty()) {
            throw new com.jobbootcamp.common.error.BusinessException(
                    com.jobbootcamp.common.error.ErrorCode.VALIDATION_FAILED, "付费授予命令参数不合法");
        }
        // rowTemplate 先独立提交 guard 行，主事务再做来源账本更新，避免首次并发创建死锁。
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        rowTemplate.executeWithoutResult(status -> guardMapper.insertIfAbsent(userId, now));
        return transactionTemplate.execute(status -> grantPurchaseInTransaction(
                orderId, userId, paidAt, courses, now));
    }

    private PurchaseGrantResult grantPurchaseInTransaction(Long orderId, Long userId,
                                                           LocalDateTime paidAt,
                                                           List<PurchaseCourse> courses,
                                                           LocalDateTime now) {
        UserGuard guard = guardMapper.selectForUpdate(userId);
        long sequence = guard.getSequence();
        String sourceKey = String.valueOf(orderId);
        EntitlementSource source = sourceMapper.selectOne(Wrappers.<EntitlementSource>lambdaQuery()
                .eq(EntitlementSource::getUserId, userId)
                .eq(EntitlementSource::getType, "COURSE_ORDER")
                .eq(EntitlementSource::getSourceKey, sourceKey));
        if (source == null) {
            guardMapper.updateSequence(userId, ++sequence, now);
            source = new EntitlementSource();
            source.setUserId(userId);
            source.setType("COURSE_ORDER");
            source.setSourceKey(sourceKey);
            source.setOrderId(orderId);
            source.setAcquiredAt(paidAt);
            source.setPaidAt(paidAt);
            source.setState("ACTIVE");
            source.setLifecycleVersion(1L);
            source.setCreatedAt(now);
            source.setUpdatedAt(now);
            sourceMapper.insert(source);
        }
        List<PurchaseGrantResult.Grant> grants = new ArrayList<>();
        for (PurchaseCourse course : courses) {
            if (course.courseId() == null || course.courseId() <= 0) {
                continue;
            }
            CourseGrant grant = grantMapper.selectOne(Wrappers.<CourseGrant>lambdaQuery()
                    .eq(CourseGrant::getSourceId, source.getId())
                    .eq(CourseGrant::getCourseId, course.courseId()));
            if (grant == null && "ACTIVE".equals(source.getState())) {
                guardMapper.updateSequence(userId, ++sequence, now);
                CourseGrant created = new CourseGrant();
                created.setUserId(userId);
                created.setCourseId(course.courseId());
                created.setSourceId(source.getId());
                created.setGrantedAt(paidAt);
                created.setGrantSequence(sequence);
                grantMapper.insert(created);
                grant = created;
            }
            if (grant != null) {
                grants.add(new PurchaseGrantResult.Grant(course.courseId(), grant.getId()));
            }
        }
        return new PurchaseGrantResult(source.getId(), grants);
    }

    private <T> PageResponse<T> page(List<T> all, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        int from = Math.min(all.size(), (safePage - 1) * safeSize);
        int to = Math.min(all.size(), from + safeSize);
        return new PageResponse<>(all.subList(from, to), all.size(), safePage, safeSize);
    }
}