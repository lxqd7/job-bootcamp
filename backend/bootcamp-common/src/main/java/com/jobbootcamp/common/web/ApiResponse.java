package com.jobbootcamp.common.web;

/**
 * 统一响应体。成功为 code=OK、message 为空；失败为稳定错误码与中文提示。
 */
public record ApiResponse<T>(String code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "", data, TraceContext.currentTraceId());
    }

    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return new ApiResponse<>(code, message, data, TraceContext.currentTraceId());
    }
}