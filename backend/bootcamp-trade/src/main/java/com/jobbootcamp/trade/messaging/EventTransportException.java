package com.jobbootcamp.trade.messaging;

public class EventTransportException extends Exception {

    private final String code;

    public EventTransportException(String code, String message) {
        super(message);
        this.code = code;
    }

    public EventTransportException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}