package com.jobbootcamp.common.error;

import org.springframework.http.HttpStatus;

/**
 * 对外错误码。code 稳定为英文常量，message 为中文可读提示，与接口契约保持一致。
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "请求参数不合法"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "请求格式错误"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "未登录或登录已失效"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "无权访问该资源"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持"),
    CONFLICT(HttpStatus.CONFLICT, "状态或版本冲突"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "依赖服务不可用"),

    NO_COURSE_ACCESS(HttpStatus.FORBIDDEN, "没有该课程的有效学习权益"),
    NO_ROUTE_BENEFIT(HttpStatus.FORBIDDEN, "没有该路线的福利资格"),
    TEACHER_OUT_OF_SCOPE(HttpStatus.FORBIDDEN, "超出教师负责范围"),
    QUOTE_CHANGED(HttpStatus.CONFLICT, "报价内容已变化，请重新确认"),
    ORDER_CONFLICT(HttpStatus.CONFLICT, "存在重叠的待支付订单"),
    COUPON_UNAVAILABLE(HttpStatus.CONFLICT, "优惠券当前不可用"),
    COUPON_COMBINATION_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "所选优惠券组合不可行"),
    EXCHANGE_CHANGED(HttpStatus.CONFLICT, "兑换配置已变化，请重新确认"),
    INSUFFICIENT_POINTS(HttpStatus.UNPROCESSABLE_ENTITY, "积分不足"),
    STOCK_EXHAUSTED(HttpStatus.CONFLICT, "库存不足"),
    ACQUISITION_LIMIT(HttpStatus.CONFLICT, "已达到领取或兑换上限"),
    AI_REVIEW_BUSY(HttpStatus.CONFLICT, "已有未完成的 AI 点评任务"),
    ASSIGNMENT_OFFLINE(HttpStatus.CONFLICT, "题目已下架"),
    DRAFT_CONFLICT(HttpStatus.CONFLICT, "草稿已在其他设备更新"),
    REFUND_DEPENDENCY(HttpStatus.CONFLICT, "订单被其他订单用作抵扣，暂不能退款"),
    REFUND_PROGRESS(HttpStatus.UNPROCESSABLE_ENTITY, "学习进度超过可退款范围"),
    REFUND_EXPIRED(HttpStatus.CONFLICT, "已超过退款申请期限"),
    ORDER_SYNCING(HttpStatus.CONFLICT, "订单状态同步中，请稍后重试"),
    CHANNEL_UNKNOWN(HttpStatus.CONFLICT, "支付渠道结果未知，处理中"),
    DATA_INTEGRITY(HttpStatus.INTERNAL_SERVER_ERROR, "业务数据完整性异常"),
    ALREADY_OWNED(HttpStatus.CONFLICT, "已拥有该课程"),
    REFUND_IN_PROGRESS(HttpStatus.CONFLICT, "该课程退款处理中"),
    ASSIGNMENT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "作业内容不合法"),
    INPUT_TOO_LARGE(HttpStatus.UNPROCESSABLE_ENTITY, "提交内容超过大小限制");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}