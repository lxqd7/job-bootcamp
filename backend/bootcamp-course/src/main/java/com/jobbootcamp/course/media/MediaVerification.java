package com.jobbootcamp.course.media;

public record MediaVerification(
        boolean available,
        Long durationMs,
        String errorCode,
        String errorMessage,
        String mediaUrl
) {
    public static MediaVerification ok(Long durationMs, String mediaUrl) {
        return new MediaVerification(true, durationMs, null, null, mediaUrl);
    }

    public static MediaVerification fail(String errorCode, String errorMessage) {
        return new MediaVerification(false, null, errorCode, errorMessage, null);
    }
}