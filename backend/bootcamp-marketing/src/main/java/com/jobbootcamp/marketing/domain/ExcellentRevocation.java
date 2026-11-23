package com.jobbootcamp.marketing.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("excellent_revocation")
public class ExcellentRevocation {

    @TableId(type = IdType.INPUT)
    private Long noteId;
    private String originalEventId;
    private String reason;
    private LocalDateTime occurredAt;
    private String state;
    private Long revokeLedgerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getNoteId() { return noteId; }
    public void setNoteId(Long noteId) { this.noteId = noteId; }
    public String getOriginalEventId() { return originalEventId; }
    public void setOriginalEventId(String originalEventId) { this.originalEventId = originalEventId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Long getRevokeLedgerId() { return revokeLedgerId; }
    public void setRevokeLedgerId(Long revokeLedgerId) { this.revokeLedgerId = revokeLedgerId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}