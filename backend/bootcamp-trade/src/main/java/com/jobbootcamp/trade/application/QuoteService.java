package com.jobbootcamp.trade.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ErrorData;
import com.jobbootcamp.contracts.internal.ProductSnapshotResponse;
import com.jobbootcamp.contracts.internal.PricingCalculateRequest;
import com.jobbootcamp.contracts.internal.PricingCalculateResponse;
import com.jobbootcamp.contracts.internal.PricingCouponAllocation;
import com.jobbootcamp.contracts.internal.PricingCouponPlan;
import com.jobbootcamp.contracts.internal.PricingCouponSelection;
import com.jobbootcamp.contracts.internal.PricingCourseAmount;
import com.jobbootcamp.contracts.internal.RouteSnapshotResponse;
import com.jobbootcamp.contracts.internal.SourceSelectionItem;
import com.jobbootcamp.trade.domain.PriceQuote;
import com.jobbootcamp.trade.domain.dto.QuoteRequest;
import com.jobbootcamp.trade.domain.dto.QuoteResponse;
import com.jobbootcamp.trade.infrastructure.PriceQuoteMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
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
    private final PricingClient pricingClient;

    public QuoteService(PriceQuoteMapper quoteMapper,
                        CourseClient courseClient,
                        LearningClient learningClient,
                        PricingClient pricingClient) {
        this.quoteMapper = quoteMapper;
        this.courseClient = courseClient;
        this.learningClient = learningClient;
        this.pricingClient = pricingClient;
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
        CouponChoice choice = normalizeCouponSelection(request.couponSelection());
        ProductSnapshotResponse snapshot = courseClient.courseSnapshot(request.productId());
        validateSaleable(snapshot);
        if (learningClient.checkAccess(userId, snapshot.id()).allowed()) {
            throw new BusinessException(ErrorCode.ALREADY_OWNED, "已拥有该课程",
                    new ErrorData(null, null, String.valueOf(snapshot.id())));
        }
        validateVideo(snapshot);

        long priceCent = snapshot.priceCent();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime expiresAt = now.plusSeconds(QUOTE_TTL_SECONDS);
        String quoteId = UUID.randomUUID().toString();
        String baseHash = QuoteHasher.quoteHash(snapshot, priceCent, 0, 0, priceCent, choice.mode());
        List<PricingCourseAmount> amounts = List.of(new PricingCourseAmount(
                snapshot.id(), priceCent, categoryIds(snapshot.categoryId(), snapshot.categoryAncestorIds())));
        PricingOutcome pricing = price(quoteId, userId, productType, snapshot.id(), null,
                priceCent, 0, amounts, choice);

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
        quote.setDiscountCent(pricing.discountCent());
        quote.setPayableCent(pricing.payableCent());
        quote.setInputHash(QuoteHasher.inputHash(userId, productType, snapshot.id(), choice.mode()));
        quote.setResultHash(pricing.resultHash(baseHash));
        quote.setStatus(pricing.status());
        quote.setCouponPlanJson(pricing.planJson());
        quote.setInputJson(inputJson(userId, productType, snapshot.id(), choice));
        quote.setResultJson(resultJson(quoteId, quote.getResultHash(), snapshot, priceCent,
                pricing.payableCent(), expiresAt));
        quote.setExpiresAt(expiresAt);
        quote.setCreatedAt(now);
        quote.setUpdatedAt(now);
        quoteMapper.insert(quote);
        return response(quote);
    }

    private QuoteResponse createRoute(Long userId, QuoteRequest request, String productType) {
        CouponChoice choice = normalizeCouponSelection(request.couponSelection());
        RouteSnapshotResponse route = courseClient.routeSnapshot(request.productId());
        validateRouteSaleable(route);
        List<Long> courseIds = route.members().stream().map(RouteSnapshotResponse.Member::courseId).toList();
        List<SourceSelectionItem> selected = learningClient.selectSources(userId, courseIds).items();
        Map<Long, SourceSelectionItem> sourceByCourse = new LinkedHashMap<>();
        for (SourceSelectionItem item : selected) {
            sourceByCourse.put(item.courseId(), item);
        }

        List<QuoteResponse.CourseLine> lines = new ArrayList<>();
        List<PricingCourseAmount> amounts = new ArrayList<>();
        long credit = 0;
        List<String> warnings = new ArrayList<>();
        for (RouteSnapshotResponse.Member member : route.members()) {
            long allocated = member.free() ? 0
                    : (member.allocatedCent() == null ? 0 : member.allocatedCent());
            SourceSelectionItem source = member.free() ? null : sourceByCourse.get(member.courseId());
            if (!member.free() && source == null) {
                validateRouteVideo(member);
            }
            long lineCredit = source == null ? 0 : allocated;
            credit += lineCredit;
            long remaining = allocated - lineCredit;
            lines.add(new QuoteResponse.CourseLine(member.courseId(), member.unitPriceCent(), allocated, lineCredit,
                    source == null ? null : source.sourceId(),
                    source == null ? null : source.sourceGrantId(),
                    source == null ? null : source.sourceOrderId(),
                    remaining));
            amounts.add(new PricingCourseAmount(member.courseId(), remaining,
                    categoryIds(member.categoryId(), member.categoryAncestorIds())));
            if (source != null && source.sourceOrderId() != null) {
                warnings.add("课程 " + member.courseId() + " 使用订单 "
                        + source.sourceOrderId() + " 的来源，退款该来源可能影响本路线权益");
            }
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
        String baseHash = QuoteHasher.routeQuoteHash(route, lines, total, credit, 0, payable, choice.mode());
        PricingOutcome pricing = price(quoteId, userId, productType, route.routeId(), route.routeId(),
                total, credit, amounts, choice);
        List<QuoteResponse.CourseLine> pricedLines = applyCouponToLines(lines, pricing.plan());
        if (!pricing.plan().isEmpty()) {
            warnings.add("优惠券仅用于报价预览，下单时才重新校验并锁定");
        }
        QuoteResponse response = new QuoteResponse(quoteId, pricing.status(), expiresAt,
                pricing.resultHash(baseHash),
                new QuoteResponse.Product(productType, route.routeId(), route.title(), route.versionNo()),
                pricedLines, pricing.plan(), total, credit, pricing.discountCent(), pricing.payableCent(), warnings);

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
        quote.setDiscountCent(pricing.discountCent());
        quote.setPayableCent(pricing.payableCent());
        quote.setInputHash(QuoteHasher.inputHash(userId, productType, route.routeId(), choice.mode()));
        quote.setResultHash(pricing.resultHash(baseHash));
        quote.setStatus(pricing.status());
        quote.setCouponPlanJson(pricing.planJson());
        quote.setInputJson(inputJson(userId, productType, route.routeId(), choice));
        quote.setResultJson(writeJson(RouteQuoteCache.from(response)));
        quote.setExpiresAt(expiresAt);
        quote.setCreatedAt(now);
        quote.setUpdatedAt(now);
        quoteMapper.insert(quote);
        return response;
    }

    public QuoteResponse get(Long userId, String quoteId) {
        PriceQuote quote = loadOwned(userId, quoteId);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (("READY".equals(quote.getStatus()) || "CALCULATING".equals(quote.getStatus()))
                && quote.getExpiresAt().isBefore(now)) {
            quoteMapper.updateStatus(quoteId, quote.getStatus(), "EXPIRED", now);
            quote.setStatus("EXPIRED");
        }
        if ("CALCULATING".equals(quote.getStatus())) {
            quote = refreshPricing(quote);
        }
        if ("ROUTE".equals(quote.getProductType())) {
            return cachedRouteResponse(quote);
        }
        return response(quote);
    }

    private PriceQuote refreshPricing(PriceQuote quote) {
        PricingCalculateResponse result = pricingClient.status(quote.getQuoteId(), quote.getUserId());
        if (result == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "营销定价服务返回为空");
        }
        if ("CALCULATING".equals(result.status())) {
            return quote;
        }
        if ("FAILED".equals(result.status())) {
            quoteMapper.updatePricingResult(quote.getQuoteId(), "FAILED", quote.getDiscountCent(),
                    quote.getPayableCent(), quote.getResultHash(), null, quote.getResultJson(),
                    LocalDateTime.now(ZoneOffset.UTC));
            throw pricingFailure(result);
        }
        applyPricing(quote, result);
        return quote;
    }

    private void applyPricing(PriceQuote quote, PricingCalculateResponse result) {
        List<QuoteResponse.CouponLine> plan = toCouponLines(result.couponPlan());
        String planJson = plan.isEmpty() ? null : writeJson(plan);
        String resultHash = QuoteHasher.withPricing(quote.getResultHash(), result.pricingHash());
        String resultJson = quote.getResultJson();
        if ("ROUTE".equals(quote.getProductType())) {
            resultJson = pricedRouteJson(quote, result, plan, resultHash);
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        quoteMapper.updatePricingResult(quote.getQuoteId(), "READY", result.discountCent(),
                result.payableCent(), resultHash, planJson, resultJson, now);
        quote.setStatus("READY");
        quote.setDiscountCent(result.discountCent());
        quote.setPayableCent(result.payableCent());
        quote.setResultHash(resultHash);
        quote.setCouponPlanJson(planJson);
        quote.setResultJson(resultJson);
        quote.setUpdatedAt(now);
    }

    private String pricedRouteJson(PriceQuote quote, PricingCalculateResponse result,
                                   List<QuoteResponse.CouponLine> plan, String resultHash) {
        try {
            RouteQuoteCache cached = JSON.readValue(quote.getResultJson(), RouteQuoteCache.class);
            List<String> warnings = new ArrayList<>(cached.refundDependencyWarnings() == null
                    ? List.of() : cached.refundDependencyWarnings());
            if (!plan.isEmpty() && !warnings.contains("优惠券仅用于报价预览，下单时才重新校验并锁定")) {
                warnings.add("优惠券仅用于报价预览，下单时才重新校验并锁定");
            }
            RouteQuoteCache updated = new RouteQuoteCache(cached.quoteId(), resultHash, cached.product(),
                    applyCouponToLines(cached.courses(), plan), cached.totalCent(), cached.creditCent(),
                    result.discountCent(), result.payableCent(), plan, warnings);
            return writeJson(updated);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "路线报价快照损坏");
        }
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

    private CouponChoice normalizeCouponSelection(QuoteRequest.CouponSelection selection) {
        String mode = selection == null || selection.mode() == null
                ? "NONE"
                : selection.mode().trim().toUpperCase();
        List<Long> ids = selection == null || selection.ids() == null
                ? List.of()
                : selection.ids().stream().filter(Objects::nonNull).toList();
        if (!"NONE".equals(mode) && !"AUTO".equals(mode) && !"MANUAL".equals(mode)) {
            throw new BusinessException(ErrorCode.COUPON_COMBINATION_INVALID, "优惠券模式仅支持 NONE/AUTO/MANUAL");
        }
        if ("NONE".equals(mode) && !ids.isEmpty()) {
            throw new BusinessException(ErrorCode.COUPON_COMBINATION_INVALID, "NONE 模式不能携带优惠券");
        }
        if ("AUTO".equals(mode) && !ids.isEmpty()) {
            throw new BusinessException(ErrorCode.COUPON_COMBINATION_INVALID, "AUTO 模式由系统选择优惠券");
        }
        if ("MANUAL".equals(mode)
                && (ids.isEmpty() || ids.size() > 3 || new HashSet<>(ids).size() != ids.size())) {
            throw new BusinessException(ErrorCode.COUPON_COMBINATION_INVALID, "手选优惠券需 1 至 3 张且不能重复");
        }
        return new CouponChoice(mode, List.copyOf(ids));
    }

    private List<Long> categoryIds(Long categoryId, List<Long> ancestors) {
        List<Long> result = new ArrayList<>();
        if (ancestors != null) {
            result.addAll(ancestors);
        }
        if (categoryId != null && !result.contains(categoryId)) {
            result.add(categoryId);
        }
        return result;
    }

    private PricingOutcome price(String quoteId, Long userId, String productType, Long productId, Long routeId,
                                 long totalCent, long creditCent, List<PricingCourseAmount> courses,
                                 CouponChoice choice) {
        long basePayable = totalCent - creditCent;
        if ("NONE".equals(choice.mode())) {
            return new PricingOutcome("READY", 0, basePayable, null, List.of());
        }
        PricingCalculateRequest request = new PricingCalculateRequest(quoteId, userId, productType, productId,
                routeId, totalCent, creditCent, courses,
                new PricingCouponSelection(choice.mode(), choice.ids()));
        PricingCalculateResponse result = pricingClient.calculate(request);
        if (result == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "营销定价服务返回为空");
        }
        if ("FAILED".equals(result.status())) {
            throw pricingFailure(result);
        }
        long discount = result.discountCent();
        long payable = "READY".equals(result.status()) ? result.payableCent() : basePayable;
        return new PricingOutcome(result.status(), discount, payable, result.pricingHash(),
                toCouponLines(result.couponPlan()));
    }

    private BusinessException pricingFailure(PricingCalculateResponse result) {
        ErrorCode code;
        try {
            code = ErrorCode.valueOf(result.errorCode());
        } catch (RuntimeException ignored) {
            code = ErrorCode.COUPON_COMBINATION_INVALID;
        }
        String message = result.errorMessage() == null ? "优惠券组合不可用" : result.errorMessage();
        return new BusinessException(code, message);
    }

    private List<QuoteResponse.CouponLine> toCouponLines(List<PricingCouponPlan> plans) {
        if (plans == null) {
            return List.of();
        }
        List<QuoteResponse.CouponLine> result = new ArrayList<>();
        for (PricingCouponPlan plan : plans) {
            List<QuoteResponse.Allocation> allocations = new ArrayList<>();
            if (plan.allocation() != null) {
                for (PricingCouponAllocation allocation : plan.allocation()) {
                    allocations.add(new QuoteResponse.Allocation(allocation.courseId(), allocation.discountCent()));
                }
            }
            result.add(new QuoteResponse.CouponLine(plan.userCouponId(), plan.applyOrder(), plan.baseCent(),
                    plan.discountCent(), allocations));
        }
        return result;
    }

    private List<QuoteResponse.CourseLine> applyCouponToLines(List<QuoteResponse.CourseLine> lines,
                                                             List<QuoteResponse.CouponLine> plan) {
        Map<Long, Long> discountByCourse = new LinkedHashMap<>();
        for (QuoteResponse.CouponLine coupon : plan) {
            for (QuoteResponse.Allocation allocation : coupon.allocation()) {
                discountByCourse.merge(allocation.courseId(), allocation.discountCent(), Long::sum);
            }
        }
        if (discountByCourse.isEmpty()) {
            return lines;
        }
        List<QuoteResponse.CourseLine> result = new ArrayList<>();
        for (QuoteResponse.CourseLine line : lines) {
            long discount = discountByCourse.getOrDefault(line.courseId(), 0L);
            result.add(new QuoteResponse.CourseLine(line.courseId(), line.unitPriceCent(), line.allocatedCent(),
                    line.creditCent(), line.sourceId(), line.sourceGrantId(), line.sourceOrderId(),
                    Math.max(0, line.remainingCent() - discount)));
        }
        return result;
    }

    private QuoteResponse response(PriceQuote quote) {
        QuoteResponse.Product product = new QuoteResponse.Product(
                quote.getProductType(), quote.getProductId(), quote.getProductTitle(), quote.getProductVersion());
        List<QuoteResponse.CourseLine> courses = List.of(new QuoteResponse.CourseLine(
                quote.getProductId(), quote.getUnitPriceCent(), quote.getTotalCent(), quote.getCreditCent(),
                null, null, null, quote.getPayableCent()));
        return new QuoteResponse(quote.getQuoteId(), quote.getStatus(), quote.getExpiresAt(),
                quote.getResultHash(), product, courses, parsePlan(quote.getCouponPlanJson()),
                quote.getTotalCent(), quote.getCreditCent(), quote.getDiscountCent(), quote.getPayableCent(),
                List.of());
    }

    private QuoteResponse cachedRouteResponse(PriceQuote quote) {
        try {
            RouteQuoteCache cached = JSON.readValue(quote.getResultJson(), RouteQuoteCache.class);
            return new QuoteResponse(cached.quoteId(), quote.getStatus(), quote.getExpiresAt(),
                    cached.quoteHash(), cached.product(), cached.courses(),
                    cached.couponPlan() == null ? List.of() : cached.couponPlan(),
                    cached.totalCent(), cached.creditCent(), cached.discountCent(), cached.payableCent(),
                    cached.refundDependencyWarnings() == null ? List.of() : cached.refundDependencyWarnings());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "路线报价快照损坏");
        }
    }

    private List<QuoteResponse.CouponLine> parsePlan(String planJson) {
        if (planJson == null || planJson.isBlank()) {
            return List.of();
        }
        try {
            return List.of(JSON.readValue(planJson, QuoteResponse.CouponLine[].class));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "报价优惠明细损坏");
        }
    }

    private String inputJson(Long userId, String productType, Long productId, CouponChoice choice) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("userId", userId);
        root.put("productType", productType);
        root.put("productId", productId);
        root.put("couponMode", choice.mode());
        root.put("selectedCouponIds", choice.ids());
        return TradeJson.write(root);
    }

    private String resultJson(String quoteId, String quoteHash, ProductSnapshotResponse snapshot,
                              long priceCent, long payableCent, LocalDateTime expiresAt) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("quoteId", quoteId);
        root.put("quoteHash", quoteHash);
        root.put("productType", snapshot.type());
        root.put("productId", snapshot.id());
        root.put("productVersion", snapshot.version());
        root.put("priceCent", priceCent);
        root.put("discountCent", priceCent - payableCent);
        root.put("payableCent", payableCent);
        root.put("expiresAt", expiresAt.toString());
        return TradeJson.write(root);
    }

    private static String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "路线报价序列化失败");
        }
    }

    private record CouponChoice(String mode, List<Long> ids) {
    }

    private record PricingOutcome(String status, long discountCent, long payableCent, String pricingHash,
                                  List<QuoteResponse.CouponLine> plan) {

        private String resultHash(String baseHash) {
            return "READY".equals(status) ? QuoteHasher.withPricing(baseHash, pricingHash) : baseHash;
        }

        private String planJson() {
            return plan.isEmpty() ? null : writeJson(plan);
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
            long payableCent,
            List<QuoteResponse.CouponLine> couponPlan,
            List<String> refundDependencyWarnings
    ) {
        private static RouteQuoteCache from(QuoteResponse response) {
            return new RouteQuoteCache(response.quoteId(), response.quoteHash(), response.product(),
                    response.courses(), response.totalCent(), response.creditCent(),
                    response.discountCent(), response.payableCent(), response.couponPlan(),
                    response.refundDependencyWarnings());
        }
    }
}