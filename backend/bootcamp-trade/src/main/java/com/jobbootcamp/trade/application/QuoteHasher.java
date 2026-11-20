package com.jobbootcamp.trade.application;

import com.jobbootcamp.contracts.internal.ProductSnapshotResponse;

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