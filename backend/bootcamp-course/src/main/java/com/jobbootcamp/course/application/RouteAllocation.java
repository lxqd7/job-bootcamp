package com.jobbootcamp.course.application;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 按单课价格权重把路线价格分摊到各付费课程；免费课程由调用方排除。尾差按余数降序、课程 ID 升序分配。
 */
public final class RouteAllocation {

    private RouteAllocation() {
    }

    public record Weight(long courseId, long unitPriceCent) {
    }

    public record Share(long courseId, long unitPriceCent, long allocatedCent) {
    }

    public static List<Share> allocate(long totalCent, List<Weight> weights) {
        if (totalCent < 0) {
            throw new IllegalArgumentException("totalCent must not be negative");
        }
        if (weights.isEmpty()) {
            throw new IllegalArgumentException("weights must not be empty");
        }
        long sum = 0;
        for (Weight weight : weights) {
            if (weight.unitPriceCent() <= 0) {
                throw new IllegalArgumentException("unit price must be positive");
            }
            sum += weight.unitPriceCent();
        }
        BigInteger total = BigInteger.valueOf(totalCent);
        BigInteger weightSum = BigInteger.valueOf(sum);
        long[] base = new long[weights.size()];
        long[] remainder = new long[weights.size()];
        long allocated = 0;
        for (int i = 0; i < weights.size(); i++) {
            Weight weight = weights.get(i);
            BigInteger product = total.multiply(BigInteger.valueOf(weight.unitPriceCent()));
            base[i] = product.divide(weightSum).longValueExact();
            remainder[i] = product.remainder(weightSum).longValueExact();
            allocated += base[i];
        }
        long left = totalCent - allocated;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < weights.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator
                .comparingLong((Integer i) -> remainder[i]).reversed()
                .thenComparingLong(i -> weights.get(i).courseId()));
        for (int i = 0; i < left; i++) {
            base[order.get(i)]++;
        }
        List<Share> shares = new ArrayList<>();
        for (int i = 0; i < weights.size(); i++) {
            Weight weight = weights.get(i);
            shares.add(new Share(weight.courseId(), weight.unitPriceCent(), base[i]));
        }
        return shares;
    }
}