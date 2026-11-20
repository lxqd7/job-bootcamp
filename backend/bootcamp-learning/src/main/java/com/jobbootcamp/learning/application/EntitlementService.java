package com.jobbootcamp.learning.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.contracts.internal.FreeClaimCommand;
import com.jobbootcamp.contracts.internal.FreeClaimResult;
import com.jobbootcamp.learning.domain.CourseGrant;
import com.jobbootcamp.learning.domain.EntitlementSource;
import com.jobbootcamp.learning.domain.FreeClaimReceipt;
import com.jobbootcamp.learning.domain.UserGuard;
import com.jobbootcamp.learning.domain.dto.AccessCheckResponse;
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

    private <T> PageResponse<T> page(List<T> all, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        int from = Math.min(all.size(), (safePage - 1) * safeSize);
        int to = Math.min(all.size(), from + safeSize);
        return new PageResponse<>(all.subList(from, to), all.size(), safePage, safeSize);
    }
}