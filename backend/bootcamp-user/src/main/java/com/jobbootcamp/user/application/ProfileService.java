package com.jobbootcamp.user.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ErrorData;
import com.jobbootcamp.contracts.internal.ProfileBatchItem;
import com.jobbootcamp.user.domain.UserAccount;
import com.jobbootcamp.user.domain.dto.MeResponse;
import com.jobbootcamp.user.domain.dto.UpdateProfileRequest;
import com.jobbootcamp.user.infrastructure.RolePermissionMapper;
import com.jobbootcamp.user.infrastructure.UserAccountMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/**
 * 本人资料与权限快照。资料更新只允许昵称/头像，角色和权限始终由服务端按账号读取。
 */
@Service
public class ProfileService {

    private final UserAccountMapper userMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public ProfileService(UserAccountMapper userMapper, RolePermissionMapper rolePermissionMapper) {
        this.userMapper = userMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    public MeResponse profile(Long userId) {
        UserAccount user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        List<String> permissions = rolePermissionMapper.selectCodesByRole(user.getRoleCode());
        return new MeResponse(
                String.valueOf(user.getId()),
                user.getNickname(),
                user.getRoleCode(),
                user.getAvatarUrl(),
                permissions,
                user.getRevision());
    }

    public List<ProfileBatchItem> batchProfiles(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = userIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Long::valueOf)
                .distinct()
                .limit(100)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .map(user -> new ProfileBatchItem(
                        String.valueOf(user.getId()),
                        user.getNickname(),
                        user.getRoleCode(),
                        Boolean.TRUE.equals(user.getEnabled()),
                        user.getRevision() == null ? 0 : user.getRevision()))
                .toList();
    }

    @Transactional
    public MeResponse updateProfile(Long userId, UpdateProfileRequest request) {
        UserAccount current = userMapper.selectById(userId);
        if (current == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int updated = userMapper.update(null, Wrappers.<UserAccount>lambdaUpdate()
                .set(UserAccount::getNickname, request.nickname())
                .set(UserAccount::getAvatarUrl, request.avatarUrl())
                .set(UserAccount::getUpdatedAt, now)
                .setSql("revision = revision + 1")
                .eq(UserAccount::getId, userId)
                .eq(UserAccount::getRevision, request.revision()));
        if (updated == 0) {
            Long currentRevision = userMapper.selectById(userId).getRevision();
            throw new BusinessException(ErrorCode.CONFLICT, "资料已在其他位置更新，请刷新后重试",
                    new ErrorData(null, currentRevision, null));
        }
        return profile(userId);
    }
}