package com.jobbootcamp.assignment.application;

/**
 * 处理过程中发现租约已不属于当前 worker，结果必须丢弃而不是写入反馈。
 */
public class AiLeaseLostException extends RuntimeException {

    public AiLeaseLostException(Long taskId) {
        super("AI task " + taskId + " lease was lost");
    }
}