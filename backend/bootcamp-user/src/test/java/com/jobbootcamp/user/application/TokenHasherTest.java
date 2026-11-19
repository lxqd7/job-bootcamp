package com.jobbootcamp.user.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    private final TokenHasher tokenHasher = new TokenHasher();

    @Test
    void randomTokenIsUrlSafeAndHasExpectedLength() {
        String token = tokenHasher.randomToken();

        assertThat(token).hasSize(43);
        assertThat(token).matches("[A-Za-z0-9_-]+");
        assertThat(tokenHasher.randomToken()).isNotEqualTo(token);
    }

    @Test
    void storedHashMatchesOnlyTheSameRawToken() {
        String token = tokenHasher.randomToken();
        String hash = tokenHasher.sha256(token);

        assertThat(hash).hasSize(64);
        assertThat(tokenHasher.matches(token, hash)).isTrue();
        assertThat(tokenHasher.matches(tokenHasher.randomToken(), hash)).isFalse();
        assertThat(tokenHasher.matches(null, hash)).isFalse();
    }
}