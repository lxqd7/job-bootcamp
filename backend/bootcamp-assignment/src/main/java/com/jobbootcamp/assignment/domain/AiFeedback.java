package com.jobbootcamp.assignment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ai_feedback")
public class AiFeedback {

    @TableId(type = IdType.INPUT)
    private Long taskId;
    private String verdict;
    private String summary;
    private String issues;
    private String suggestions;
    private String limitations;
    private Boolean isMock;
    private LocalDateTime createdAt;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getIssues() { return issues; }
    public void setIssues(String issues) { this.issues = issues; }
    public String getSuggestions() { return suggestions; }
    public void setSuggestions(String suggestions) { this.suggestions = suggestions; }
    public String getLimitations() { return limitations; }
    public void setLimitations(String limitations) { this.limitations = limitations; }
    public Boolean getIsMock() { return isMock; }
    public void setIsMock(Boolean isMock) { this.isMock = isMock; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}