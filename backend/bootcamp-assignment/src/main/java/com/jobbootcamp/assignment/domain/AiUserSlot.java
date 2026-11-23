package com.jobbootcamp.assignment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ai_user_slot")
public class AiUserSlot {

    @TableId(type = IdType.INPUT)
    private Long userId;
    private Long activeTaskId;
    private LocalDateTime updatedAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getActiveTaskId() { return activeTaskId; }
    public void setActiveTaskId(Long activeTaskId) { this.activeTaskId = activeTaskId; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}