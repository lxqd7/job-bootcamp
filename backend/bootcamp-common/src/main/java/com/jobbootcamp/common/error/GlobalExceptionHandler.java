package com.jobbootcamp.common.error;

import com.jobbootcamp.common.web.ApiResponse;
import com.jobbootcamp.common.web.ErrorData;
import com.jobbootcamp.common.web.FieldErrorItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * 统一错误出口：业务异常保留稳定 code/message/data，参数错误附加字段明细，
 * 其余异常只记录服务端日志并返回通用 INTERNAL_ERROR，避免泄漏实现细节。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<ErrorData>> handleBusinessException(BusinessException ex) {
        return respond(ex.errorCode(), ex.getMessage(), ex.data());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ErrorData>> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorItem> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldErrorItem)
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(),
                new ErrorData(fieldErrors, null, null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<ErrorData>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("unreadable request body: {}", ex.getMessage());
        return respond(ErrorCode.BAD_REQUEST, ErrorCode.BAD_REQUEST.defaultMessage(), ErrorData.empty());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<ErrorData>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.defaultMessage(), ErrorData.empty());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<ErrorData>> handleNoResource(NoResourceFoundException ex) {
        log.warn("requested resource not found: {}", ex.getResourcePath());
        return respond(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(), ErrorData.empty());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorData>> handleUnexpected(Exception ex) {
        log.error("unexpected exception", ex);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), ErrorData.empty());
    }

    private FieldErrorItem toFieldErrorItem(FieldError fieldError) {
        String message = fieldError.getDefaultMessage() == null
                ? ErrorCode.VALIDATION_FAILED.defaultMessage()
                : fieldError.getDefaultMessage();
        return new FieldErrorItem(fieldError.getField(), message);
    }

    private ResponseEntity<ApiResponse<ErrorData>> respond(ErrorCode errorCode, String message, ErrorData data) {
        return ResponseEntity.status(errorCode.status())
                .body(ApiResponse.error(errorCode.name(), message, data));
    }
}