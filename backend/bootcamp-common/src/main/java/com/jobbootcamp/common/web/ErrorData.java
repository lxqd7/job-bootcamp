package com.jobbootcamp.common.web;

import java.util.List;

/**
 * 错误附加信息：字段校验明细、乐观锁当前版本，或可供前端定位的业务资源 ID。
 */
public record ErrorData(List<FieldErrorItem> fieldErrors, Long currentRevision, String resourceId) {

    public static ErrorData empty() {
        return new ErrorData(null, null, null);
    }
}