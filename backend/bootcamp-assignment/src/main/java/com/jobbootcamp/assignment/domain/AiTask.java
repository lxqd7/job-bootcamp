package com.jobbootcamp.assignment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ai_task")
public class AiTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long submissionId;
    private Long userId;
    private Long assignmentId;
    private String state;
    private Integer roundNo;
    private Integer callCount;
    private Boolean repairUsed;
    private LocalDateTime nextRetryAt;
    private LocalDateTime deadlineAt;
    private String leaseToken;
    private LocalDateTime leaseUntil;
    private String safeErrorCode;
    private String promptVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getAssignmentId() { return assignmentId; }
    public void setAssignmentId(Long assignmentId) { this.assignmentId = assignmentId; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Integer getRoundNo() { return roundNo; }
    public void setRoundNo(Integer roundNo) { this.roundNo = roundNo; }
    public Integer getCallCount() { return callCount; }
    public void setCallCount(Integer callCount) { this.callCount = callCount; }
    public Boolean getRepairUsed() { return repairUsed; }
    public void setRepairUsed(Boolean repairUsed) { this.repairUsed = repairUsed; }
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public LocalDateTime getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(LocalDateTime deadlineAt) { this.deadlineAt = deadlineAt; }
    public String getLeaseToken() { return leaseToken; }
    public void setLeaseToken(String leaseToken) { this.leaseToken = leaseToken; }
    public LocalDateTime getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(LocalDateTime leaseUntil) { this.leaseUntil = leaseUntil; }
    public String getSafeErrorCode() { return safeErrorCode; }
    public void setSafeErrorCode(String safeErrorCode) { this.safeErrorCode = safeErrorCode; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}