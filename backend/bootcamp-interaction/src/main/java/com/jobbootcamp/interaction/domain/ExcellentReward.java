package com.jobbootcamp.interaction.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 优秀笔记奖励事实。note_id 是主键，同一笔记最多一条；取消后重评只再次展示，不再产生新奖励。
 */
@TableName("excellent_reward")
public class ExcellentReward {

    @TableId(type = IdType.INPUT)
    private Long noteId;
    private Long authorId;
    private Long teacherId;
    private Long contentRevision;
    private LocalDateTime awardedAt;
    private String rewardEventId;
    private Long rewardVersion;
    private Boolean revoked;
    private String revokeReason;
    private LocalDateTime revokedAt;

    public Long getNoteId() { return noteId; }
    public void setNoteId(Long noteId) { this.noteId = noteId; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public Long getContentRevision() { return contentRevision; }
    public void setContentRevision(Long contentRevision) { this.contentRevision = contentRevision; }
    public LocalDateTime getAwardedAt() { return awardedAt; }
    public void setAwardedAt(LocalDateTime awardedAt) { this.awardedAt = awardedAt; }
    public String getRewardEventId() { return rewardEventId; }
    public void setRewardEventId(String rewardEventId) { this.rewardEventId = rewardEventId; }
    public Long getRewardVersion() { return rewardVersion; }
    public void setRewardVersion(Long rewardVersion) { this.rewardVersion = rewardVersion; }
    public Boolean getRevoked() { return revoked; }
    public void setRevoked(Boolean revoked) { this.revoked = revoked; }
    public String getRevokeReason() { return revokeReason; }
    public void setRevokeReason(String revokeReason) { this.revokeReason = revokeReason; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
}