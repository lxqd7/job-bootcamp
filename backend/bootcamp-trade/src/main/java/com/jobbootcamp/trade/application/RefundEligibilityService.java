package com.jobbootcamp.trade.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.RefundBasisResponse;
import com.jobbootcamp.contracts.internal.RefundQualificationRequest;
import com.jobbootcamp.contracts.internal.RefundQualificationResponse;
import com.jobbootcamp.trade.domain.DeductionLink;
import com.jobbootcamp.trade.domain.OrderCourse;
import com.jobbootcamp.trade.domain.Payment;
import com.jobbootcamp.trade.domain.TradeOrder;
import com.jobbootcamp.trade.infrastructure.DeductionLinkMapper;
import com.jobbootcamp.trade.infrastructure.OrderCourseMapper;
import com.jobbootcamp.trade.infrastructure.OrderLessonMapper;
import com.jobbootcamp.trade.infrastructure.PaymentMapper;
import com.jobbootcamp.trade.infrastructure.PaymentRequestMapper;
import com.jobbootcamp.trade.infrastructure.TradeOrderMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 退款申请的交易侧资格预检。学习侧负责受理和冻结，交易只回答：
 * 订单快照是否一致、是否仍在 7 天窗口、进度是否≤20%、以及是否被已支付/处理中的依赖订单阻塞。
 */
@Service
public class RefundEligibilityService {

    private final TradeOrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final OrderCourseMapper orderCourseMapper;
    private final OrderLessonMapper orderLessonMapper;
    private final DeductionLinkMapper deductionLinkMapper;
    private final PaymentRequestMapper paymentRequestMapper;
    private final PaymentService paymentService;

    public RefundEligibilityService(TradeOrderMapper orderMapper,
                                    PaymentMapper paymentMapper,
                                    OrderCourseMapper orderCourseMapper,
                                    OrderLessonMapper orderLessonMapper,
                                    DeductionLinkMapper deductionLinkMapper,
                                    PaymentRequestMapper paymentRequestMapper,
                                    PaymentService paymentService) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.orderCourseMapper = orderCourseMapper;
        this.orderLessonMapper = orderLessonMapper;
        this.deductionLinkMapper = deductionLinkMapper;
        this.paymentRequestMapper = paymentRequestMapper;
        this.paymentService = paymentService;
    }

    public RefundBasisResponse basis(Long orderId, Long userId) {
        TradeOrder order = requirePaidOrder(orderId, userId);
        Payment payment = paymentMapper.selectSucceededByOrder(orderId);
        if (payment == null) {
            throw new BusinessException(ErrorCode.ORDER_SYNCING, "支付投影尚未就绪");
        }
        List<OrderCourse> courses = orderCourseMapper.selectList(Wrappers.<OrderCourse>lambdaQuery()
                .eq(OrderCourse::getOrderId, orderId)
                .orderByAsc(OrderCourse::getCourseId));
        List<RefundBasisResponse.CourseLessons> result = new ArrayList<>();
        for (OrderCourse course : courses) {
            if ("ROUTE".equals(order.getProductType()) && !Boolean.TRUE.equals(course.getIsNewPaid())) {
                continue;
            }
            List<Long> lessonIds = orderLessonMapper.selectLessonIds(orderId, course.getCourseId());
            if (lessonIds.isEmpty()) {
                throw new BusinessException(ErrorCode.DATA_INTEGRITY, "退款清单缺少课程小节");
            }
            result.add(new RefundBasisResponse.CourseLessons(course.getCourseId(), lessonIds));
        }
        if (result.isEmpty()) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "退款清单缺少新购课程");
        }
        return new RefundBasisResponse(orderId, order.getProductType(),
                payment.getPaidAt().toInstant(ZoneOffset.UTC).toString(), order.getSnapshotHash(),
                order.getPayableCent(), result);
    }

    public RefundQualificationResponse qualify(RefundQualificationRequest request) {
        TradeOrder order = requirePaidOrder(request.orderId(), request.userId());
        Payment payment = paymentMapper.selectSucceededByOrder(request.orderId());
        if (payment == null) {
            return new RefundQualificationResponse("PENDING", "ORDER_SYNCING", 0, null);
        }
        if (!order.getSnapshotHash().equals(request.snapshotHash())) {
            return rejected("QUOTE_CHANGED", order, payment);
        }
        LocalDateTime acceptedAt;
        try {
            acceptedAt = LocalDateTime.ofInstant(Instant.parse(request.acceptedAt()), ZoneOffset.UTC);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "acceptedAt 格式不合法");
        }
        // 资格以申请时点判断；处理、重试或跨过七天都不能重新计时。
        if (!acceptedAt.isBefore(payment.getPaidAt().plusDays(7))) {
            return rejected("REFUND_EXPIRED", order, payment);
        }
        if (request.totalCount() <= 0 || request.completedCount() < 0
                || request.completedCount() * 5L > request.totalCount()) {
            return rejected("REFUND_PROGRESS", order, payment);
        }
        String dependency = coordinateDependencies(request.orderId(), request.userId());
        if ("REFUND_DEPENDENCY".equals(dependency)) {
            return rejected(dependency, order, payment);
        }
        if (dependency != null) {
            return new RefundQualificationResponse("PENDING", dependency, 0,
                    payment.getPaidAt().toInstant(ZoneOffset.UTC).toString());
        }
        return new RefundQualificationResponse("QUALIFIED", null, order.getPayableCent(),
                payment.getPaidAt().toInstant(ZoneOffset.UTC).toString());
    }

    /**
     * 退款来源被后续路线订单用作抵扣时，先处理待付依赖：无渠道单的直接关闭，渠道结果未知的保持 PENDING；
     * 已支付或已提交抵扣边的依赖则构成真实关联，必须由用户先退依赖订单。
     */
    private String coordinateDependencies(Long sourceOrderId, Long userId) {
        List<DeductionLink> links = deductionLinkMapper.selectList(Wrappers.<DeductionLink>lambdaQuery()
                .eq(DeductionLink::getSourceOrderId, sourceOrderId)
                .in(DeductionLink::getState, List.of("RESERVED", "COMMITTED"))
                .orderByAsc(DeductionLink::getDependentOrderId));
        if (links.isEmpty()) {
            return null;
        }
        boolean pending = false;
        for (DeductionLink link : links) {
            TradeOrder dependent = orderMapper.selectById(link.getDependentOrderId());
            if (dependent == null) {
                continue;
            }
            if (!dependent.getUserId().equals(userId)) {
                return "REFUND_DEPENDENCY";
            }
            if ("COMMITTED".equals(link.getState()) || "PAID".equals(dependent.getState())) {
                return "REFUND_DEPENDENCY";
            }
            if ("WAIT_PAY".equals(dependent.getState())) {
                if (paymentRequestMapper.countActiveByOrder(dependent.getId()) > 0) {
                    pending = true;
                    continue;
                }
                try {
                    paymentService.closeBySystem(dependent.getId());
                    deductionLinkMapper.updateState(dependent.getId(), "RESERVED", "RELEASED",
                            LocalDateTime.now(ZoneOffset.UTC));
                } catch (RuntimeException e) {
                    pending = true;
                }
                continue;
            }
            if ("CLOSING".equals(dependent.getState())) {
                pending = true;
                continue;
            }
            if ("CLOSED".equals(dependent.getState()) || "CREATE_FAILED".equals(dependent.getState())) {
                deductionLinkMapper.updateState(dependent.getId(), "RESERVED", "RELEASED",
                        LocalDateTime.now(ZoneOffset.UTC));
            }
        }
        return pending ? "PAYMENT_UNKNOWN" : null;
    }

    private TradeOrder requirePaidOrder(Long orderId, Long userId) {
        TradeOrder order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!"PAID".equals(order.getState()) || order.getPaidAt() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "订单尚未支付完成");
        }
        return order;
    }

    private RefundQualificationResponse rejected(String reason, TradeOrder order, Payment payment) {
        return new RefundQualificationResponse("REJECTED", reason, order.getPayableCent(),
                payment.getPaidAt().toInstant(ZoneOffset.UTC).toString());
    }
}