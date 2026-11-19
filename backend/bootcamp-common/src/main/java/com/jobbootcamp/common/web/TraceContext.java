package com.jobbootcamp.common.web;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

public final class TraceContext {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Trace-Id";

    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[0-9a-f]{32}");

    private TraceContext() {
    }

    public static String currentTraceId() {
        String traceId = MDC.get(MDC_KEY);
        return traceId == null ? "" : traceId;
    }

    static String resolve(String incoming) {
        if (incoming != null && TRACE_ID_PATTERN.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}