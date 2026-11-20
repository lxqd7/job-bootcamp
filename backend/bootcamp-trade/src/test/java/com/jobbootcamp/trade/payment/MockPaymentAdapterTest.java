package com.jobbootcamp.trade.payment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockPaymentAdapterTest {

    private final MockPaymentAdapter adapter = new MockPaymentAdapter("test-key");

    @Test
    void signatureVerifiesOnlyOriginalBody() {
        String body = "{\"merchantRequestNo\":\"M1\",\"amountCent\":990}";
        String signature = adapter.sign(body);
        assertTrue(adapter.verify(body, signature));
        assertFalse(adapter.verify(body + " ", signature));
        assertFalse(adapter.verify(body, null));
    }
}