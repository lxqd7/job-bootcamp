package com.jobbootcamp.trade.application;

import com.jobbootcamp.contracts.internal.ProductSnapshotResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class QuoteHasherTest {

    private ProductSnapshotResponse snapshot(long priceCent) {
        return new ProductSnapshotResponse("COURSE", 11L, "并发实战", "desc", 2L, priceCent,
                false, "ONLINE", 3L, List.of(3L),
                List.of(new ProductSnapshotResponse.SnapshotLesson(12L, 4L, "第一课", 1, 1L, 5055L)));
    }

    @Test
    void quoteHashIsStableForSameSnapshotAndAmounts() {
        String first = QuoteHasher.quoteHash(snapshot(990), 990, 0, 0, 990, "NONE");
        String second = QuoteHasher.quoteHash(snapshot(990), 990, 0, 0, 990, "NONE");
        assertEquals(first, second);
    }

    @Test
    void quoteHashChangesWhenPriceChanges() {
        String oldHash = QuoteHasher.quoteHash(snapshot(990), 990, 0, 0, 990, "NONE");
        String newHash = QuoteHasher.quoteHash(snapshot(1000), 1000, 0, 0, 1000, "NONE");
        assertNotEquals(oldHash, newHash);
    }

    @Test
    void requestHashIgnoresCouponIdOrder() {
        String first = QuoteHasher.requestHash("q", "h", List.of(9L, 3L, 7L));
        String second = QuoteHasher.requestHash("q", "h", List.of(3L, 7L, 9L));
        assertEquals(first, second);
    }
}