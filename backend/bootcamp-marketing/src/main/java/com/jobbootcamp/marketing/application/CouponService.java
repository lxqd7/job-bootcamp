package com.jobbootcamp.marketing.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.marketing.domain.Activity;
import com.jobbootcamp.marketing.domain.ActivityItem;
import com.jobbootcamp.marketing.domain.CouponRule;
import com.jobbootcamp.marketing.domain.ExchangeProject;
import com.jobbootcamp.marketing.domain.UserCoupon;
import com.jobbootcamp.marketing.domain.dto.ActivityItemRequest;
import com.jobbootcamp.marketing.domain.dto.ActivityRequest;
import com.jobbootcamp.marketing.domain.dto.ActivityView;
import com.jobbootcamp.marketing.domain.dto.CouponRuleRequest;
import com.jobbootcamp.marketing.domain.dto.CouponRuleView;
import com.jobbootcamp.marketing.domain.dto.PageResponse;
import com.jobbootcamp.marketing.domain.dto.UserCouponView;
import com.jobbootcamp.marketing.infrastructure.AcquisitionCounterMapper;
import com.jobbootcamp.marketing.infrastructure.AcquisitionMapper;
import com.jobbootcamp.marketing.infrastructure.ActivityItemMapper;
import com.jobbootcamp.marketing.infrastructure.ActivityMapper;
import com.jobbootcamp.marketing.infrastructure.CouponRuleMapper;
import com.jobbootcamp.marketing.infrastructure.ExchangeProjectMapper;
import com.jobbootcamp.marketing.infrastructure.UserCouponMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 券规则、活动、库存/限领和用户券。规则首次发券后核心字段不可改；
 * 领取在同一事务锁 activity→item→rule→counter，库存与个人限领都用条件更新。
 */
@Service
public class CouponService {

    private static final String ACTIVE = "ACTIVE";
    private static final String AVAILABLE = "AVAILABLE";
    private static final String LOCKED = "LOCKED";
    private static final String USED = "USED";
    private static final String ACTIVITY = "ACTIVITY";

    private final ActivityMapper activityMapper;
    private final ActivityItemMapper itemMapper;
    private final CouponRuleMapper ruleMapper;
    private final UserCouponMapper userCouponMapper;
    private final AcquisitionMapper acquisitionMapper;
    private final AcquisitionCounterMapper counterMapper;
    private final ExchangeProjectMapper exchangeProjectMapper;
    private final TransactionTemplate transactionTemplate;

    public CouponService(ActivityMapper activityMapper,
                         ActivityItemMapper itemMapper,
                         CouponRuleMapper ruleMapper,
                         UserCouponMapper userCouponMapper,
                         AcquisitionMapper acquisitionMapper,
                         AcquisitionCounterMapper counterMapper,
                         ExchangeProjectMapper exchangeProjectMapper,
                         PlatformTransactionManager transactionManager) {
        this.activityMapper = activityMapper;
        this.itemMapper = itemMapper;
        this.ruleMapper = ruleMapper;
        this.userCouponMapper = userCouponMapper;
        this.acquisitionMapper = acquisitionMapper;
        this.counterMapper = counterMapper;
        this.exchangeProjectMapper = exchangeProjectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PageResponse<CouponRuleView> listRules(int page, int pageSize) {
        IPage<CouponRule> result = ruleMapper.selectPage(page(page, pageSize),
                Wrappers.<CouponRule>lambdaQuery().orderByDesc(CouponRule::getId));
        return new PageResponse<>(result.getRecords().stream().map(this::toRuleView).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Transactional
    public CouponRuleView createRule(CouponRuleRequest request) {
        ValidatedRule validated = validateRule(request);
        int version = ruleMapper.maxVersion(validated.ruleKey()) + 1;
        CouponRule rule = new CouponRule();
        rule.setRuleKey(validated.ruleKey());
        rule.setVersionNo(version);
        applyRule(rule, validated);
        LocalDateTime now = now();
        rule.setIssued(false);
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        rule.setRevision(0L);
        ruleMapper.insert(rule);
        return toRuleView(rule);
    }

    @Transactional
    public CouponRuleView updateRule(Long id, CouponRuleRequest request) {
        CouponRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "券规则不存在");
        }
        if (Boolean.TRUE.equals(rule.getIssued())) {
            throw new BusinessException(ErrorCode.CONFLICT, "券规则已发放，不能直接修改，请创建新版本");
        }
        ValidatedRule validated = validateRule(request);
        if (!rule.getRuleKey().equals(validated.ruleKey())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "券规则键不可修改");
        }
        applyRule(rule, validated);
        rule.setUpdatedAt(now());
        rule.setRevision(rule.getRevision() + 1);
        ruleMapper.updateById(rule);
        return toRuleView(rule);
    }

    public PageResponse<ActivityView> listActivities(boolean admin, int page, int pageSize) {
        var query = Wrappers.<Activity>lambdaQuery();
        if (!admin) {
            LocalDateTime now = now();
            query.eq(Activity::getState, ACTIVE)
                    .le(Activity::getOpenAt, now)
                    .gt(Activity::getCloseAt, now);
        }
        query.orderByDesc(Activity::getId);
        IPage<Activity> result = activityMapper.selectPage(page(page, pageSize), query);
        return new PageResponse<>(result.getRecords().stream()
                .map(activity -> toActivityView(activity,
                        itemMapper.selectByActivity(activity.getId()).stream()
                                .filter(item -> admin || Boolean.TRUE.equals(item.getEnabled()))
                                .toList()))
                .toList(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    public ActivityView getActivity(Long id, boolean admin) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null || (!admin && !"ACTIVE".equals(activity.getState()))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
        }
        return toActivityView(activity, itemMapper.selectByActivity(id));
    }

    @Transactional
    public ActivityView createActivity(ActivityRequest request) {
        LocalDateTime open = parseTime(request.openAt(), "openAt");
        LocalDateTime close = parseTime(request.closeAt(), "closeAt");
        if (!open.isBefore(close)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "活动结束时间必须晚于开始时间");
        }
        Activity activity = new Activity();
        activity.setName(request.name().trim());
        activity.setState("DRAFT");
        activity.setOpenAt(open);
        activity.setCloseAt(close);
        activity.setCreatedAt(now());
        activity.setUpdatedAt(now());
        activity.setRevision(0L);
        activityMapper.insert(activity);
        return toActivityView(activity, List.of());
    }

    @Transactional
    public ActivityView updateActivity(Long id, ActivityRequest request) {
        Activity activity = requireActivity(id);
        LocalDateTime open = parseTime(request.openAt(), "openAt");
        LocalDateTime close = parseTime(request.closeAt(), "closeAt");
        if (!open.isBefore(close)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "活动结束时间必须晚于开始时间");
        }
        int updated = activityMapper.update(null, Wrappers.<Activity>lambdaUpdate()
                .set(Activity::getName, request.name().trim())
                .set(Activity::getOpenAt, open)
                .set(Activity::getCloseAt, close)
                .set(Activity::getUpdatedAt, now())
                .setSql("revision = revision + 1")
                .eq(Activity::getId, id)
                .eq(Activity::getRevision, request.revision()));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "活动已在其他位置更新，请刷新后重试");
        }
        return toActivityView(requireActivity(id), itemMapper.selectByActivity(id));
    }

    @Transactional
    public ActivityView addItem(Long activityId, ActivityItemRequest request) {
        Activity activity = requireActivity(activityId);
        if (!"DRAFT".equals(activity.getState())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有草稿活动可以新增券");
        }
        CouponRule rule = ruleMapper.selectById(request.ruleId());
        if (rule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "券规则不存在");
        }
        int stock = request.stockTotal() == null ? 0 : request.stockTotal();
        int limit = request.perUserLimit() == null ? 1 : request.perUserLimit();
        if (stock < 0 || limit <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "库存或限领数量不合法");
        }
        ActivityItem item = new ActivityItem();
        item.setActivityId(activityId);
        item.setRuleId(request.ruleId());
        item.setStockTotal(stock);
        item.setIssuedCount(0);
        item.setPerUserLimit(limit);
        item.setEnabled(request.enabled() == null || request.enabled());
        item.setCreatedAt(now());
        item.setUpdatedAt(now());
        item.setRevision(0L);
        try {
            itemMapper.insert(item);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "活动已包含该券规则");
        }
        return toActivityView(activity, itemMapper.selectByActivity(activityId));
    }

    @Transactional
    public ActivityView updateItem(Long itemId, ActivityItemRequest request) {
        ActivityItem item = itemMapper.selectForUpdate(itemId);
        if (item == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动券项不存在");
        }
        int stock = request.stockTotal() == null ? item.getStockTotal() : request.stockTotal();
        int limit = request.perUserLimit() == null ? item.getPerUserLimit() : request.perUserLimit();
        if (stock < item.getIssuedCount() || limit <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "库存不能低于已发数量，限领必须大于 0");
        }
        boolean enabled = request.enabled() == null ? Boolean.TRUE.equals(item.getEnabled()) : request.enabled();
        int updated = itemMapper.updateConfig(itemId, stock, limit, enabled, request.revision(), now());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "活动券项已在其他位置更新，请刷新后重试");
        }
        return toActivityView(requireActivity(item.getActivityId()),
                itemMapper.selectByActivity(item.getActivityId()));
    }

    @Transactional
    public ActivityView changeState(Long activityId, String target, Long revision) {
        Activity activity = requireActivity(activityId);
        String targetState = target == null ? "" : target.trim().toUpperCase(Locale.ROOT);
        if (!List.of(ACTIVE, "PAUSED", "ENDED").contains(targetState)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "活动状态不合法");
        }
        if (!canTransition(activity.getState(), targetState)) {
            throw new BusinessException(ErrorCode.CONFLICT, "活动当前状态不能切换到目标状态");
        }
        int updated = activityMapper.updateState(activityId, targetState, revision, now());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "活动已在其他位置更新，请刷新后重试");
        }
        return toActivityView(requireActivity(activityId), itemMapper.selectByActivity(activityId));
    }

    public UserCouponView claim(Long userId, String requestKey, Long itemId) {
        String key = requestKey == null || requestKey.isBlank() ? String.valueOf(itemId) : requestKey.trim();
        Long existingId = acquisitionMapper.findCouponId(userId, ACTIVITY, itemId, key);
        if (existingId != null) {
            return toCouponView(userCouponMapper.selectById(existingId));
        }
        try {
            UserCouponView view = transactionTemplate.execute(status ->
                    claimInTransaction(userId, itemId, key));
            if (view == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "领券未产生结果");
            }
            return view;
        } catch (DuplicateKeyException e) {
            Long raced = acquisitionMapper.findCouponId(userId, ACTIVITY, itemId, key);
            if (raced == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "领券请求正在处理，请稍后重试");
            }
            return toCouponView(userCouponMapper.selectById(raced));
        }
    }

    private UserCouponView claimInTransaction(Long userId, Long itemId, String requestKey) {
        ActivityItem item = itemMapper.selectForUpdate(itemId);
        if (item == null || !Boolean.TRUE.equals(item.getEnabled())) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "券当前不可领取");
        }
        Activity activity = activityMapper.selectForUpdate(item.getActivityId());
        LocalDateTime now = now();
        if (activity == null || !ACTIVE.equals(activity.getState())
                || now.isBefore(activity.getOpenAt()) || !now.isBefore(activity.getCloseAt())) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "活动未开放");
        }
        CouponRule rule = ruleMapper.selectForUpdate(item.getRuleId());
        if (rule == null) {
            throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "券规则不可用");
        }
        LocalDateTime validFrom;
        LocalDateTime validUntil;
        if ("FIXED".equals(rule.getValidityType())) {
            validFrom = rule.getValidFrom();
            validUntil = rule.getValidUntil();
            if (validUntil == null || !validUntil.isAfter(now)) {
                throw new BusinessException(ErrorCode.COUPON_UNAVAILABLE, "券已过有效期");
            }
        } else {
            validFrom = now;
            validUntil = now.plusDays(rule.getValidDays() == null ? 1 : rule.getValidDays());
        }

        counterMapper.insertIfAbsent(userId, ACTIVITY, itemId, now);
        Integer count = counterMapper.selectForUpdate(userId, ACTIVITY, itemId);
        if (count != null && count >= item.getPerUserLimit()) {
            throw new BusinessException(ErrorCode.ACQUISITION_LIMIT, "已达到该券领取上限");
        }
        if (itemMapper.incrementIssued(itemId, now) == 0) {
            throw new BusinessException(ErrorCode.STOCK_EXHAUSTED, "券库存不足");
        }
        counterMapper.increment(userId, ACTIVITY, itemId, now);
        if (!Boolean.TRUE.equals(rule.getIssued())) {
            ruleMapper.markIssued(rule.getId(), now);
        }

        UserCoupon coupon = new UserCoupon();
        coupon.setUserId(userId);
        coupon.setRuleId(rule.getId());
        coupon.setSourceType(ACTIVITY);
        coupon.setSourceGroupId(activity.getId());
        coupon.setSourceItemId(itemId);
        coupon.setValidFrom(validFrom);
        coupon.setValidUntil(validUntil);
        coupon.setStatus(AVAILABLE);
        coupon.setCreatedAt(now);
        coupon.setUpdatedAt(now);
        coupon.setRevision(0L);
        userCouponMapper.insert(coupon);
        acquisitionMapper.insert(userId, ACTIVITY, itemId, coupon.getId(), requestKey, now);
        return toCouponView(coupon);
    }

    public PageResponse<UserCouponView> myCoupons(Long userId, String displayStatus,
                                                   int page, int pageSize) {
        int safePage = Math.max(1, page);
        int size = Math.min(100, Math.max(1, pageSize));
        LocalDateTime now = now();
        var query = Wrappers.<UserCoupon>lambdaQuery().eq(UserCoupon::getUserId, userId);
        if (displayStatus != null && !displayStatus.isBlank()) {
            switch (displayStatus.toUpperCase(Locale.ROOT)) {
                case "LOCKED" -> query.eq(UserCoupon::getStatus, LOCKED);
                case "USED" -> query.eq(UserCoupon::getStatus, USED);
                case "EXPIRED" -> query.eq(UserCoupon::getStatus, AVAILABLE)
                        .le(UserCoupon::getValidUntil, now);
                case "NOT_STARTED" -> query.eq(UserCoupon::getStatus, AVAILABLE)
                        .gt(UserCoupon::getValidFrom, now);
                case "AVAILABLE" -> query.eq(UserCoupon::getStatus, AVAILABLE)
                        .le(UserCoupon::getValidFrom, now)
                        .gt(UserCoupon::getValidUntil, now);
                default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "券状态筛选不合法");
            }
        }
        query.orderByAsc(UserCoupon::getValidUntil).orderByAsc(UserCoupon::getId);
        IPage<UserCoupon> result = userCouponMapper.selectPage(new Page<>(safePage, size), query);
        return new PageResponse<>(result.getRecords().stream().map(this::toCouponView).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    private Activity requireActivity(Long id) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
        }
        return activity;
    }

    private boolean canTransition(String current, String target) {
        if (target.equals(current)) {
            return true;
        }
        return switch (current) {
            case "DRAFT", "PAUSED" -> ACTIVE.equals(target) || "ENDED".equals(target);
            case "ACTIVE" -> "PAUSED".equals(target) || "ENDED".equals(target);
            default -> false;
        };
    }

    private ActivityView toActivityView(Activity activity, List<ActivityItem> items) {
        List<ActivityView.ActivityItemView> views = new ArrayList<>();
        for (ActivityItem item : items) {
            CouponRule rule = ruleMapper.selectById(item.getRuleId());
            views.add(new ActivityView.ActivityItemView(item.getId(), item.getRuleId(),
                    rule == null ? null : toRuleView(rule), item.getStockTotal(), item.getIssuedCount(),
                    item.getPerUserLimit(), Boolean.TRUE.equals(item.getEnabled()), item.getRevision()));
        }
        return new ActivityView(activity.getId(), activity.getName(), activity.getState(),
                activity.getOpenAt(), activity.getCloseAt(), activity.getRevision(), views);
    }

    private CouponRuleView toRuleView(CouponRule rule) {
        return new CouponRuleView(rule.getId(), rule.getRuleKey(), rule.getVersionNo(), rule.getType(),
                rule.getScope(), rule.getTargetId(), rule.getThresholdCent(), rule.getAmountCent(),
                rule.getPayRateBps(), rule.getMaxDiscountCent(), rule.getValidityType(),
                rule.getValidFrom(), rule.getValidUntil(), rule.getValidDays(),
                Boolean.TRUE.equals(rule.getIssued()), rule.getRevision());
    }

    private UserCouponView toCouponView(UserCoupon coupon) {
        if (coupon == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户券不存在");
        }
        CouponRule rule = ruleMapper.selectById(coupon.getRuleId());
        LocalDateTime now = now();
        String display = coupon.getStatus();
        if (AVAILABLE.equals(coupon.getStatus())) {
            if (coupon.getValidFrom().isAfter(now)) {
                display = "NOT_STARTED";
            } else if (!coupon.getValidUntil().isAfter(now)) {
                display = "EXPIRED";
            }
        }
        String sourceName = null;
        if (ACTIVITY.equals(coupon.getSourceType()) && coupon.getSourceGroupId() != null) {
            Activity activity = activityMapper.selectById(coupon.getSourceGroupId());
            sourceName = activity == null ? null : activity.getName();
        } else if ("EXCHANGE".equals(coupon.getSourceType()) && coupon.getSourceGroupId() != null) {
            ExchangeProject project = exchangeProjectMapper.selectById(coupon.getSourceGroupId());
            sourceName = project == null ? null : project.getName();
        }
        return new UserCouponView(coupon.getId(), rule == null ? null : toRuleView(rule),
                coupon.getSourceType(), coupon.getSourceGroupId(), coupon.getSourceItemId(), sourceName,
                coupon.getValidFrom(), coupon.getValidUntil(), coupon.getStatus(), display,
                coupon.getLockedOrderId(), coupon.getUsedOrderId(), coupon.getCreatedAt());
    }

    private ValidatedRule validateRule(CouponRuleRequest request) {
        String ruleKey = request.ruleKey() == null ? "" : request.ruleKey().trim();
        if (ruleKey.isEmpty() || ruleKey.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "ruleKey 必填且不超过 64 字符");
        }
        String type = upper(request.type());
        String scope = upper(request.scope());
        if (!List.of("NO_THRESHOLD", "FULL_REDUCTION", "DISCOUNT").contains(type)
                || !List.of("ALL", "COURSE", "CATEGORY", "ROUTE").contains(scope)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "券类型或范围不合法");
        }
        if (!"ALL".equals(scope) && (request.targetId() == null || request.targetId() <= 0)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "非全场券必须指定 targetId");
        }
        long threshold = request.thresholdCent() == null ? 0 : request.thresholdCent();
        if (threshold < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "门槛金额不能为负");
        }
        Long amount = request.amountCent();
        Integer rate = request.payRateBps();
        Long maxDiscount = request.maxDiscountCent();
        if ("NO_THRESHOLD".equals(type) || "FULL_REDUCTION".equals(type)) {
            if (amount == null || amount <= 0 || rate != null || maxDiscount != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "满减/无门槛券参数不合法");
            }
            if ("NO_THRESHOLD".equals(type) && threshold != 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "无门槛券门槛必须为 0");
            }
            if ("FULL_REDUCTION".equals(type) && threshold == 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "满减券门槛必须大于 0");
            }
        } else {
            if (rate == null || rate <= 0 || rate >= 10000 || maxDiscount == null || maxDiscount <= 0
                    || amount != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "折扣券参数不合法");
            }
        }
        String validityType = upper(request.validityType());
        LocalDateTime from = null;
        LocalDateTime until = null;
        Integer days = request.validDays();
        if ("FIXED".equals(validityType)) {
            from = parseTime(request.validFrom(), "validFrom");
            until = parseTime(request.validUntil(), "validUntil");
            if (!from.isBefore(until)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "券有效期不合法");
            }
            days = null;
        } else if ("DAYS".equals(validityType)) {
            if (days == null || days < 1 || days > 3650) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "有效天数需为 1-3650");
            }
            from = null;
            until = null;
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "有效期类型不合法");
        }
        return new ValidatedRule(ruleKey, type, scope, request.targetId(), threshold, amount, rate,
                maxDiscount, validityType, from, until, days);
    }

    private void applyRule(CouponRule rule, ValidatedRule value) {
        rule.setType(value.type());
        rule.setScope(value.scope());
        rule.setTargetId(value.targetId());
        rule.setThresholdCent(value.thresholdCent());
        rule.setAmountCent(value.amountCent());
        rule.setPayRateBps(value.payRateBps());
        rule.setMaxDiscountCent(value.maxDiscountCent());
        rule.setValidityType(value.validityType());
        rule.setValidFrom(value.validFrom());
        rule.setValidUntil(value.validUntil());
        rule.setValidDays(value.validDays());
    }

    private LocalDateTime parseTime(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, field + " 必填");
        }
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, field + " 格式应为 ISO 本地时间");
        }
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private <T> Page<T> page(int page, int pageSize) {
        return new Page<>(Math.max(1, page), Math.min(100, Math.max(1, pageSize)));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private record ValidatedRule(String ruleKey, String type, String scope, Long targetId,
                                 Long thresholdCent, Long amountCent, Integer payRateBps,
                                 Long maxDiscountCent, String validityType, LocalDateTime validFrom,
                                 LocalDateTime validUntil, Integer validDays) {
    }
}