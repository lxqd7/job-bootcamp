package com.jobbootcamp.trade.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ErrorData;
import com.jobbootcamp.contracts.internal.AccessCheckResponse;
import com.jobbootcamp.contracts.internal.CouponLockResponse;
import com.jobbootcamp.contracts.internal.ProductSnapshotResponse;
import com.jobbootcamp.contracts.internal.PurchasePrepareResponse;
import com.jobbootcamp.contracts.internal.RouteSnapshotResponse;
import com.jobbootcamp.contracts.internal.SourceSelectionItem;
import com.jobbootcamp.contracts.internal.SourceSelectionResponse;
import com.jobbootcamp.trade.domain.DeductionLink;
import com.jobbootcamp.trade.domain.OrderCourse;
import com.jobbootcamp.trade.domain.OrderCoupon;
import com.jobbootcamp.trade.domain.PriceQuote;
import com.jobbootcamp.trade.domain.TradeOrder;
import com.jobbootcamp.trade.domain.dto.CreateOrderRequest;
import com.jobbootcamp.trade.domain.dto.OrderResponse;
import com.jobbootcamp.trade.domain.dto.PageResponse;
import com.jobbootcamp.trade.domain.dto.QuoteResponse;
import com.jobbootcamp.trade.infrastructure.DeductionLinkMapper;
import com.jobbootcamp.trade.infrastructure.OrderCouponMapper;
import com.jobbootcamp.trade.infrastructure.OrderCourseMapper;
import com.jobbootcamp.trade.infrastructure.OrderLessonMapper;
import com.jobbootcamp.trade.infrastructure.PendingCourseMapper;
import com.jobbootcamp.trade.infrastructure.PriceQuoteMapper;
import com.jobbootcamp.trade.infrastructure.TradeGuardMapper;
import com.jobbootcamp.trade.infrastructure.TradeOrderMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 订单写模型。下单只信服务端报价快照，创建时重取商品/权益并重算 hash；
 * 同一用户同课程用 pending_course 占锁，request_key 唯一键和 api_idempotency 共同保证重试不重复建单。
 */
@Service
public class OrderService {

    private static final long ORDER_TTL_SECONDS = 1800;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final TradeOrderMapper orderMapper;
    private final OrderCourseMapper orderCourseMapper;
    private final OrderLessonMapper orderLessonMapper;
    private final PriceQuoteMapper quoteMapper;
    private final PendingCourseMapper pendingCourseMapper;
    private final TradeGuardMapper guardMapper;
    private final IdempotencyService idempotencyService;
    private final QuoteStatusService quoteStatusService;
    private final CourseClient courseClient;
    private final LearningClient learningClient;
    private final CouponClient couponClient;
    private final DeductionLinkMapper deductionLinkMapper;
    private final OrderCouponMapper orderCouponMapper;
    private final TransactionTemplate transactionTemplate;

    public OrderService(TradeOrderMapper orderMapper,
                        OrderCourseMapper orderCourseMapper,
                        OrderLessonMapper orderLessonMapper,
                        PriceQuoteMapper quoteMapper,
                        PendingCourseMapper pendingCourseMapper,
                        TradeGuardMapper guardMapper,
                        IdempotencyService idempotencyService,
                        QuoteStatusService quoteStatusService,
                        CourseClient courseClient,
                        LearningClient learningClient,
                        CouponClient couponClient,
                        DeductionLinkMapper deductionLinkMapper,
                        OrderCouponMapper orderCouponMapper,
                        PlatformTransactionManager transactionManager) {
        this.orderMapper = orderMapper;
        this.orderCourseMapper = orderCourseMapper;
        this.orderLessonMapper = orderLessonMapper;
        this.quoteMapper = quoteMapper;
        this.pendingCourseMapper = pendingCourseMapper;
        this.guardMapper = guardMapper;
        this.idempotencyService = idempotencyService;
        this.quoteStatusService = quoteStatusService;
        this.courseClient = courseClient;
        this.learningClient = learningClient;
        this.couponClient = couponClient;
        this.deductionLinkMapper = deductionLinkMapper;
        this.orderCouponMapper = orderCouponMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public OrderResponse create(Long userId, String idempotencyHeader, CreateOrderRequest request) {
        String requestKey = requestKey(idempotencyHeader, request.quoteId());
        List<Long> couponIds = request.selectedCouponIds() == null ? List.of() : request.selectedCouponIds();
        String requestHash = QuoteHasher.requestHash(request.quoteId(), request.quoteHash(), couponIds);

        TradeOrder existing = findByRequestKey(userId, requestKey);
        if (existing != null) {
            return existingResponse(existing, requestHash);
        }

        IdempotencyService.Claim claim = idempotencyService.claim(userId, requestKey, requestHash);
        if (!claim.owner()) {
            TradeOrder completed = findByRequestKey(userId, requestKey);
            if (completed == null) {
                throw new BusinessException(ErrorCode.ORDER_SYNCING, "订单创建结果同步中，请稍后重试");
            }
            return existingResponse(completed, requestHash);
        }

        OrderResponse response;
        try {
            response = transactionTemplate.execute(status ->
                    createInTransaction(userId, requestKey, requestHash, request));
        } catch (RuntimeException e) {
            idempotencyService.fail(claim.recordId());
            throw e;
        }
        if (response == null) {
            idempotencyService.fail(claim.recordId());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "订单创建未产生结果");
        }
        idempotencyService.complete(claim.recordId(), response.id());
        return response;
    }

    public OrderResponse get(Long userId, Long orderId) {
        TradeOrder order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return toResponse(order);
    }

    public PageResponse<OrderResponse> list(Long userId, String state, int page, int pageSize) {
        long pageNumber = Math.max(1, page);
        long size = Math.min(100, Math.max(1, pageSize));
        IPage<TradeOrder> result = orderMapper.selectPage(new Page<>(pageNumber, size),
                Wrappers.<TradeOrder>lambdaQuery()
                        .eq(TradeOrder::getUserId, userId)
                        .eq(state != null && !state.isBlank(), TradeOrder::getState, state)
                        .orderByDesc(TradeOrder::getCreatedAt)
                        .orderByDesc(TradeOrder::getId));
        List<OrderResponse> items = result.getRecords().stream().map(this::toResponse).toList();
        return new PageResponse<>(items, result.getTotal(), pageNumber, size);
    }

    public void markGranted(Long orderId, Long userId, Long sourceId, List<GrantAck> grants) {
        // learning 的 grant 回执只更新归因字段；全部课程都有 source 才把 fulfillment 置 DONE。
        transactionTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            TradeOrder order = orderMapper.selectForUpdate(orderId);
            if (order == null || !order.getUserId().equals(userId)) {
                return;
            }
            for (GrantAck grant : grants) {
                orderCourseMapper.updateGrant(orderId, grant.courseId(), sourceId, grant.grantId());
            }
            if (!"PAID".equals(order.getState())) {
                return;
            }
            if (orderCourseMapper.countMissingGrant(orderId) == 0) {
                orderMapper.updateFulfillment(orderId, "DONE", now);
            } else {
                orderMapper.updateFulfillment(orderId, "RUNNING", now);
            }
        });
    }

    public record GrantAck(Long courseId, Long grantId) {
    }

    private OrderResponse createInTransaction(Long userId, String requestKey, String requestHash,
                                              CreateOrderRequest request) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        // 先锁用户交易守卫，再检查待付锁，避免同一用户两个不同请求并发卖同一课程。
        guardMapper.ensure(userId, now);
        guardMapper.lock(userId);

        TradeOrder duplicate = findByRequestKey(userId, requestKey);
        if (duplicate != null) {
            return existingResponse(duplicate, requestHash);
        }

        PriceQuote quote = quoteMapper.selectOne(Wrappers.<PriceQuote>lambdaQuery()
                .eq(PriceQuote::getQuoteId, request.quoteId()));
        if (quote == null || !quote.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.QUOTE_CHANGED);
        }
        if (!"READY".equals(quote.getStatus())) {
            throw new BusinessException(ErrorCode.QUOTE_CHANGED);
        }
        if (!quote.getExpiresAt().isAfter(now)) {
            quoteStatusService.mark(quote.getQuoteId(), "EXPIRED");
            throw new BusinessException(ErrorCode.QUOTE_CHANGED);
        }
        if (!quote.getResultHash().equals(request.quoteHash())) {
            throw new BusinessException(ErrorCode.QUOTE_CHANGED);
        }
        List<Long> couponIds = request.selectedCouponIds() == null ? List.of() : request.selectedCouponIds();
        CouponPlan couponPlan = couponPlan(quote, couponIds);
        if ("ROUTE".equals(quote.getProductType())) {
            return createRouteInTransaction(quote, userId, requestKey, requestHash, couponPlan, now);
        }

        ProductSnapshotResponse snapshot = courseClient.courseSnapshot(quote.getProductId());
        String currentSnapshotHash = QuoteHasher.snapshotHash(snapshot);
        // 报价只是预览：成交前必须用当前商品/价格重算，任何变化都不能静默沿用旧金额。
        String currentBaseHash = QuoteHasher.quoteHash(snapshot, quote.getTotalCent(), quote.getCreditCent(),
                0, quote.getTotalCent() - quote.getCreditCent(), quote.getCouponMode());
        String currentQuoteHash = QuoteHasher.withPricing(currentBaseHash, quote.getPricingHash());
        if (!snapshot.version().equals(quote.getProductVersion())
                || !quote.getResultHash().equals(currentQuoteHash)) {
            quoteStatusService.mark(quote.getQuoteId(), "STALE");
            throw new BusinessException(ErrorCode.QUOTE_CHANGED);
        }
        AccessCheckResponse access = learningClient.checkAccess(userId, quote.getProductId());
        if (access.allowed()) {
            throw new BusinessException(ErrorCode.ALREADY_OWNED, "已拥有该课程",
                    new ErrorData(null, null, String.valueOf(quote.getProductId())));
        }

        Long pendingOrderId = pendingCourseMapper.findOrderId(userId, quote.getProductId());
        if (pendingOrderId != null) {
            throw new BusinessException(ErrorCode.ORDER_CONFLICT, "该课程已有待支付订单",
                    new ErrorData(null, null, String.valueOf(pendingOrderId)));
        }

        String orderNo = "BO" + UUID.randomUUID().toString().replace("-", "");
        TradeOrder order = new TradeOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setProductType(quote.getProductType());
        order.setProductId(quote.getProductId());
        order.setState("CREATING");
        order.setFulfillmentState("PENDING");
        order.setQuoteId(quote.getQuoteId());
        order.setPricingHash(quote.getPricingHash());
        order.setCouponMode(quote.getCouponMode());
        order.setCouponState(couponPlan.lines().isEmpty() ? "NONE" : "PENDING");
        order.setTotalCent(quote.getTotalCent());
        order.setCreditCent(quote.getCreditCent());
        order.setDiscountCent(quote.getDiscountCent());
        order.setPayableCent(quote.getPayableCent());
        order.setCreatedAt(now);
        order.setExpiresAt(now.plusSeconds(ORDER_TTL_SECONDS));
        order.setSnapshotHash(currentSnapshotHash);
        order.setSnapshot(orderSnapshot(snapshot, quote, couponPlan));
        order.setRequestKey(requestKey);
        order.setRequestHash(requestHash);
        order.setUpdatedAt(now);
        boolean couponLocked = false;
        try {
            orderMapper.insert(order);
            insertOrderCoupons(order.getId(), couponPlan, now);

            OrderCourse orderCourse = new OrderCourse();
            orderCourse.setOrderId(order.getId());
            orderCourse.setCourseId(quote.getProductId());
            orderCourse.setCourseVersion(quote.getProductVersion());
            orderCourse.setUnitPriceCent(quote.getUnitPriceCent());
            orderCourse.setAllocatedCent(quote.getTotalCent());
            orderCourse.setCreditCent(0L);
            orderCourse.setCouponCent(couponPlan.discount());
            orderCourse.setPayableCent(quote.getPayableCent());
            orderCourse.setIsFree(false);
            orderCourse.setIsNewPaid(true);
            orderCourse.setCategoryIds(TradeJson.write(snapshot.categoryId() == null
                    ? List.of() : List.of(snapshot.categoryId())));
            orderCourse.setCreatedAt(now);
            orderCourseMapper.insert(orderCourse);

            if (snapshot.lessons() != null) {
                for (ProductSnapshotResponse.SnapshotLesson lesson : snapshot.lessons()) {
                    orderLessonMapper.insert(order.getId(), quote.getProductId(), lesson.lessonId(),
                            lesson.durationMs() == null ? 0L : lesson.durationMs(), now);
                }
            }

            try {
                pendingCourseMapper.insert(userId, quote.getProductId(), order.getId(), now);
            } catch (DuplicateKeyException e) {
                throw new BusinessException(ErrorCode.ORDER_CONFLICT, "该课程已有待支付订单");
            }

            if (!couponPlan.lines().isEmpty()) {
                lockCoupons(order, quote, couponPlan, userId);
                couponLocked = true;
                order.setCouponState("LOCKED");
            }

            order.setState("WAIT_PAY");
            order.setUpdatedAt(now);
            orderMapper.updateById(order);
            quoteMapper.updateStatusFromReady(quote.getQuoteId(), "CONSUMED", now);
            guardMapper.increment(userId, now);
            return toResponse(order);
        } catch (RuntimeException e) {
            if (couponLocked) {
                releaseCouponsQuietly(order.getId(), userId, quote);
            }
            throw e;
        }
    }

    private TradeOrder findByRequestKey(Long userId, String requestKey) {
        return orderMapper.selectOne(Wrappers.<TradeOrder>lambdaQuery()
                .eq(TradeOrder::getUserId, userId)
                .eq(TradeOrder::getRequestKey, requestKey));
    }

    private CouponPlan couponPlan(PriceQuote quote, List<Long> requestedIds) {
        List<QuoteResponse.CouponLine> lines = List.of();
        if (quote.getCouponPlanJson() != null && !quote.getCouponPlanJson().isBlank()) {
            try {
                lines = List.of(JSON.readValue(quote.getCouponPlanJson(), QuoteResponse.CouponLine[].class));
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.DATA_INTEGRITY, "报价优惠计划损坏");
            }
        }
        List<Long> rawRequestIds = requestedIds.stream().filter(Objects::nonNull).toList();
        if (rawRequestIds.size() != rawRequestIds.stream().distinct().count()) {
            throw new BusinessException(ErrorCode.COUPON_COMBINATION_INVALID, "下单优惠券不能重复");
        }
        List<Long> planIds = lines.stream().map(QuoteResponse.CouponLine::userCouponId).sorted().toList();
        List<Long> requestIds = rawRequestIds.stream().distinct().sorted().toList();
        if (!planIds.equals(requestIds)) {
            throw new BusinessException(ErrorCode.QUOTE_CHANGED, "下单优惠券与报价方案不一致，请重新确认");
        }
        if (!lines.isEmpty() && (quote.getPricingHash() == null || quote.getPricingHash().isBlank())) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "带券报价缺少定价指纹");
        }
        long discount = 0;
        Map<Long, Long> allocations = new LinkedHashMap<>();
        for (QuoteResponse.CouponLine line : lines) {
            long allocated = 0;
            List<QuoteResponse.Allocation> lineAllocations = line.allocation() == null
                    ? List.of() : line.allocation();
            for (QuoteResponse.Allocation allocation : lineAllocations) {
                allocations.merge(allocation.courseId(), allocation.discountCent(), Long::sum);
                allocated += allocation.discountCent();
            }
            if (allocated != line.discountCent()) {
                throw new BusinessException(ErrorCode.DATA_INTEGRITY, "优惠券分摊与优惠金额不一致");
            }
            discount += line.discountCent();
        }
        if (discount != quote.getDiscountCent()) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "报价优惠总额与券计划不一致");
        }
        return new CouponPlan(lines, allocations, discount);
    }

    private void insertOrderCoupons(Long orderId, CouponPlan plan, LocalDateTime now) {
        for (QuoteResponse.CouponLine line : plan.lines()) {
            OrderCoupon coupon = new OrderCoupon();
            coupon.setOrderId(orderId);
            coupon.setUserCouponId(line.userCouponId());
            coupon.setApplyOrder(line.applyOrder());
            coupon.setBaseCent(line.baseCent());
            coupon.setDiscountCent(line.discountCent());
            coupon.setAllocationJson(writeAllocation(line.allocation()));
            coupon.setCreatedAt(now);
            orderCouponMapper.insert(coupon);
        }
    }

    private String writeAllocation(List<QuoteResponse.Allocation> allocation) {
        try {
            return JSON.writeValueAsString(allocation == null ? List.of() : allocation);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "优惠分摊快照序列化失败");
        }
    }

    private List<Map<String, Object>> couponPlanMaps(CouponPlan plan) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (QuoteResponse.CouponLine line : plan.lines()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userCouponId", line.userCouponId());
            row.put("applyOrder", line.applyOrder());
            row.put("baseCent", line.baseCent());
            row.put("discountCent", line.discountCent());
            List<Map<String, Object>> allocations = new ArrayList<>();
            for (QuoteResponse.Allocation allocation : line.allocation() == null
                    ? List.<QuoteResponse.Allocation>of() : line.allocation()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("courseId", allocation.courseId());
                item.put("discountCent", allocation.discountCent());
                allocations.add(item);
            }
            row.put("allocation", allocations);
            rows.add(row);
        }
        return rows;
    }

    private void lockCoupons(TradeOrder order, PriceQuote quote, CouponPlan plan, Long userId) {
        CouponLockResponse response = couponClient.prepare(order.getId(), userId, quote.getQuoteId(),
                quote.getPricingHash(), plan.lines().stream().map(QuoteResponse.CouponLine::userCouponId).toList());
        if (response == null || !"LOCKED".equals(response.state())) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "优惠券锁定结果异常");
        }
    }

    private void releaseCouponsQuietly(Long orderId, Long userId, PriceQuote quote) {
        if (orderId == null || quote.getPricingHash() == null || quote.getPricingHash().isBlank()
                || quote.getCouponPlanJson() == null || quote.getCouponPlanJson().isBlank()) {
            return;
        }
        try {
            couponClient.release(orderId, userId, quote.getPricingHash(), "CLOSED");
        } catch (RuntimeException ignored) {
            // 营销对账任务会再次读取订单状态并补齐释放。
        }
    }

    private record CouponPlan(List<QuoteResponse.CouponLine> lines, Map<Long, Long> allocations, long discount) {
    }

    /**
     * 路线订单：报价只预览，成交时重新取路线快照和最早来源；学习 prepare 建立 hold，
     * 已有课程只写抵扣边，不新增来源；全部已拥有时在报价阶段就不允许出现零元订单。
     */
    private OrderResponse createRouteInTransaction(PriceQuote quote, Long userId,
                                                   String requestKey, String requestHash,
                                                   CouponPlan couponPlan, LocalDateTime now) {
        RouteSnapshotResponse route = courseClient.routeSnapshot(quote.getProductId());
        if (!"ONLINE".equals(route.status())
                || !Objects.equals(route.versionNo(), quote.getProductVersion())) {
            quoteStatusService.mark(quote.getQuoteId(), "STALE");
            throw new BusinessException(ErrorCode.QUOTE_CHANGED);
        }
        List<Long> courseIds = route.members().stream()
                .map(RouteSnapshotResponse.Member::courseId).toList();
        SourceSelectionResponse selectedResponse = learningClient.selectSources(userId, courseIds);
        Map<Long, SourceSelectionItem> selected = sourceByCourse(selectedResponse.items());
        List<QuoteResponse.CourseLine> currentLines = routeLines(route, selected);
        long currentCredit = currentLines.stream().mapToLong(QuoteResponse.CourseLine::creditCent).sum();
        long currentPayable = route.priceCent() - currentCredit;
        String currentBaseHash = QuoteHasher.routeQuoteHash(route, currentLines, route.priceCent(),
                currentCredit, 0, currentPayable, quote.getCouponMode());
        String currentHash = QuoteHasher.withPricing(currentBaseHash, quote.getPricingHash());
        if (!quote.getResultHash().equals(currentHash)) {
            quoteStatusService.mark(quote.getQuoteId(), "STALE");
            throw new BusinessException(ErrorCode.QUOTE_CHANGED);
        }
        if (currentPayable <= 0) {
            throw new BusinessException(ErrorCode.ALREADY_OWNED,
                    "路线课程均已拥有，无需创建付费订单",
                    new ErrorData(null, null, String.valueOf(route.routeId())));
        }
        if (currentPayable < 100) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "路线实付低于最低金额");
        }

        List<RouteSnapshotResponse.Member> newPaidMembers = route.members().stream()
                .filter(member -> !member.free() && !selected.containsKey(member.courseId()))
                .toList();
        for (RouteSnapshotResponse.Member member : newPaidMembers) {
            validateRouteNewPaid(member);
            Long pendingOrderId = pendingCourseMapper.findOrderId(userId, member.courseId());
            if (pendingOrderId != null) {
                throw new BusinessException(ErrorCode.ORDER_CONFLICT, "路线中有课程已有待支付订单",
                        new ErrorData(null, null, String.valueOf(pendingOrderId)));
            }
        }

        TradeOrder order = new TradeOrder();
        order.setOrderNo("BO" + UUID.randomUUID().toString().replace("-", ""));
        order.setUserId(userId);
        order.setProductType("ROUTE");
        order.setProductId(route.routeId());
        order.setState("CREATING");
        order.setFulfillmentState("PENDING");
        order.setQuoteId(quote.getQuoteId());
        order.setPricingHash(quote.getPricingHash());
        order.setCouponMode(quote.getCouponMode());
        order.setCouponState(couponPlan.lines().isEmpty() ? "NONE" : "PENDING");
        order.setTotalCent(route.priceCent());
        order.setCreditCent(currentCredit);
        order.setDiscountCent(quote.getDiscountCent());
        order.setPayableCent(quote.getPayableCent());
        order.setCreatedAt(now);
        order.setExpiresAt(now.plusSeconds(ORDER_TTL_SECONDS));
        order.setSnapshotHash(QuoteHasher.routeSnapshotHash(route));
        order.setSnapshot(routeOrderSnapshot(route, selected, currentLines, quote, couponPlan));
        order.setRequestKey(requestKey);
        order.setRequestHash(requestHash);
        order.setUpdatedAt(now);
        orderMapper.insert(order);
        insertOrderCoupons(order.getId(), couponPlan, now);
        boolean prepared = false;
        boolean couponLocked = false;
        try {
            PurchasePrepareResponse preparedResponse = learningClient.preparePurchase(
                    order.getId(), userId, courseIds);
            prepared = true;
            Map<Long, SourceSelectionItem> held = sourceByCourse(preparedResponse.holds());
            List<QuoteResponse.CourseLine> heldLines = routeLines(route, held);
            long heldCredit = heldLines.stream().mapToLong(QuoteResponse.CourseLine::creditCent).sum();
            long heldPayable = route.priceCent() - heldCredit;
            String heldBaseHash = QuoteHasher.routeQuoteHash(route, heldLines,
                    route.priceCent(), heldCredit, 0, heldPayable, quote.getCouponMode());
            if (!quote.getResultHash().equals(QuoteHasher.withPricing(heldBaseHash, quote.getPricingHash()))) {
                quoteStatusService.mark(quote.getQuoteId(), "STALE");
                throw new BusinessException(ErrorCode.QUOTE_CHANGED);
            }

            Map<Long, Long> couponByCourse = couponPlan.allocations();
            for (RouteSnapshotResponse.Member member : route.members()) {
                SourceSelectionItem source = member.free() ? null : held.get(member.courseId());
                long allocated = member.free() ? 0
                        : (member.allocatedCent() == null ? 0 : member.allocatedCent());
                long credit = source == null ? 0 : allocated;
                long coupon = member.free() ? 0 : couponByCourse.getOrDefault(member.courseId(), 0L);
                long payable = allocated - credit - coupon;
                if (payable < 0) {
                    throw new BusinessException(ErrorCode.DATA_INTEGRITY, "路线券分摊超过课程应付");
                }
                boolean newPaid = !member.free() && source == null;
                OrderCourse line = new OrderCourse();
                line.setOrderId(order.getId());
                line.setCourseId(member.courseId());
                line.setCourseVersion(member.courseVersion() == null ? 0 : member.courseVersion());
                line.setEpochId(member.epochId());
                line.setUnitPriceCent(member.unitPriceCent());
                line.setAllocatedCent(allocated);
                line.setCreditCent(credit);
                line.setCouponCent(coupon);
                line.setPayableCent(payable);
                line.setSourceId(source == null ? null : source.sourceId());
                line.setSourceGrantId(source == null ? null : source.sourceGrantId());
                line.setIsFree(member.free());
                line.setIsNewPaid(newPaid);
                line.setCategoryIds(TradeJson.write(member.categoryAncestorIds() == null
                        ? List.of() : member.categoryAncestorIds()));
                line.setCreatedAt(now);
                orderCourseMapper.insert(line);

                if (newPaid) {
                    if (member.lessons() != null) {
                        for (ProductSnapshotResponse.SnapshotLesson lesson : member.lessons()) {
                            orderLessonMapper.insert(order.getId(), member.courseId(), lesson.lessonId(),
                                    lesson.durationMs() == null ? 0L : lesson.durationMs(), now);
                        }
                    }
                    try {
                        pendingCourseMapper.insert(userId, member.courseId(), order.getId(), now);
                    } catch (DuplicateKeyException e) {
                        throw new BusinessException(ErrorCode.ORDER_CONFLICT, "路线中有课程已有待支付订单");
                    }
                }
                if (source != null && allocated > 0 && source.sourceGrantId() != null) {
                    DeductionLink link = new DeductionLink();
                    link.setSourceOrderId(source.sourceOrderId());
                    link.setDependentOrderId(order.getId());
                    link.setCourseId(member.courseId());
                    link.setSourceId(source.sourceId());
                    link.setSourceGrantId(source.sourceGrantId());
                    link.setState("RESERVED");
                    link.setCreatedAt(now);
                    link.setUpdatedAt(now);
                    deductionLinkMapper.insert(link);
                }
            }

            order.setState("WAIT_PAY");
            order.setUpdatedAt(now);
            if (!couponPlan.lines().isEmpty()) {
                lockCoupons(order, quote, couponPlan, userId);
                couponLocked = true;
                order.setCouponState("LOCKED");
            }
            orderMapper.updateById(order);
            quoteMapper.updateStatusFromReady(quote.getQuoteId(), "CONSUMED", now);
            guardMapper.increment(userId, now);
            return toResponse(order);
        } catch (RuntimeException e) {
            if (couponLocked) {
                releaseCouponsQuietly(order.getId(), userId, quote);
            }
            if (prepared) {
                try {
                    learningClient.releasePurchase(order.getId(), userId);
                } catch (RuntimeException ignored) {
                    // 释放失败留给订单关闭/对账任务重试，不覆盖原始建单错误。
                }
            }
            throw e;
        }
    }

    private Map<Long, SourceSelectionItem> sourceByCourse(List<SourceSelectionItem> items) {
        Map<Long, SourceSelectionItem> result = new LinkedHashMap<>();
        for (SourceSelectionItem item : items) {
            result.put(item.courseId(), item);
        }
        return result;
    }

    private List<QuoteResponse.CourseLine> routeLines(RouteSnapshotResponse route,
                                                      Map<Long, SourceSelectionItem> sources) {
        List<QuoteResponse.CourseLine> lines = new ArrayList<>();
        for (RouteSnapshotResponse.Member member : route.members()) {
            long allocated = member.free() ? 0
                    : (member.allocatedCent() == null ? 0 : member.allocatedCent());
            SourceSelectionItem source = member.free() ? null : sources.get(member.courseId());
            long credit = source == null ? 0 : allocated;
            lines.add(new QuoteResponse.CourseLine(member.courseId(), member.unitPriceCent(), allocated,
                    credit, source == null ? null : source.sourceId(),
                    source == null ? null : source.sourceGrantId(),
                    source == null ? null : source.sourceOrderId(), allocated - credit));
        }
        return lines;
    }

    private void validateRouteNewPaid(RouteSnapshotResponse.Member member) {
        boolean hasVideo = member.lessons() != null && member.lessons().stream()
                .anyMatch(lesson -> lesson.mediaId() != null
                        && lesson.durationMs() != null && lesson.durationMs() > 0);
        if (!hasVideo) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY,
                    "路线新购付费课程缺少可播放视频：" + member.title());
        }
    }

    private String routeOrderSnapshot(RouteSnapshotResponse route,
                                      Map<Long, SourceSelectionItem> selected,
                                      List<QuoteResponse.CourseLine> lines,
                                      PriceQuote quote,
                                      CouponPlan couponPlan) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("productType", "ROUTE");
        root.put("productId", route.routeId());
        root.put("productVersion", route.versionNo());
        root.put("priceCent", route.priceCent());
        root.put("couponMode", quote.getCouponMode());
        root.put("couponPlan", couponPlanMaps(couponPlan));
        root.put("quoteHash", quote.getResultHash());
        root.put("policyVersion", "trade-v1");
        Map<Long, Long> couponByCourse = couponPlan.allocations();
        List<Map<String, Object>> members = new ArrayList<>();
        for (QuoteResponse.CourseLine line : lines) {
            SourceSelectionItem source = selected.get(line.courseId());
            long coupon = couponByCourse.getOrDefault(line.courseId(), 0L);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("courseId", line.courseId());
            row.put("unitPriceCent", line.unitPriceCent());
            row.put("allocatedCent", line.allocatedCent());
            row.put("creditCent", line.creditCent());
            row.put("couponCent", coupon);
            row.put("payableCent", line.remainingCent() - coupon);
            row.put("sourceId", source == null ? null : source.sourceId());
            row.put("sourceGrantId", source == null ? null : source.sourceGrantId());
            row.put("sourceOrderId", source == null ? null : source.sourceOrderId());
            members.add(row);
        }
        root.put("members", members);
        return TradeJson.write(root);
    }

    private OrderResponse existingResponse(TradeOrder order, String requestHash) {
        if (!order.getRequestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.CONFLICT, "该幂等键已用于不同的下单请求");
        }
        return toResponse(order);
    }

    private String requestKey(String header, String quoteId) {
        String key = header == null ? "" : header.trim();
        if (key.isEmpty()) {
            key = quoteId;
        }
        if (key.length() > 64) {
            key = QuoteHasher.sha256(key);
        }
        return key;
    }

    public OrderResponse toResponse(TradeOrder order) {
        List<OrderCourse> courses = orderCourseMapper.selectList(Wrappers.<OrderCourse>lambdaQuery()
                .eq(OrderCourse::getOrderId, order.getId())
                .orderByAsc(OrderCourse::getCourseId));
        List<OrderResponse.Item> items = new ArrayList<>();
        for (OrderCourse course : courses) {
            items.add(new OrderResponse.Item(course.getCourseId(), course.getCourseVersion(),
                    course.getUnitPriceCent(), course.getAllocatedCent(), course.getCreditCent(),
                    course.getCouponCent(), course.getPayableCent(),
                    Boolean.TRUE.equals(course.getIsFree()), Boolean.TRUE.equals(course.getIsNewPaid())));
        }
        List<OrderCoupon> couponRows = orderCouponMapper.selectList(Wrappers.<OrderCoupon>lambdaQuery()
                .eq(OrderCoupon::getOrderId, order.getId())
                .orderByAsc(OrderCoupon::getApplyOrder));
        List<QuoteResponse.CouponLine> coupons = new ArrayList<>();
        for (OrderCoupon row : couponRows) {
            coupons.add(new QuoteResponse.CouponLine(row.getUserCouponId(), row.getApplyOrder(),
                    row.getBaseCent(), row.getDiscountCent(), parseAllocations(row.getAllocationJson())));
        }
        return new OrderResponse(order.getId(), order.getOrderNo(), order.getState(),
                order.getFulfillmentState(), order.getCreatedAt(), order.getExpiresAt(), order.getPaidAt(),
                new OrderResponse.Pricing(order.getTotalCent(), order.getCreditCent(),
                        order.getDiscountCent(), order.getPayableCent()),
                items, coupons, order.getProductType());
    }

    private List<QuoteResponse.Allocation> parseAllocations(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(JSON.readValue(json, QuoteResponse.Allocation[].class));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "订单优惠分摊快照损坏");
        }
    }

    private String orderSnapshot(ProductSnapshotResponse snapshot, PriceQuote quote, CouponPlan couponPlan) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("productType", snapshot.type());
        root.put("productId", snapshot.id());
        root.put("productVersion", snapshot.version());
        root.put("priceCent", snapshot.priceCent());
        root.put("couponMode", quote.getCouponMode());
        root.put("couponPlan", couponPlanMaps(couponPlan));
        root.put("selectedSourceIds", List.of());
        root.put("quoteHash", quote.getResultHash());
        root.put("policyVersion", "trade-v1");
        List<Map<String, Object>> lessons = new ArrayList<>();
        if (snapshot.lessons() != null) {
            for (ProductSnapshotResponse.SnapshotLesson lesson : snapshot.lessons()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("lessonId", lesson.lessonId());
                row.put("durationMs", lesson.durationMs());
                lessons.add(row);
            }
        }
        root.put("lessons", lessons);
        return TradeJson.write(root);
    }
}