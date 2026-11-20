package com.jobbootcamp.trade.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ErrorData;
import com.jobbootcamp.contracts.internal.AccessCheckResponse;
import com.jobbootcamp.contracts.internal.ProductSnapshotResponse;
import com.jobbootcamp.trade.domain.OrderCourse;
import com.jobbootcamp.trade.domain.PriceQuote;
import com.jobbootcamp.trade.domain.TradeOrder;
import com.jobbootcamp.trade.domain.dto.CreateOrderRequest;
import com.jobbootcamp.trade.domain.dto.OrderResponse;
import com.jobbootcamp.trade.domain.dto.PageResponse;
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
import java.util.UUID;

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

        ProductSnapshotResponse snapshot = courseClient.courseSnapshot(quote.getProductId());
        String currentSnapshotHash = QuoteHasher.snapshotHash(snapshot);
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
                items, List.of(), "COURSE");
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