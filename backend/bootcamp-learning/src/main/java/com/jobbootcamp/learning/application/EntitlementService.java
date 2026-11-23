package com.jobbootcamp.learning.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.contracts.internal.FreeClaimCommand;
import com.jobbootcamp.contracts.internal.FreeClaimResult;
import com.jobbootcamp.learning.domain.CourseGrant;
import com.jobbootcamp.learning.domain.EntitlementSource;
import com.jobbootcamp.learning.domain.FreeClaimReceipt;
import com.jobbootcamp.learning.domain.UserGuard;
import com.jobbootcamp.contracts.internal.AccessCheckResponse;
import com.jobbootcamp.contracts.internal.BenefitAccessCheckRequest;
import com.jobbootcamp.contracts.internal.BenefitAccessCheckResponse;
import com.jobbootcamp.contracts.internal.PurchasePrepareResponse;
import com.jobbootcamp.contracts.internal.SourceSelectionItem;
import com.jobbootcamp.contracts.internal.SourceSelectionResponse;
import com.jobbootcamp.learning.domain.PurchaseHold;
import com.jobbootcamp.learning.domain.RouteEpochProjection;
import com.jobbootcamp.learning.domain.RouteEntitlement;
import com.jobbootcamp.learning.domain.SourceSelectionRow;
import com.jobbootcamp.learning.domain.dto.MyCourseItem;
import com.jobbootcamp.learning.domain.dto.MyCourseRow;
import com.jobbootcamp.learning.domain.dto.MyRouteItem;
import com.jobbootcamp.learning.domain.dto.PageResponse;
import com.jobbootcamp.learning.infrastructure.CourseGrantMapper;
import com.jobbootcamp.learning.infrastructure.CourseSourceMapper;
import com.jobbootcamp.learning.infrastructure.BenefitAccessMapper;
import com.jobbootcamp.learning.infrastructure.EntitlementQueryMapper;
import com.jobbootcamp.learning.infrastructure.EntitlementSourceMapper;
import com.jobbootcamp.learning.infrastructure.FreeClaimReceiptMapper;
import com.jobbootcamp.learning.infrastructure.GrantRouteMemberMapper;
import com.jobbootcamp.learning.infrastructure.PurchaseHoldMapper;
import com.jobbootcamp.learning.infrastructure.RouteEpochProjectionMapper;
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
    private final CourseSourceMapper sourceSelectionMapper;
    private final PurchaseHoldMapper purchaseHoldMapper;
    private final GrantRouteMemberMapper routeMemberMapper;
    private final RouteEpochProjectionMapper epochProjectionMapper;
    private final BenefitAccessMapper benefitAccessMapper;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate rowTemplate;

    public EntitlementService(UserGuardMapper guardMapper,
                              EntitlementSourceMapper sourceMapper,
                              CourseGrantMapper grantMapper,
                              RouteEntitlementMapper routeMapper,
                              FreeClaimReceiptMapper receiptMapper,
                              EntitlementQueryMapper queryMapper,
                              CourseSourceMapper sourceSelectionMapper,
                              PurchaseHoldMapper purchaseHoldMapper,
                              GrantRouteMemberMapper routeMemberMapper,
                              RouteEpochProjectionMapper epochProjectionMapper,
                              BenefitAccessMapper benefitAccessMapper,
                              PlatformTransactionManager transactionManager) {
        this.guardMapper = guardMapper;
        this.sourceMapper = sourceMapper;
        this.grantMapper = grantMapper;
        this.routeMapper = routeMapper;
        this.receiptMapper = receiptMapper;
        this.queryMapper = queryMapper;
        this.sourceSelectionMapper = sourceSelectionMapper;
        this.purchaseHoldMapper = purchaseHoldMapper;
        this.routeMemberMapper = routeMemberMapper;
        this.epochProjectionMapper = epochProjectionMapper;
        this.benefitAccessMapper = benefitAccessMapper;
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

    /**
     * 福利题双权益：课程有效 grant + 至少一条路线在题目绑定 epoch 上的有效资格。
     * 历史 epoch 的 grant 保留，路线移除课程后老学员仍可访问；退款冻结来源后资格立即失效。
     */
    public BenefitAccessCheckResponse checkBenefitAccess(long userId, long courseId,
                                                         List<BenefitAccessCheckRequest.RouteEpoch> routes) {
        AccessCheckResponse course = checkAccess(userId, courseId);
        if (!course.allowed() || routes == null) {
            return new BenefitAccessCheckResponse(false, course.allowed(), null, null, null);
        }
        for (BenefitAccessCheckRequest.RouteEpoch route : routes) {
            if (route == null || route.routeId() == null || route.epochId() == null) {
                continue;
            }
            Long sourceId = benefitAccessMapper.selectBenefitSource(
                    userId, courseId, route.routeId(), route.epochId());
            if (sourceId != null) {
                return new BenefitAccessCheckResponse(true, true, route.routeId(), route.epochId(), sourceId);
            }
        }
        return new BenefitAccessCheckResponse(false, true, null, null, null);
    }

    /**
     * 报价只读选择：按课程 grant 的 grantedAt、grantId 最早排序，不建立任何预留。
     */
    public SourceSelectionResponse selectSources(Long userId, List<Long> courseIds) {
        return new SourceSelectionResponse(selectActiveSources(userId, courseIds).values().stream()
                .map(this::toSourceItem)
                .toList());
    }

    /**
     * 下单预留：在 user_guard 内重新选来源并建立 PURCHASED hold。
     * 已存在的 hold 不换来源；释放/提交由订单生命周期驱动。
     */
    public PurchasePrepareResponse preparePurchase(Long orderId, Long userId, List<Long> courseIds) {
        if (orderId == null || orderId <= 0 || userId == null || userId <= 0
                || courseIds == null || courseIds.isEmpty()) {
            throw new com.jobbootcamp.common.error.BusinessException(
                    com.jobbootcamp.common.error.ErrorCode.VALIDATION_FAILED, "购买预留参数不合法");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        rowTemplate.executeWithoutResult(status -> guardMapper.insertIfAbsent(userId, now));
        return transactionTemplate.execute(status -> {
            UserGuard guard = guardMapper.selectForUpdate(userId);
            List<SourceSelectionRow> rows = sourceSelectionMapper.selectActiveSources(userId, courseIds);
            Map<Long, SourceSelectionRow> earliest = earliestByCourse(rows);
            for (SourceSelectionRow row : earliest.values()) {
                purchaseHoldMapper.insertIfAbsent(orderId, userId, row.getCourseId(),
                        row.getSourceId(), row.getSourceGrantId(), now);
            }
            Map<Long, SourceSelectionRow> bySource = new LinkedHashMap<>();
            for (SourceSelectionRow row : rows) {
                bySource.putIfAbsent(row.getSourceId(), row);
            }
            List<SourceSelectionItem> holds = purchaseHoldMapper.selectByOrder(orderId).stream()
                    .map(hold -> toSourceItem(hold, bySource.get(hold.getSourceId())))
                    .toList();
            return new PurchasePrepareResponse(orderId, guard.getSequence(), holds);
        });
    }

    public void commitPurchase(Long orderId) {
        purchaseHoldMapper.updateState(orderId, "PREPARED", "COMMITTED", LocalDateTime.now(ZoneOffset.UTC));
    }

    public void releasePurchase(Long orderId) {
        purchaseHoldMapper.updateState(orderId, "PREPARED", "RELEASED", LocalDateTime.now(ZoneOffset.UTC));
    }

    private Map<Long, SourceSelectionRow> selectActiveSources(Long userId, List<Long> courseIds) {
        if (userId == null || userId <= 0 || courseIds == null || courseIds.isEmpty()) {
            return Map.of();
        }
        return earliestByCourse(sourceSelectionMapper.selectActiveSources(userId, courseIds));
    }

    private Map<Long, SourceSelectionRow> earliestByCourse(List<SourceSelectionRow> rows) {
        Map<Long, SourceSelectionRow> earliest = new LinkedHashMap<>();
        for (SourceSelectionRow row : rows) {
            earliest.putIfAbsent(row.getCourseId(), row);
        }
        return earliest;
    }

    private SourceSelectionItem toSourceItem(SourceSelectionRow row) {
        return new SourceSelectionItem(row.getCourseId(), row.getSourceId(), row.getSourceGrantId(),
                row.getSourceType(), row.getSourceOrderId(),
                row.getGrantedAt() == null ? null : row.getGrantedAt().toInstant(ZoneOffset.UTC).toString());
    }

    private SourceSelectionItem toSourceItem(PurchaseHold hold, SourceSelectionRow row) {
        return new SourceSelectionItem(hold.getCourseId(), hold.getSourceId(), hold.getSourceGrantId(),
                row == null ? null : row.getSourceType(),
                row == null ? null : row.getSourceOrderId(),
                row == null || row.getGrantedAt() == null ? null
                        : row.getGrantedAt().toInstant(ZoneOffset.UTC).toString());
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

    /**
     * 路线支付成功：建立 ROUTE_ORDER 来源和路线资格；已抵扣课程不新增路线 grant，
     * 新购付费课和免费课分别建立课程来源，所有合法成员记录一条 epoch 资格。
     */
    public PurchaseGrantResult grantRoutePurchase(Long orderId, Long userId, LocalDateTime paidAt,
                                                  Long routeId, List<RoutePurchaseMember> members) {
        if (orderId == null || orderId <= 0 || userId == null || userId <= 0
                || routeId == null || routeId <= 0 || paidAt == null
                || members == null || members.isEmpty()) {
            throw new com.jobbootcamp.common.error.BusinessException(
                    com.jobbootcamp.common.error.ErrorCode.VALIDATION_FAILED, "路线授予命令参数不合法");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        rowTemplate.executeWithoutResult(status -> guardMapper.insertIfAbsent(userId, now));
        return transactionTemplate.execute(status -> {
            UserGuard guard = guardMapper.selectForUpdate(userId);
            EntitlementSource source = findRouteSource(userId, orderId);
            if (source == null) {
                source = new EntitlementSource();
                source.setUserId(userId);
                source.setType("ROUTE_ORDER");
                source.setSourceKey(String.valueOf(orderId));
                source.setOrderId(orderId);
                source.setRouteId(routeId);
                source.setAcquiredAt(paidAt);
                source.setPaidAt(paidAt);
                source.setState("ACTIVE");
                source.setLifecycleVersion(1L);
                source.setCreatedAt(now);
                source.setUpdatedAt(now);
                sourceMapper.insert(source);
            }
            RouteEntitlement routeEntitlement = routeMapper.selectOne(Wrappers.<RouteEntitlement>lambdaQuery()
                    .eq(RouteEntitlement::getSourceId, source.getId())
                    .eq(RouteEntitlement::getRouteId, routeId));
            if (routeEntitlement == null) {
                RouteEntitlement created = new RouteEntitlement();
                created.setUserId(userId);
                created.setRouteId(routeId);
                created.setSourceId(source.getId());
                created.setFutureCourses(true);
                created.setCreatedAt(now);
                routeMapper.insert(created);
            }
            List<PurchaseGrantResult.Grant> grants = new ArrayList<>();
            for (RoutePurchaseMember member : members) {
                if (member.epochId() != null) {
                    routeMemberMapper.insertIfAbsent(source.getId(), member.epochId(), member.courseId(),
                            paidAt, now);
                }
                if (member.free()) {
                    ensureFreeGrant(userId, member.courseId(), paidAt, guard, now);
                } else if (member.newPaid()) {
                    CourseGrant grant = ensureGrant(userId, member.courseId(), source, paidAt, guard, now);
                    grants.add(new PurchaseGrantResult.Grant(member.courseId(), grant.getId()));
                }
            }
            return new PurchaseGrantResult(source.getId(), grants);
        });
    }

    /**
     * 消费 course.route-changed：记录 epoch 投影，已有路线资格用户补发新增课程；
     * 移除只关闭投影，不撤销已授予课程（BUY-07）。
     */
    public void applyRouteChange(Long routeId, Long version, List<RouteEpochChange> added,
                                 List<Long> removedEpochIds, LocalDateTime eventAt) {
        if (routeId == null || version == null || added == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        transactionTemplate.executeWithoutResult(status -> {
            if (removedEpochIds != null) {
                for (Long epochId : removedEpochIds) {
                    epochProjectionMapper.close(epochId, eventAt, version, now);
                }
            }
            for (RouteEpochChange change : added) {
                if (change.epochId() == null || change.courseId() == null) {
                    continue;
                }
                epochProjectionMapper.upsert(change.epochId(), routeId, change.courseId(),
                        change.joinedAt() == null ? eventAt : change.joinedAt(),
                        change.routeVersion(), change.free(), now);
                for (RouteEntitlement entitlement : routeMapper.selectActiveByRoute(routeId)) {
                    EntitlementSource source = sourceMapper.selectById(entitlement.getSourceId());
                    if (source == null || !"ACTIVE".equals(source.getState())) {
                        continue;
                    }
                    UserGuard guard = guardMapper.selectForUpdate(entitlement.getUserId());
                    LocalDateTime qualifiedAt = change.joinedAt() == null ? eventAt : change.joinedAt();
                    routeMemberMapper.insertIfAbsent(source.getId(), change.epochId(), change.courseId(),
                            qualifiedAt, now);
                    if (change.free()) {
                        ensureFreeGrant(entitlement.getUserId(), change.courseId(), qualifiedAt, guard, now);
                    } else {
                        ensureGrant(entitlement.getUserId(), change.courseId(), source, qualifiedAt, guard, now);
                    }
                }
            }
        });
    }

    private EntitlementSource findRouteSource(Long userId, Long orderId) {
        return sourceMapper.selectOne(Wrappers.<EntitlementSource>lambdaQuery()
                .eq(EntitlementSource::getUserId, userId)
                .eq(EntitlementSource::getType, "ROUTE_ORDER")
                .eq(EntitlementSource::getSourceKey, String.valueOf(orderId)));
    }

    private CourseGrant ensureGrant(Long userId, Long courseId, EntitlementSource source,
                                    LocalDateTime grantedAt, UserGuard guard, LocalDateTime now) {
        CourseGrant grant = grantMapper.selectOne(Wrappers.<CourseGrant>lambdaQuery()
                .eq(CourseGrant::getSourceId, source.getId())
                .eq(CourseGrant::getCourseId, courseId));
        if (grant != null) {
            return grant;
        }
        long sequence = guard.getSequence() + 1;
        guardMapper.updateSequence(userId, sequence, now);
        guard.setSequence(sequence);
        CourseGrant created = new CourseGrant();
        created.setUserId(userId);
        created.setCourseId(courseId);
        created.setSourceId(source.getId());
        created.setGrantedAt(grantedAt);
        created.setGrantSequence(sequence);
        grantMapper.insert(created);
        return created;
    }

    private CourseGrant ensureFreeGrant(Long userId, Long courseId, LocalDateTime acquiredAt,
                                        UserGuard guard, LocalDateTime now) {
        EntitlementSource free = sourceMapper.selectOne(Wrappers.<EntitlementSource>lambdaQuery()
                .eq(EntitlementSource::getUserId, userId)
                .eq(EntitlementSource::getType, "FREE")
                .eq(EntitlementSource::getSourceKey, String.valueOf(courseId)));
        if (free == null) {
            long sequence = guard.getSequence() + 1;
            guardMapper.updateSequence(userId, sequence, now);
            guard.setSequence(sequence);
            free = new EntitlementSource();
            free.setUserId(userId);
            free.setType("FREE");
            free.setSourceKey(String.valueOf(courseId));
            free.setAcquiredAt(acquiredAt);
            free.setState("ACTIVE");
            free.setLifecycleVersion(1L);
            free.setCreatedAt(now);
            free.setUpdatedAt(now);
            sourceMapper.insert(free);
        }
        return ensureGrant(userId, courseId, free, acquiredAt, guard, now);
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