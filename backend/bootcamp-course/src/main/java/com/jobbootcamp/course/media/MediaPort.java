package com.jobbootcamp.course.media;

import java.time.Instant;

public interface MediaPort {

    MediaVerification verify(MediaRef media);

    MediaAuthorization authorize(MediaRef media, Long userId, Long authSessionId,
                                 String playSessionId, Instant expiresAt);
}