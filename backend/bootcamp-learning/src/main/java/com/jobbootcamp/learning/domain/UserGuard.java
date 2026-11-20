package com.jobbootcamp.learning.domain;

public class UserGuard {

    private Long userId;
    private Long sequence;
    private Long completionEventSequence;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getSequence() { return sequence; }
    public void setSequence(Long sequence) { this.sequence = sequence; }
    public Long getCompletionEventSequence() { return completionEventSequence; }
    public void setCompletionEventSequence(Long completionEventSequence) { this.completionEventSequence = completionEventSequence; }
}