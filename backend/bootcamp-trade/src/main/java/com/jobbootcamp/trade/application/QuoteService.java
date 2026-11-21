package com.jobbootcamp.trade.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ErrorData;
import com.jobbootcamp.contracts.internal.ProductSnapshotResponse;
import com.jobbootcamp.contracts.internal.RouteSnapshotResponse;
import com.jobbootcamp.contracts.internal.SourceSelectionItem;
import com.jobbootcamp.contracts.internal.SourceSelectionResponse;
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
import java.util.Objects;
import java.util.UUID;

/**
 * 5 分钟报价。商品、价格、权益和金额全部来自服务端快照，quoteHash 是用户确认内容的指纹；
 * 报价不占券、不占权益，真正下单时再重新校验。路线报价还会固化每门课的抵扣来源。
 */
@Service
public class QuoteService {

    private static final long QUOTE_TTL_SECONDS = 300;
    private static final long MIN_PAID_CENT = 990;
    private static final ObjectMapper JSON = new ObjectMapper();

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
        if ("COURSE".equals(productType)) {
            return createCourse(userId, request, productType);
        }
        if ("ROUTE".equals(productType)) {
            return createRoute(userId, request, productType);
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "报价仅支持单课或路线购买");
    }

    private QuoteResponse createCourse(Long userId, QuoteRequest request, String productType) {
        String couponMode = normalizeCouponMode(request.couponSelection());
        ProductSnapshotResponse snapshot = courseClient.courseSnapshot(request.productId());
        validateSaleable(snapshot);
        if (learningClient.checkAccess(userId, snapshot.id()).allowed()) {
            throw new BusinessException(ErrorCode.ALREADY_OWNED, "已拥有该课程",
                    new ErrorData(null, null, String.valueOf(snapshot.id())));
        }
        validateVideo(snapshot);

        long priceCent = snapshot.priceCent();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String quoteId = UUID.randomUUID().toString();
        String quoteHash = QuoteHasher.quoteHash(snapshot, priceCent, 0, 0, priceCent, couponMode);
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
        quote.setInputHash(QuoteHasher.inputHash(userId, productType, snapshot.id(), couponMode));
        quote.setResultHash(quoteHash);
        quote.setStatus("READY");
        quote.setInputJson(inputJson(userId, productType, snapshot.id(), couponMode));
        quote.setResultJson(resultJson(quoteId, quoteHash, snapshot, priceCent, now.plusSeconds(QUOTE_TTL_SECONDS)));
        quote.setExpiresAt(now.plusSeconds(QUOTE_TTL_SECONDS));
        quote.setCreatedAt(now);
        quote.setUpdatedAt(now);
        quoteMapper.insert(quote);
        return response(quote);
    }

    private QuoteResponse createRoute(Long userId, QuoteRequest request, String productType) {
        String couponMode = normalizeCouponMode(request.couponSelection());
        RouteSnapshotResponse route = courseClient.routeSnapshot(request.productId());
        validateRouteSaleable(route);
        List<Long> courseIds = route.members().stream().map(RouteSnapshotResponse.Member::courseId).toList();
        List<SourceSelectionItem> selected = learningClient.selectSources(userId, courseIds).items();
        Map<Long, SourceSelectionItem> sourceByCourse = new LinkedHashMap<>();
        for (SourceSelectionItem item : selected) {
            sourceByCourse.put(item.courseId(), item);
        }

        List<QuoteResponse.CourseLine> lines = new ArrayList<>();
        long credit = 0;
        for (RouteSnapshotResponse.Member member : route.members()) {
            long allocated = member.free() ? 0
                    : (member.allocatedCent() == null ? 0 : member.allocatedCent());
            SourceSelectionItem source = member.free() ? null : sourceByCourse.get(member.courseId());
            if (!member.free() && source == null) {
                validateRouteVideo(member);
            }
            long lineCredit = source == null ? 0 : allocated;
            credit += lineCredit;
            lines.add(new QuoteResponse.CourseLine(member.courseId(), member.unitPriceCent(), allocated, lineCredit,
                    source == null ? null : source.sourceId(),
                    source == null ? null : source.sourceGrantId(),
                    source == null ? null : source.sourceOrderId(),
                    allocated - lineCredit));
        }
        long total = route.priceCent();
        long payable = total - credit;
        if (payable <= 0) {
            throw new BusinessException(ErrorCode.ALREADY_OWNED,
                    "路线课程均已拥有，无需创建付费订单",
                    new ErrorData(null, null, String.valueOf(route.routeId())));
        }
        if (payable < 100) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "路线实付低于最低金额");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime expiresAt = now.plusSeconds(QUOTE_TTL_SECONDS);
        String quoteId = UUID.randomUUID().toString();
        String quoteHash = QuoteHasher.routeQuoteHash(route, lines, total, credit, 0, payable, couponMode);
        QuoteResponse response = new QuoteResponse(quoteId, "READY", expiresAt, quoteHash,
                new QuoteResponse.Product(productType, route.routeId(), route.title(), route.versionNo()),
                lines, List.of(), total, credit, 0, payable, List.of());

        PriceQuote quote = new PriceQuote();
        quote.setQuoteId(quoteId);
        quote.setUserId(userId);
        quote.setProductType(productType);
        quote.setProductId(route.routeId());
        quote.setProductVersion(route.versionNo());
        quote.setProductTitle(route.title());
        quote.setUnitPriceCent(total);
        quote.setTotalCent(total);
        quote.setCreditCent(credit);
        quote.setDiscountCent(0L);
        quote.setPayableCent(payable);
        quote.setInputHash(QuoteHasher.inputHash(userId, productType, route.routeId(), couponMode));
        quote.setResultHash(quoteHash);
        quote.setStatus("READY");
        quote.setInputJson(inputJson(userId, productType, route.routeId(), couponMode));
        quote.setResultJson(writeJson(RouteQuoteCache.from(response)));
        quote.setExpiresAt(expiresAt);
        quote.setCreatedAt(now);
        quote.setUpdatedAt(now);
        quoteMapper.insert(quote);
        return response;
    }

    public QuoteResponse get(Long userId, String quoteId) {
        PriceQuote quote = loadOwned(userId, quoteId);
        if ("READY".equals(quote.getStatus())
                && quote.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            quoteMapper.updateStatusFromReady(quoteId, "EXPIRED", LocalDateTime.now(ZoneOffset.UTC));
            quote.setStatus("EXPIRED");
        }
        if ("ROUTE".equals(quote.getProductType())) {
            return cachedRouteResponse(quote);
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

    private void validateRouteSaleable(RouteSnapshotResponse route) {
        if (!"ONLINE".equals(route.status())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "路线未上架，暂不能购买");
        }
        if (route.priceCent() == null || route.priceCent() <= 0
                || route.members() == null || route.members().size() < 2) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "路线商品快照不合法");
        }
    }

    private void validateRouteVideo(RouteSnapshotResponse.Member member) {
        boolean hasVideo = member.lessons() != null && member.lessons().stream()
                .anyMatch(lesson -> lesson.mediaId() != null
                        && lesson.durationMs() != null && lesson.durationMs() > 0);
        if (!hasVideo) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY,
                    "路线新购付费课程缺少可播放视频：" + member.title());
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

    private QuoteResponse cachedRouteResponse(PriceQuote quote) {
        try {
            RouteQuoteCache cached = JSON.readValue(quote.getResultJson(), RouteQuoteCache.class);
            return new QuoteResponse(cached.quoteId(), quote.getStatus(), quote.getExpiresAt(),
                    cached.quoteHash(), cached.product(), cached.courses(), List.of(),
                    cached.totalCent(), cached.creditCent(), cached.discountCent(), cached.payableCent(),
                    List.of());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "路线报价快照损坏");
        }
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

    private String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "路线报价序列化失败");
        }
    }

    /**
     * 路线报价缓存刻意不含 LocalDateTime，避免公共 ObjectMapper 依赖 JavaTime 模块；
     * 有效期由 price_quote.expires_at 单独保存。
     */
    public record RouteQuoteCache(
            String quoteId,
            String quoteHash,
            QuoteResponse.Product product,
            List<QuoteResponse.CourseLine> courses,
            long totalCent,
            long creditCent,
            long discountCent,
            long payableCent
    ) {
        private static RouteQuoteCache from(QuoteResponse response) {
            return new RouteQuoteCache(response.quoteId(), response.quoteHash(), response.product(),
                    response.courses(), response.totalCent(), response.creditCent(),
                    response.discountCent(), response.payableCent());
        }
    }
}