package com.jobbootcamp.course.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

@Component
public class DemoMediaAdapter implements MediaPort {

    private final Path root;
    private final DemoMediaSigner signer;

    public DemoMediaAdapter(@Value("${app.media.demo-root}") String demoRoot,
                            DemoMediaSigner signer) {
        this.root = Paths.get(demoRoot).toAbsolutePath().normalize();
        this.signer = signer;
    }

    @Override
    public MediaVerification verify(MediaRef media) {
        Path path = resolve(media.demoPath());
        if (path == null) {
            return MediaVerification.fail("DEMO_PATH_INVALID", "演示媒体路径不在允许目录内");
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            return MediaVerification.fail("DEMO_FILE_MISSING", "演示媒体文件不存在或不可读");
        }
        try {
            Long duration = Mp4DurationReader.readDurationMs(path);
            if (duration == null || duration <= 0) {
                return MediaVerification.fail("DEMO_DURATION_INVALID", "无法读取演示媒体的有效时长");
            }
            return MediaVerification.ok(duration, null);
        } catch (IOException e) {
            return MediaVerification.fail("DEMO_READ_ERROR", "读取演示媒体失败");
        }
    }

    @Override
    public MediaAuthorization authorize(MediaRef media, Long userId, Long authSessionId,
                                        String playSessionId, Instant expiresAt) {
        String nonce = UUID.randomUUID().toString();
        long expires = expiresAt.getEpochSecond();
        String signature = signer.sign(media.id(), userId, authSessionId, playSessionId, expires, nonce);
        String url = UriComponentsBuilder.fromPath("/media/demo/{id}")
                .queryParam("uid", userId)
                .queryParam("sid", authSessionId)
                .queryParam("psid", playSessionId)
                .queryParam("expires", expires)
                .queryParam("nonce", nonce)
                .queryParam("sig", signature)
                .buildAndExpand(media.id())
                .encode()
                .toUriString();
        return new MediaAuthorization("DEMO", playSessionId, expiresAt, null, null, null, url, media.durationMs());
    }

    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path candidate = root.resolve(relativePath).normalize();
        if (!candidate.startsWith(root)) {
            return null;
        }
        return candidate;
    }

    public Path root() {
        return root;
    }

    public DemoMediaSigner signer() {
        return signer;
    }
}