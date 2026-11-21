package com.jobbootcamp.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ErrorData;
import com.jobbootcamp.user.domain.AuthSession;
import com.jobbootcamp.user.domain.RoleCode;
import com.jobbootcamp.user.domain.UserAccount;
import com.jobbootcamp.user.domain.dto.AdminUserItem;
import com.jobbootcamp.user.domain.dto.CreateAdminUserRequest;
import com.jobbootcamp.user.domain.dto.PageResponse;
import com.jobbootcamp.user.domain.dto.UpdateAdminUserRequest;
import com.jobbootcamp.user.infrastructure.AuthSessionMapper;
import com.jobbootcamp.user.infrastructure.RolePermissionMapper;
import com.jobbootcamp.user.infrastructure.UserAccountMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/**
 * 管理员账号管理只允许教师/管理员两类角色，并禁止操作者禁用自己或修改自己的角色。
 * 禁用或改角色会递增 token_version 并撤销该账号现有会话，防止旧权限继续生效。
 */
@Service
public class AdminUserService {

    private static final Set<RoleCode> MANAGED_ROLES = Set.of(RoleCode.TEACHER, RoleCode.ADMIN);
    private static final String USER_MANAGE = "user:manage";

    private final UserAccountMapper userMapper;
    private final AuthSessionMapper sessionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(UserAccountMapper userMapper,
                            AuthSessionMapper sessionMapper,
                            RolePermissionMapper rolePermissionMapper,
                            PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.sessionMapper = sessionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResponse<AdminUserItem> list(Long operatorId, String keyword, RoleCode role,
                                            int pageNumber, int pageSize) {
        requireUserManage(operatorId);
        int safePage = Math.max(pageNumber, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        LambdaQueryWrapper<UserAccount> query = Wrappers.<UserAccount>lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            query.and(wrapper -> wrapper.like(UserAccount::getUsername, trimmed)
                    .or()
                    .like(UserAccount::getNickname, trimmed));
        }
        if (role != null) {
            query.eq(UserAccount::getRoleCode, role.name());
        }
        query.orderByDesc(UserAccount::getId);
        Page<UserAccount> result = userMapper.selectPage(new Page<>(safePage, safeSize), query);
        List<AdminUserItem> items = result.getRecords().stream().map(this::toItem).toList();
        return new PageResponse<>(items, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Transactional
    public AdminUserItem create(Long operatorId, CreateAdminUserRequest request) {
        requireUserManage(operatorId);
        if (!MANAGED_ROLES.contains(request.role())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "只能创建教师或管理员账号");
        }
        validatePassword(request.password());
        Long existing = userMapper.selectCount(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getUsername, request.username()));
        if (existing != null && existing > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }
        LocalDateTime now = utcNow();
        UserAccount user = new UserAccount();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setRoleCode(request.role().name());
        user.setEnabled(true);
        user.setTokenVersion(0L);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setRevision(0L);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }
        return toItem(user);
    }

    @Transactional
    public AdminUserItem update(Long operatorId, Long targetId, UpdateAdminUserRequest request) {
        requireUserManage(operatorId);
        UserAccount target = userMapper.selectById(targetId);
        if (target == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        if (request.enabled() == null && request.role() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "至少提供一个要修改的字段");
        }
        boolean roleChanged = request.role() != null && !request.role().name().equals(target.getRoleCode());
        if (roleChanged && !MANAGED_ROLES.contains(request.role())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "只能设置为教师或管理员角色");
        }
        boolean disabled = Boolean.FALSE.equals(request.enabled()) && Boolean.TRUE.equals(target.getEnabled());
        if (targetId.equals(operatorId) && (disabled || roleChanged)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "不能禁用自己或修改自己的角色");
        }

        LocalDateTime now = utcNow();
        boolean revokeSessions = disabled || roleChanged;
        var update = Wrappers.<UserAccount>lambdaUpdate()
                .set(UserAccount::getUpdatedAt, now)
                .setSql("revision = revision + 1")
                .eq(UserAccount::getId, targetId)
                .eq(UserAccount::getRevision, request.revision());
        if (request.enabled() != null) {
            update.set(UserAccount::getEnabled, request.enabled());
        }
        if (roleChanged) {
            update.set(UserAccount::getRoleCode, request.role().name());
        }
        if (revokeSessions) {
            update.setSql("token_version = token_version + 1");
        }
        int updated = userMapper.update(null, update);
        if (updated == 0) {
            Long currentRevision = userMapper.selectById(targetId).getRevision();
            throw new BusinessException(ErrorCode.CONFLICT, "账号已在其他位置更新，请刷新后重试",
                    new ErrorData(null, currentRevision, null));
        }
        if (revokeSessions) {
            sessionMapper.update(null, Wrappers.<AuthSession>lambdaUpdate()
                    .set(AuthSession::getRevokedAt, now)
                    .set(AuthSession::getUpdatedAt, now)
                    .setSql("revision = revision + 1")
                    .eq(AuthSession::getUserId, targetId)
                    .isNull(AuthSession::getRevokedAt));
        }
        return toItem(userMapper.selectById(targetId));
    }

    private void requireUserManage(Long operatorId) {
        UserAccount operator = userMapper.selectById(operatorId);
        if (operator == null || !RoleCode.ADMIN.name().equals(operator.getRoleCode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "没有账号管理权限");
        }
        List<String> permissions = rolePermissionMapper.selectCodesByRole(operator.getRoleCode());
        if (!permissions.contains(USER_MANAGE)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "没有账号管理权限");
        }
    }

    private AdminUserItem toItem(UserAccount user) {
        return new AdminUserItem(
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getNickname(),
                user.getRoleCode(),
                Boolean.TRUE.equals(user.getEnabled()),
                user.getRevision(),
                user.getCreatedAt().toInstant(ZoneOffset.UTC).toString());
    }

    private void validatePassword(String password) {
        int length = password.getBytes(StandardCharsets.UTF_8).length;
        if (length < 8 || length > 72) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "密码长度需为 8-72 字节");
        }
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}