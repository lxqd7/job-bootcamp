package com.jobbootcamp.common.error;

import com.jobbootcamp.common.web.ErrorData;

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