package com.jobbootcamp.marketing.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.marketing.domain.CouponRule;
import com.jobbootcamp.marketing.domain.ExchangeProject;
import com.jobbootcamp.marketing.domain.dto.ExchangeProjectRequest;
import com.jobbootcamp.marketing.domain.dto.ExchangeProjectView;
import com.jobbootcamp.marketing.domain.dto.PageResponse;
import com.jobbootcamp.marketing.infrastructure.CouponRuleMapper;
import com.jobbootcamp.marketing.infrastructure.ExchangeProjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

/**
 * 兑换项目配置：规则版本、积分价、库存、每人累计限兑和开放窗口。
 * 上下架只改变可兑换状态，不清空 stock_used 或用户累计次数。
 */
@Service
public class ExchangeProjectService {

    private static final String ONLINE = "ONLINE";
    private static final String OFFLINE = "OFFLINE";

    private final ExchangeProjectMapper projectMapper;
    private final CouponRuleMapper ruleMapper;

    public ExchangeProjectService(ExchangeProjectMapper projectMapper, CouponRuleMapper ruleMapper) {
        this.projectMapper = projectMapper;
        this.ruleMapper = ruleMapper;
    }

    public PageResponse<ExchangeProjectView> list(String state, int page, int pageSize) {
        String normalized = state == null || state.isBlank() ? null : state.trim().toUpperCase(Locale.ROOT);
        if (normalized != null && !List.of(ONLINE, OFFLINE).contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "state 只能是 ONLINE/OFFLINE");
        }
        IPage<ExchangeProject> result = projectMapper.selectPage(
                new Page<>(Math.max(1, page), Math.min(100, Math.max(1, pageSize))),
                Wrappers.<ExchangeProject>lambdaQuery()
                        .eq(normalized != null, ExchangeProject::getState, normalized)
                        .orderByDesc(ExchangeProject::getId));
        return new PageResponse<>(result.getRecords().stream().map(this::view).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    public ExchangeProjectView get(Long id) {
        return view(require(id));
    }

    @Transactional
    public ExchangeProjectView create(ExchangeProjectRequest request) {
        Validated value = validate(request);
        ExchangeProject project = new ExchangeProject();
        LocalDateTime now = now();
        project.setName(value.name());
        project.setRuleId(value.ruleId());
        project.setPointsPrice(value.pointsPrice());
        project.setStockTotal(value.stockTotal());
        project.setStockUsed(0);
        project.setPerUserLimit(value.perUserLimit());
        project.setState(OFFLINE);
        project.setOpenAt(value.openAt());
        project.setCloseAt(value.closeAt());
        project.setRevision(0L);
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        projectMapper.insert(project);
        return view(project);
    }

    @Transactional
    public ExchangeProjectView update(Long id, ExchangeProjectRequest request) {
        ExchangeProject project = projectMapper.selectForUpdate(id);
        if (project == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "兑换项目不存在");
        }
        requireRevision(project, request == null ? null : request.revision());
        Validated value = validate(request);
        if (value.stockTotal() < project.getStockUsed()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "库存不能低于已兑换数量");
        }
        project.setName(value.name());
        project.setRuleId(value.ruleId());
        project.setPointsPrice(value.pointsPrice());
        project.setStockTotal(value.stockTotal());
        project.setPerUserLimit(value.perUserLimit());
        project.setOpenAt(value.openAt());
        project.setCloseAt(value.closeAt());
        project.setUpdatedAt(now());
        project.setRevision(project.getRevision() + 1);
        projectMapper.updateById(project);
        return view(project);
    }

    @Transactional
    public ExchangeProjectView changeState(Long id, String target, Long revision) {
        ExchangeProject project = projectMapper.selectForUpdate(id);
        if (project == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "兑换项目不存在");
        }
        requireRevision(project, revision);
        String normalized = target == null ? "" : target.trim().toUpperCase(Locale.ROOT);
        if (!List.of(ONLINE, OFFLINE).contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "兑换项目状态不合法");
        }
        if (normalized.equals(project.getState())) {
            return view(project);
        }
        if (ONLINE.equals(normalized) && !project.getCloseAt().isAfter(now())) {
            throw new BusinessException(ErrorCode.CONFLICT, "兑换项目已结束，不能上架");
        }
        project.setState(normalized);
        project.setUpdatedAt(now());
        project.setRevision(project.getRevision() + 1);
        projectMapper.updateById(project);
        return view(project);
    }

    private Validated validate(ExchangeProjectRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "兑换项目参数缺失");
        }
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty() || name.length() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "项目名称长度必须为 1～100");
        }
        if (request.ruleId() == null || request.ruleId() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "必须选择券规则");
        }
        CouponRule rule = ruleMapper.selectById(request.ruleId());
        if (rule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "券规则不存在");
        }
        int price = request.pointsPrice() == null ? 0 : request.pointsPrice();
        if (price <= 0 || price > 1_000_000) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "积分价格必须为 1～1000000");
        }
        int stock = request.stockTotal() == null ? 0 : request.stockTotal();
        if (stock < 0 || stock > 1_000_000) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "库存必须为 0～1000000");
        }
        int limit = request.perUserLimit() == null ? 0 : request.perUserLimit();
        if (limit <= 0 || limit > 1_000_000) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "每人限兑必须为 1～1000000");
        }
        LocalDateTime open = parseTime(request.openAt(), "openAt");
        LocalDateTime close = parseTime(request.closeAt(), "closeAt");
        if (!open.isBefore(close)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "结束时间必须晚于开始时间");
        }
        return new Validated(name, request.ruleId(), price, stock, limit, open, close);
    }

    private ExchangeProject require(Long id) {
        ExchangeProject project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "兑换项目不存在");
        }
        return project;
    }

    private void requireRevision(ExchangeProject project, Long expected) {
        if (expected == null || !expected.equals(project.getRevision())) {
            throw new BusinessException(ErrorCode.CONFLICT, "兑换项目已更新，请刷新后重试");
        }
    }

    private ExchangeProjectView view(ExchangeProject project) {
        CouponRule rule = ruleMapper.selectById(project.getRuleId());
        ExchangeProjectView.ExchangeRuleView ruleView = rule == null ? null
                : new ExchangeProjectView.ExchangeRuleView(rule.getId(), rule.getRuleKey(),
                rule.getVersionNo(), rule.getType(), rule.getScope(), rule.getTargetId(),
                rule.getValidityType(), rule.getValidDays());
        return new ExchangeProjectView(project.getId(), project.getName(), ruleView,
                project.getPointsPrice(), project.getStockTotal(), project.getStockUsed(),
                project.getPerUserLimit(), project.getState(), project.getOpenAt(), project.getCloseAt(),
                project.getRevision(), project.getCreatedAt(), project.getUpdatedAt());
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

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private record Validated(String name, Long ruleId, int pointsPrice, int stockTotal,
                             int perUserLimit, LocalDateTime openAt, LocalDateTime closeAt) {
    }
}