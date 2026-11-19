package com.jobbootcamp.course.controller;

import com.jobbootcamp.course.domain.MediaAsset;
import com.jobbootcamp.course.domain.PlaySession;
import com.jobbootcamp.course.infrastructure.MediaAssetMapper;
import com.jobbootcamp.course.infrastructure.PlaySessionMapper;
import com.jobbootcamp.course.media.DemoMediaAdapter;
import com.jobbootcamp.course.media.DemoMediaSigner;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/media/demo")
public class DemoMediaController {

    private final MediaAssetMapper mediaMapper;
    private final PlaySessionMapper playSessionMapper;
    private final DemoMediaAdapter demoMediaAdapter;
    private final DemoMediaSigner signer;

    public DemoMediaController(MediaAssetMapper mediaMapper,
                               PlaySessionMapper playSessionMapper,
                               DemoMediaAdapter demoMediaAdapter,
                               DemoMediaSigner signer) {
        this.mediaMapper = mediaMapper;
        this.playSessionMapper = playSessionMapper;
        this.demoMediaAdapter = demoMediaAdapter;
        this.signer = signer;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> play(@PathVariable("id") Long id,
                                  @RequestParam("uid") Long userId,
                                  @RequestParam("sid") Long authSessionId,
                                  @RequestParam("psid") String playSessionId,
                                  @RequestParam("expires") Long expires,
                                  @RequestParam("nonce") String nonce,
                                  @RequestParam("sig") String signature,
                                  @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) throws Exception {
        if (expires <= Instant.now().getEpochSecond()
                || !signer.matches(id, userId, authSessionId, playSessionId, expires, nonce, signature)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        PlaySession session = playSessionMapper.selectById(playSessionId);
        if (session == null
                || !session.getMediaId().equals(id)
                || !session.getUserId().equals(userId)
                || !session.getAuthSessionId().equals(authSessionId)
                || session.getExpiresAt().isBefore(java.time.LocalDateTime.now(ZoneOffset.UTC))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        MediaAsset media = mediaMapper.selectById(id);
        if (media == null || !"DEMO".equals(media.getProvider())
                || !"AVAILABLE".equals(media.getStatus())
                || Boolean.TRUE.equals(media.getDetached())) {
            return ResponseEntity.notFound().build();
        }
        Path path = demoMediaAdapter.resolve(media.getDemoPath());
        if (path == null || !Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(path);
        long length = resource.contentLength();
        MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        List<HttpRange> ranges;
        try {
            ranges = (rangeHeader == null || rangeHeader.isBlank())
                    ? List.of()
                    : HttpRange.parseRanges(rangeHeader);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + length)
                    .build();
        }
        if (ranges.isEmpty()) {
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(length)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(resource);
        }
        HttpRange range = ranges.get(0);
        long start;
        long end;
        try {
            start = range.getRangeStart(length);
            end = range.getRangeEnd(length);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + length)
                    .build();
        }
        long count = end - start + 1;
        FileInputStream input = new FileInputStream(path.toFile());
        input.getChannel().position(start);
        Resource region = new InputStreamResource(input) {
            @Override
            public long contentLength() {
                return count;
            }
        };
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(mediaType)
                .contentLength(count)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + length)
                .body(region);
    }
}