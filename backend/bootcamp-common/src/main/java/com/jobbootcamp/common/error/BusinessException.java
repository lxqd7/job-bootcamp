package com.jobbootcamp.common.error;

import com.jobbootcamp.common.web.ErrorData;

/**
 * 可预期的业务失败：携带稳定错误码和可展示给用户的提示。
 * 未预期异常由全局处理器转成 INTERNAL_ERROR，不把堆栈或内部细节返回客户端。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final ErrorData data;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), ErrorData.empty());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, ErrorData.empty());
    }

    public BusinessException(ErrorCode errorCode, String message, ErrorData data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data == null ? ErrorData.empty() : data;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public ErrorData data() {
        return data;
    }
}