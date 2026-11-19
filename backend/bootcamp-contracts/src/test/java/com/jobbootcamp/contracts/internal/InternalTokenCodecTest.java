package com.jobbootcamp.contracts.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalTokenCodecTest {

    private static final String SECRET = "unit-test-secret";

    @Test
    void validTokenPassesVerification() {
        String token = InternalTokenCodec.issue(SECRET, "gateway", "user-service", "sessions:check", 60);

        assertThatCode(() -> InternalTokenCodec.verify(SECRET, token, "user-service", "sessions:check"))
                .doesNotThrowAnyException();
    }

    @Test
    void wrongAudienceOperationOrSecretIsRejected() {
        String token = InternalTokenCodec.issue(SECRET, "gateway", "user-service", "sessions:check", 60);

        assertThatThrownBy(() -> InternalTokenCodec.verify(SECRET, token, "course-service", "sessions:check"))
                .isInstanceOf(InternalTokenException.class);
        assertThatThrownBy(() -> InternalTokenCodec.verify(SECRET, token, "user-service", "other:operation"))
                .isInstanceOf(InternalTokenException.class);
        assertThatThrownBy(() -> InternalTokenCodec.verify("other-secret", token, "user-service", "sessions:check"))
                .isInstanceOf(InternalTokenException.class);
    }

    @Test
    void expiredOrTamperedTokenIsRejected() {
        String expired = InternalTokenCodec.issue(SECRET, "gateway", "user-service", "sessions:check", -1);
        String valid = InternalTokenCodec.issue(SECRET, "gateway", "user-service", "sessions:check", 60);
        String tampered = valid.substring(0, valid.length() - 2) + "xx";

        assertThatThrownBy(() -> InternalTokenCodec.verify(SECRET, expired, "user-service", "sessions:check"))
                .isInstanceOf(InternalTokenException.class);
        assertThatThrownBy(() -> InternalTokenCodec.verify(SECRET, tampered, "user-service", "sessions:check"))
                .isInstanceOf(InternalTokenException.class);
    }
}