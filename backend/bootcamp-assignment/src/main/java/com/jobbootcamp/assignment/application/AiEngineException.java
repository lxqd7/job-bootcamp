package com.jobbootcamp.assignment.application;

/**
 * 引擎已经完成错误分类，worker 只把安全类别写入 ai_task，不把供应商细节暴露给学生。
 */
public class AiEngineException extends RuntimeException {

    private final String safeCode;

    public AiEngineException(String safeCode, String message) {
        super(message);
        this.safeCode = safeCode;
    }

    public AiEngineException(String safeCode, String message, Throwable cause) {
        super(message, cause);
        this.safeCode = safeCode;
    }

    public String safeCode() {
        return safeCode;
    }
}