package com.jobbootcamp.trade.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ErrorData;
import com.jobbootcamp.contracts.internal.AccessCheckResponse;
import com.jobbootcamp.contracts.internal.ProductSnapshotResponse;
import com.jobbootcamp.trade.domain.PriceQuote;
import com.jobbootcamp.trade.domain.dto.QuoteRequest;
import com.jobbootcamp.trade.domain.dto.QuoteResponse;
import com.jobbootcamp.trade.infrastructure.PriceQuoteMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class QuoteService {

    private static final long QUOTE_TTL_SECONDS = 300;
    private static final long MIN_PAID_CENT = 990;

    private final PriceQuoteMapper quoteMapper;
    private final CourseClient courseClient;
    private final LearningClient learningClient;

    public QuoteService(PriceQuoteMapper quoteMapper,
                        CourseClient courseClient,
                        LearningClient learningClient) {
        this.quoteMapper = quoteMapper;
        this.courseClient = courseClient;
        this.learningClient = learningClient;
    }

    public QuoteResponse create(Long userId, QuoteRequest request) {
        String productType = request.productType() == null ? "" : request.productType().trim().toUpperCase();
        if (!"COURSE".equals(productType)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "首版报价仅支持单课购买");
        }
        String couponMode = normalizeCouponMode(request.couponSelection());
        ProductSnapshotResponse snapshot = courseClient.courseSnapshot(request.productId());
        validateSaleable(snapshot);
        if (learningClient.checkAccess(userId, snapshot.id()).allowed()) {
            throw new BusinessException(ErrorCode.ALREADY_OWNED, "已拥有该课程",
                    new ErrorData(null, null, String.valueOf(snapshot.id())));
        }
        validateVideo(snapshot);

        long priceCent = snapshot.priceCent();
        String quoteId = UUID.randomUUID().toString();
        String quoteHash = QuoteHasher.quoteHash(snapshot, priceCent, 0, 0, priceCent, couponMode);
        String inputHash = QuoteHasher.inputHash(userId, productType, snapshot.id(), couponMode);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime expiresAt = now.plusSeconds(QUOTE_TTL_SECONDS);

        PriceQuote quote = new PriceQuote();
        quote.setQuoteId(quoteId);
        quote.setUserId(userId);
        quote.setProductType(productType);
        quote.setProductId(snapshot.id());
        quote.setProductVersion(snapshot.version());
        quote.setProductTitle(snapshot.title());
        quote.setUnitPriceCent(priceCent);
        quote.setTotalCent(priceCent);
        quote.setCreditCent(0L);
        quote.setDiscountCent(0L);
        quote.setPayableCent(priceCent);
        quote.setInputHash(inputHash);
        quote.setResultHash(quoteHash);
        quote.setStatus("READY");
        quote.setInputJson(inputJson(userId, productType, snapshot.id(), couponMode));
        quote.setResultJson(resultJson(quoteId, quoteHash, snapshot, priceCent, expiresAt));
        quote.setExpiresAt(expiresAt);
        quote.setCreatedAt(now);
        quote.setUpdatedAt(now);
        quoteMapper.insert(quote);
        return response(quote);
    }

    public QuoteResponse get(Long userId, String quoteId) {
        PriceQuote quote = loadOwned(userId, quoteId);
        if ("READY".equals(quote.getStatus())
                && quote.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            quoteMapper.updateStatusFromReady(quoteId, "EXPIRED", LocalDateTime.now(ZoneOffset.UTC));
            quote.setStatus("EXPIRED");
        }
        return response(quote);
    }

    private PriceQuote loadOwned(Long userId, String quoteId) {
        PriceQuote quote = quoteMapper.selectOne(Wrappers.<PriceQuote>lambdaQuery()
                .eq(PriceQuote::getQuoteId, quoteId));
        if (quote == null || !quote.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报价不存在");
        }
        return quote;
    }

    private void validateSaleable(ProductSnapshotResponse snapshot) {
        if (!"ONLINE".equals(snapshot.status())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "课程未上架，暂不能购买");
        }
        if (snapshot.free()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "免费课程请直接领取");
        }
        if (snapshot.priceCent() == null || snapshot.priceCent() < MIN_PAID_CENT) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "付费课程价格低于最低售价");
        }
    }

    private void validateVideo(ProductSnapshotResponse snapshot) {
        boolean hasVideo = snapshot.lessons() != null && snapshot.lessons().stream()
                .anyMatch(lesson -> lesson.mediaId() != null
                        && lesson.durationMs() != null && lesson.durationMs() > 0);
        if (!hasVideo) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "付费课程缺少可播放视频，不能创建订单");
        }
    }

    private String normalizeCouponMode(QuoteRequest.CouponSelection selection) {
        String mode = selection == null || selection.mode() == null
                ? "NONE"
                : selection.mode().trim().toUpperCase();
        List<Long> ids = selection == null || selection.ids() == null
                ? List.of()
                : selection.ids();
        if (!"NONE".equals(mode) || !ids.isEmpty()) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "优惠券功能尚未开放，请使用 NONE 模式");
        }
        return mode;
    }

    private QuoteResponse response(PriceQuote quote) {
        QuoteResponse.Product product = new QuoteResponse.Product(
                quote.getProductType(), quote.getProductId(), quote.getProductTitle(), quote.getProductVersion());
        List<QuoteResponse.CourseLine> courses = List.of(new QuoteResponse.CourseLine(
                quote.getProductId(), quote.getUnitPriceCent(), quote.getTotalCent(), quote.getCreditCent(),
                null, null, null, quote.getPayableCent()));
        return new QuoteResponse(quote.getQuoteId(), quote.getStatus(), quote.getExpiresAt(),
                quote.getResultHash(), product, courses, List.of(),
                quote.getTotalCent(), quote.getCreditCent(), quote.getDiscountCent(), quote.getPayableCent(),
                List.of());
    }

    private String inputJson(Long userId, String productType, Long productId, String couponMode) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("userId", userId);
        root.put("productType", productType);
        root.put("productId", productId);
        root.put("couponMode", couponMode);
        root.put("selectedCouponIds", new ArrayList<>());
        return TradeJson.write(root);
    }

    private String resultJson(String quoteId, String quoteHash, ProductSnapshotResponse snapshot,
                              long priceCent, LocalDateTime expiresAt) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("quoteId", quoteId);
        root.put("quoteHash", quoteHash);
        root.put("productType", snapshot.type());
        root.put("productId", snapshot.id());
        root.put("productVersion", snapshot.version());
        root.put("priceCent", priceCent);
        root.put("payableCent", priceCent);
        root.put("expiresAt", expiresAt.toString());
        return TradeJson.write(root);
    }
}