package com.jobbootcamp.trade.application;

import com.jobbootcamp.contracts.internal.ProductSnapshotResponse;
import com.jobbootcamp.contracts.internal.RouteSnapshotResponse;
import com.jobbootcamp.trade.domain.dto.QuoteResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RouteQuoteHasherTest {

    private RouteSnapshotResponse route() {
        return new RouteSnapshotResponse(2L, "路线", "desc", 1L, 1200L, "ONLINE", List.of(
                new RouteSnapshotResponse.Member(15L, "A", 2L, 3L, 990L, 600L, false, 3L, List.of(3L),
                        List.of(new ProductSnapshotResponse.SnapshotLesson(12L, 4L, "L", 1, 1L, 5055L))),
                new RouteSnapshotResponse.Member(11L, "B", 1L, 4L, 990L, 600L, false, 3L, List.of(3L),
                        List.of(new ProductSnapshotResponse.SnapshotLesson(13L, 4L, "M", 1, 1L, 5055L)))));
    }

    private QuoteResponse.CourseLine line(long sourceId, long credit) {
        return new QuoteResponse.CourseLine(15L, 990L, 600L, credit, sourceId, sourceId, null, 600L - credit);
    }

    @Test
    void routeQuoteHashChangesWhenDeductionSourceChanges() {
        String first = QuoteHasher.routeQuoteHash(route(), List.of(line(3L, 600L)), 1200, 600, 0, 600, "NONE");
        String second = QuoteHasher.routeQuoteHash(route(), List.of(line(4L, 600L)), 1200, 600, 0, 600, "NONE");
        assertNotEquals(first, second);
    }

    @Test
    void routeQuoteHashIsStableForSameInput() {
        String first = QuoteHasher.routeQuoteHash(route(), List.of(line(3L, 600L)), 1200, 600, 0, 600, "NONE");
        String second = QuoteHasher.routeQuoteHash(route(), List.of(line(3L, 600L)), 1200, 600, 0, 600, "NONE");
        assertEquals(first, second);
    }
}