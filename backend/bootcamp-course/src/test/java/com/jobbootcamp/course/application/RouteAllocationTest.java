package com.jobbootcamp.course.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteAllocationTest {

    @Test
    void allocatesProportionallyWithDocumentedExample() {
        List<RouteAllocation.Share> shares = RouteAllocation.allocate(48000, List.of(
                new RouteAllocation.Weight(1, 10000),
                new RouteAllocation.Weight(2, 20000),
                new RouteAllocation.Weight(3, 30000)));

        assertThat(shares).extracting(RouteAllocation.Share::allocatedCent)
                .containsExactly(8000L, 16000L, 24000L);
    }

    @Test
    void distributesRemainderByRemainderThenCourseId() {
        List<RouteAllocation.Share> shares = RouteAllocation.allocate(1000, List.of(
                new RouteAllocation.Weight(3, 990),
                new RouteAllocation.Weight(1, 990),
                new RouteAllocation.Weight(2, 990)));

        assertThat(shares).extracting(RouteAllocation.Share::allocatedCent)
                .containsExactly(333L, 334L, 333L);
    }
}