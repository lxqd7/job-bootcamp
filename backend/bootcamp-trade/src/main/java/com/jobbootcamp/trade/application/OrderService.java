package com.jobbootcamp.trade.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ErrorData;
import com.jobbootcamp.contracts.internal.AccessCheckResponse;
import com.jobbootcamp.contracts.internal.ProductSnapshotResponse;
import com.jobbootcamp.contracts.internal.PurchasePrepareResponse;
import com.jobbootcamp.contracts.internal.RouteSnapshotResponse;
import com.jobbootcamp.contracts.internal.SourceSelectionItem;
import com.jobbootcamp.contracts.internal.SourceSelectionResponse;
import com.jobbootcamp.trade.domain.DeductionLink;
import com.jobbootcamp.trade.domain.OrderCourse;
import com.jobbootcamp.trade.domain.PriceQuote;
import com.jobbootcamp.trade.domain.TradeOrder;
import com.jobbootcamp.trade.domain.dto.CreateOrderRequest;
import com.jobbootcamp.trade.domain.dto.OrderResponse;
import com.jobbootcamp.trade.domain.dto.PageResponse;
import com.jobbootcamp.trade.domain.dto.QuoteResponse;
import com.jobbootcamp.trade.infrastructure.DeductionLinkMapper;
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
    private final DeductionLinkMapper deductionLinkMapper;
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
                        DeductionLinkMapper deductionLinkMapper,
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
        this.deductionLinkMapper = deductionLinkMapper;
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
        if (!couponIds.isEmpty()) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "优惠券功能尚未开放");
        }
        if ("ROUTE".equals(quote.getProductType())) {
            return createRouteInTransaction(quote, userId, requestKey, requestHash, now);
        }

        ProductSnapshotResponse snapshot = courseClient.courseSnapshot(quote.getProductId());
        String currentSnapshotHash = QuoteHasher.snapshotHash(snapshot);
        // 报价只是预览：成交前必须用当前商品/价格重算，任何变化都不能静默沿用旧金额。
        String currentQuoteHash = QuoteHasher.quoteHash(snapshot, quote.getTotalCent(), quote.getCreditCent(),
                quote.getDiscountCent(), quote.getPayableCent(), "NONE");
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
        order.setTotalCent(quote.getTotalCent());
        order.setCreditCent(quote.getCreditCent());
        order.setDiscountCent(quote.getDiscountCent());
        order.setPayableCent(quote.getPayableCent());
        order.setCreatedAt(now);
        order.setExpiresAt(now.plusSeconds(ORDER_TTL_SECONDS));
        order.setSnapshotHash(currentSnapshotHash);
        order.setSnapshot(orderSnapshot(snapshot, quote));
        order.setRequestKey(requestKey);
        order.setRequestHash(requestHash);
        order.setUpdatedAt(now);
        orderMapper.insert(order);

        OrderCourse orderCourse = new OrderCourse();
        orderCourse.setOrderId(order.getId());
        orderCourse.setCourseId(quote.getProductId());
        orderCourse.setCourseVersion(quote.getProductVersion());
        orderCourse.setUnitPriceCent(quote.getUnitPriceCent());
        orderCourse.setAllocatedCent(quote.getTotalCent());
        orderCourse.setCreditCent(0L);
        orderCourse.setCouponCent(0L);
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

        order.setState("WAIT_PAY");
        order.setUpdatedAt(now);
        orderMapper.updateById(order);
        quoteMapper.updateStatusFromReady(quote.getQuoteId(), "CONSUMED", now);
        guardMapper.increment(userId, now);
        return toResponse(order);
    }

    private TradeOrder findByRequestKey(Long userId, String requestKey) {
        return orderMapper.selectOne(Wrappers.<TradeOrder>lambdaQuery()
                .eq(TradeOrder::getUserId, userId)
                .eq(TradeOrder::getRequestKey, requestKey));
    }

    /**
     * 路线订单：报价只预览，成交时重新取路线快照和最早来源；学习 prepare 建立 hold，
     * 已有课程只写抵扣边，不新增来源；全部已拥有时在报价阶段就不允许出现零元订单。
     */
    private OrderResponse createRouteInTransaction(PriceQuote quote, Long userId,
                                                   String requestKey, String requestHash,
                                                   LocalDateTime now) {
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
        String currentHash = QuoteHasher.routeQuoteHash(route, currentLines, route.priceCent(),
                currentCredit, 0, currentPayable, "NONE");
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
        order.setTotalCent(route.priceCent());
        order.setCreditCent(currentCredit);
        order.setDiscountCent(0L);
        order.setPayableCent(currentPayable);
        order.setCreatedAt(now);
        order.setExpiresAt(now.plusSeconds(ORDER_TTL_SECONDS));
        order.setSnapshotHash(QuoteHasher.routeSnapshotHash(route));
        order.setSnapshot(routeOrderSnapshot(route, selected, currentLines, quote));
        order.setRequestKey(requestKey);
        order.setRequestHash(requestHash);
        order.setUpdatedAt(now);
        orderMapper.insert(order);
        boolean prepared = false;
        try {
            PurchasePrepareResponse preparedResponse = learningClient.preparePurchase(
                    order.getId(), userId, courseIds);
            prepared = true;
            Map<Long, SourceSelectionItem> held = sourceByCourse(preparedResponse.holds());
            List<QuoteResponse.CourseLine> heldLines = routeLines(route, held);
            long heldCredit = heldLines.stream().mapToLong(QuoteResponse.CourseLine::creditCent).sum();
            long heldPayable = route.priceCent() - heldCredit;
            if (!quote.getResultHash().equals(QuoteHasher.routeQuoteHash(route, heldLines,
                    route.priceCent(), heldCredit, 0, heldPayable, "NONE"))) {
                quoteStatusService.mark(quote.getQuoteId(), "STALE");
                throw new BusinessException(ErrorCode.QUOTE_CHANGED);
            }

            for (RouteSnapshotResponse.Member member : route.members()) {
                SourceSelectionItem source = member.free() ? null : held.get(member.courseId());
                long allocated = member.free() ? 0
                        : (member.allocatedCent() == null ? 0 : member.allocatedCent());
                long credit = source == null ? 0 : allocated;
                long payable = allocated - credit;
                boolean newPaid = !member.free() && source == null;
                OrderCourse line = new OrderCourse();
                line.setOrderId(order.getId());
                line.setCourseId(member.courseId());
                line.setCourseVersion(member.courseVersion() == null ? 0 : member.courseVersion());
                line.setUnitPriceCent(member.unitPriceCent());
                line.setAllocatedCent(allocated);
                line.setCreditCent(credit);
                line.setCouponCent(0L);
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
            orderMapper.updateById(order);
            quoteMapper.updateStatusFromReady(quote.getQuoteId(), "CONSUMED", now);
            guardMapper.increment(userId, now);
            return toResponse(order);
        } catch (RuntimeException e) {
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
                                      PriceQuote quote) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("productType", "ROUTE");
        root.put("productId", route.routeId());
        root.put("productVersion", route.versionNo());
        root.put("priceCent", route.priceCent());
        root.put("couponMode", "NONE");
        root.put("quoteHash", quote.getResultHash());
        root.put("policyVersion", "trade-v1");
        List<Map<String, Object>> members = new ArrayList<>();
        for (QuoteResponse.CourseLine line : lines) {
            SourceSelectionItem source = selected.get(line.courseId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("courseId", line.courseId());
            row.put("unitPriceCent", line.unitPriceCent());
            row.put("allocatedCent", line.allocatedCent());
            row.put("creditCent", line.creditCent());
            row.put("payableCent", line.remainingCent());
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
        return new OrderResponse(order.getId(), order.getOrderNo(), order.getState(),
                order.getFulfillmentState(), order.getCreatedAt(), order.getExpiresAt(), order.getPaidAt(),
                new OrderResponse.Pricing(order.getTotalCent(), order.getCreditCent(),
                        order.getDiscountCent(), order.getPayableCent()),
                items, List.of(), order.getProductType());
    }

    private String orderSnapshot(ProductSnapshotResponse snapshot, PriceQuote quote) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("productType", snapshot.type());
        root.put("productId", snapshot.id());
        root.put("productVersion", snapshot.version());
        root.put("priceCent", snapshot.priceCent());
        root.put("couponMode", "NONE");
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