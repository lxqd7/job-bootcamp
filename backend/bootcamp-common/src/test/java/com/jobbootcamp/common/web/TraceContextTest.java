package com.jobbootcamp.common.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextTest {

    @Test
    void okResponseUsesOkCodeAndEmptyMessage() {
        ApiResponse<String> response = ApiResponse.ok("data");

        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.message()).isEmpty();
        assertThat(response.data()).isEqualTo("data");
        assertThat(response.traceId()).isEmpty();
    }

    @Test
    void generatesNewTraceIdWhenAbsentOrInvalid() {
        assertThat(TraceContext.resolve(null)).matches("[0-9a-f]{32}");
        assertThat(TraceContext.resolve("not-a-trace-id")).matches("[0-9a-f]{32}");
    }

    @Test
    void keepsValidIncomingTraceId() {
        String traceId = "0123456789abcdef0123456789abcdef";

        assertThat(TraceContext.resolve(traceId)).isEqualTo(traceId);
    }
}