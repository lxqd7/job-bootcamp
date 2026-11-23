package com.jobbootcamp.interaction.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.ProfileBatchItem;
import com.jobbootcamp.interaction.domain.ExcellentReward;
import com.jobbootcamp.interaction.domain.ModerationAction;
import com.jobbootcamp.interaction.domain.Note;
import com.jobbootcamp.interaction.domain.dto.AdminExcellentRewardView;
import com.jobbootcamp.interaction.domain.dto.AdminModerationAuditView;
import com.jobbootcamp.interaction.domain.dto.PageResponse;
import com.jobbootcamp.interaction.infrastructure.ExcellentRewardMapper;
import com.jobbootcamp.interaction.infrastructure.ModerationActionMapper;
import com.jobbootcamp.interaction.infrastructure.NoteMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理员治理视图：只读取公开原创笔记的评优事实和审计记录，不开放私人笔记正文浏览。
 */
@Service
public class AdminGovernanceService {

    private final ExcellentRewardMapper rewardMapper;
    private final ModerationActionMapper moderationMapper;
    private final NoteMapper noteMapper;
    private final InteractionAccessClient accessClient;

    public AdminGovernanceService(ExcellentRewardMapper rewardMapper,
                                  ModerationActionMapper moderationMapper,
                                  NoteMapper noteMapper,
                                  InteractionAccessClient accessClient) {
        this.rewardMapper = rewardMapper;
        this.moderationMapper = moderationMapper;
        this.noteMapper = noteMapper;
        this.accessClient = accessClient;
    }

    public PageResponse<AdminExcellentRewardView> rewards(String state, int page, int pageSize) {
        String normalized = state == null || state.isBlank() ? "" : state.trim().toUpperCase();
        if (!normalized.isEmpty() && !"ACTIVE".equals(normalized) && !"REVOKED".equals(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "state 只能是 ACTIVE/REVOKED");
        }
        long pageNumber = Math.max(1, page);
        long size = Math.min(100, Math.max(1, pageSize));
        IPage<ExcellentReward> result = rewardMapper.selectPage(new Page<>(pageNumber, size),
                Wrappers.<ExcellentReward>lambdaQuery()
                        .eq(!normalized.isEmpty(), ExcellentReward::getRevoked, "REVOKED".equals(normalized))
                        .orderByDesc(ExcellentReward::getAwardedAt)
                        .orderByDesc(ExcellentReward::getNoteId));
        List<Long> noteIds = result.getRecords().stream().map(ExcellentReward::getNoteId).toList();
        Map<Long, Note> notes = noteIds.isEmpty() ? Map.of() : noteMapper.selectBatchIds(noteIds).stream()
                .collect(Collectors.toMap(Note::getId, Function.identity()));
        List<Long> userIds = result.getRecords().stream()
                .flatMap(reward -> java.util.stream.Stream.of(reward.getAuthorId(), reward.getTeacherId()))
                .distinct().toList();
        Map<Long, ProfileBatchItem> profiles = accessClient.profiles(userIds);
        List<AdminExcellentRewardView> items = result.getRecords().stream()
                .map(reward -> view(reward, notes.get(reward.getNoteId()), profiles))
                .toList();
        return new PageResponse<>(items, result.getTotal(), pageNumber, size);
    }

    public PageResponse<AdminModerationAuditView> audits(String action, int page, int pageSize) {
        String normalized = action == null || action.isBlank() ? null : action.trim().toUpperCase();
        long pageNumber = Math.max(1, page);
        long size = Math.min(100, Math.max(1, pageSize));
        IPage<ModerationAction> result = moderationMapper.selectPage(new Page<>(pageNumber, size),
                Wrappers.<ModerationAction>lambdaQuery()
                        .eq(normalized != null, ModerationAction::getAction, normalized)
                        .orderByDesc(ModerationAction::getCreatedAt)
                        .orderByDesc(ModerationAction::getId));
        Map<Long, ProfileBatchItem> profiles = accessClient.profiles(result.getRecords().stream()
                .map(ModerationAction::getAdminId).distinct().toList());
        List<AdminModerationAuditView> items = result.getRecords().stream()
                .map(item -> new AdminModerationAuditView(item.getId(), item.getTargetType(), item.getTargetId(),
                        item.getAction(), item.getAdminId(), display(item.getAdminId(), profiles),
                        item.getReason(), item.getCreatedAt()))
                .toList();
        return new PageResponse<>(items, result.getTotal(), pageNumber, size);
    }

    private AdminExcellentRewardView view(ExcellentReward reward, Note note,
                                          Map<Long, ProfileBatchItem> profiles) {
        String excerpt = note == null ? "笔记已不可用" : excerpt(note.getBody());
        return new AdminExcellentRewardView(reward.getNoteId(), note == null ? null : note.getCourseId(),
                excerpt, note == null ? null : note.getState(),
                note != null && Boolean.TRUE.equals(note.getExcellent()),
                reward.getAuthorId(), display(reward.getAuthorId(), profiles),
                reward.getTeacherId(), display(reward.getTeacherId(), profiles),
                reward.getContentRevision(), reward.getAwardedAt(), Boolean.TRUE.equals(reward.getRevoked()),
                reward.getRevokeReason(), reward.getRevokedAt());
    }

    private String excerpt(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 160 ? body.substring(0, 160) : body;
    }

    private String display(Long userId, Map<Long, ProfileBatchItem> profiles) {
        ProfileBatchItem profile = profiles.get(userId);
        return profile == null || profile.nickname() == null || profile.nickname().isBlank()
                ? "用户 #" + userId : profile.nickname();
    }
}