package com.jobbootcamp.trade.application;

import com.jobbootcamp.contracts.internal.ProductSnapshotResponse;
import com.jobbootcamp.contracts.internal.RouteSnapshotResponse;
import com.jobbootcamp.trade.domain.dto.QuoteResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报价哈希的 canonical 输入：固定字段顺序与数组顺序，避免 Map 顺序导致同一报价产生不同哈希。
 */
public final class QuoteHasher {

    private QuoteHasher() {
    }

    public static String inputHash(Long userId, String productType, Long productId, String couponMode) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("userId", userId);
        root.put("productType", productType);
        root.put("productId", productId);
        root.put("couponMode", couponMode);
        return sha256(TradeJson.write(root));
    }

    public static String snapshotHash(ProductSnapshotResponse snapshot) {
        return sha256(TradeJson.write(snapshotRoot(snapshot)));
    }

    public static String quoteHash(ProductSnapshotResponse snapshot, long totalCent, long creditCent,
                                   long discountCent, long payableCent, String couponMode) {
        Map<String, Object> root = snapshotRoot(snapshot);
        root.put("couponMode", couponMode);
        root.put("totalCent", totalCent);
        root.put("creditCent", creditCent);
        root.put("discountCent", discountCent);
        root.put("payableCent", payableCent);
        return sha256(TradeJson.write(root));
    }

    public static String requestHash(String quoteId, String quoteHash, List<Long> selectedCouponIds) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("quoteId", quoteId);
        root.put("quoteHash", quoteHash);
        List<Long> ids = selectedCouponIds == null ? new ArrayList<>() : new ArrayList<>(selectedCouponIds);
        ids.sort(Long::compareTo);
        root.put("selectedCouponIds", ids);
        return sha256(TradeJson.write(root));
    }

    /**
     * 把券定价结果绑定到基础报价指纹；基础金额或优惠计划任一变化都会改变最终 quoteHash。
     */
    public static String withPricing(String baseHash, String pricingHash) {
        if (pricingHash == null || pricingHash.isBlank()) {
            return baseHash;
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("baseHash", baseHash);
        root.put("pricingHash", pricingHash);
        return sha256(TradeJson.write(root));
    }

    public static String routeSnapshotHash(RouteSnapshotResponse snapshot) {
        return sha256(TradeJson.write(routeRoot(snapshot)));
    }

    public static String routeQuoteHash(RouteSnapshotResponse snapshot, List<QuoteResponse.CourseLine> lines,
                                        long totalCent, long creditCent, long discountCent,
                                        long payableCent, String couponMode) {
        Map<String, Object> root = routeRoot(snapshot);
        root.put("couponMode", couponMode);
        List<Map<String, Object>> lineValues = new ArrayList<>();
        for (QuoteResponse.CourseLine line : lines) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("courseId", line.courseId());
            value.put("unitPriceCent", line.unitPriceCent());
            value.put("allocatedCent", line.allocatedCent());
            value.put("creditCent", line.creditCent());
            value.put("sourceId", line.sourceId());
            value.put("sourceGrantId", line.sourceGrantId());
            value.put("sourceOrderId", line.sourceOrderId());
            value.put("remainingCent", line.remainingCent());
            lineValues.add(value);
        }
        root.put("courses", lineValues);
        root.put("totalCent", totalCent);
        root.put("creditCent", creditCent);
        root.put("discountCent", discountCent);
        root.put("payableCent", payableCent);
        return sha256(TradeJson.write(root));
    }

    private static Map<String, Object> routeRoot(RouteSnapshotResponse snapshot) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "ROUTE");
        root.put("id", snapshot.routeId());
        root.put("title", snapshot.title());
        root.put("description", snapshot.description());
        root.put("versionNo", snapshot.versionNo());
        root.put("priceCent", snapshot.priceCent());
        root.put("status", snapshot.status());
        List<Map<String, Object>> members = new ArrayList<>();
        if (snapshot.members() != null) {
            for (RouteSnapshotResponse.Member member : snapshot.members()) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("courseId", member.courseId());
                value.put("title", member.title());
                value.put("courseVersion", member.courseVersion());
                value.put("epochId", member.epochId());
                value.put("unitPriceCent", member.unitPriceCent());
                value.put("allocatedCent", member.allocatedCent());
                value.put("free", member.free());
                value.put("categoryId", member.categoryId());
                value.put("categoryAncestorIds", member.categoryAncestorIds());
                List<Map<String, Object>> lessons = new ArrayList<>();
                if (member.lessons() != null) {
                    for (ProductSnapshotResponse.SnapshotLesson lesson : member.lessons()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("lessonId", lesson.lessonId());
                        row.put("chapterId", lesson.chapterId());
                        row.put("title", lesson.title());
                        row.put("sort", lesson.sort());
                        row.put("mediaId", lesson.mediaId());
                        row.put("durationMs", lesson.durationMs());
                        lessons.add(row);
                    }
                }
                value.put("lessons", lessons);
                members.add(value);
            }
        }
        root.put("members", members);
        return root;
    }

    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static Map<String, Object> snapshotRoot(ProductSnapshotResponse snapshot) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", snapshot.type());
        root.put("id", snapshot.id());
        root.put("title", snapshot.title());
        root.put("description", snapshot.description());
        root.put("version", snapshot.version());
        root.put("priceCent", snapshot.priceCent());
        root.put("free", snapshot.free());
        root.put("status", snapshot.status());
        root.put("categoryId", snapshot.categoryId());
        root.put("categoryAncestorIds", snapshot.categoryAncestorIds());
        List<Map<String, Object>> lessons = new ArrayList<>();
        if (snapshot.lessons() != null) {
            for (ProductSnapshotResponse.SnapshotLesson lesson : snapshot.lessons()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("lessonId", lesson.lessonId());
                row.put("chapterId", lesson.chapterId());
                row.put("title", lesson.title());
                row.put("sort", lesson.sort());
                row.put("mediaId", lesson.mediaId());
                row.put("durationMs", lesson.durationMs());
                lessons.add(row);
            }
        }
        root.put("lessons", lessons);
        return root;
    }
}