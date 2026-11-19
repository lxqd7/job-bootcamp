package com.jobbootcamp.common.web;

import java.util.List;

public record ErrorData(List<FieldErrorItem> fieldErrors, Long currentRevision, String resourceId) {

    public static ErrorData empty() {
        return new ErrorData(null, null, null);
    }
}