package com.jobbootcamp.course.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 读取 MP4 的 mvhd box 得到时长，供演示媒体校验使用；不依赖外部 ffprobe。
 */
public final class Mp4DurationReader {

    private Mp4DurationReader() {
    }

    public static Long readDurationMs(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        byte[] marker = {'m', 'v', 'h', 'd'};
        int index = indexOf(bytes, marker);
        if (index < 0 || index + 32 > bytes.length) {
            return null;
        }
        int payload = index + 4;
        int version = bytes[payload];
        long timescale;
        long duration;
        if (version == 0) {
            timescale = readUInt32(bytes, payload + 12);
            duration = readUInt32(bytes, payload + 16);
        } else {
            timescale = readUInt32(bytes, payload + 20);
            duration = readUInt64(bytes, payload + 24);
        }
        if (timescale <= 0 || duration <= 0) {
            return null;
        }
        return Math.round(duration * 1000.0 / timescale);
    }

    private static int indexOf(byte[] bytes, byte[] marker) {
        outer:
        for (int i = 0; i <= bytes.length - marker.length; i++) {
            for (int j = 0; j < marker.length; j++) {
                if (bytes[i + j] != marker[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static long readUInt32(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xff) << 24)
                | ((long) (bytes[offset + 1] & 0xff) << 16)
                | ((long) (bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }

    private static long readUInt64(byte[] bytes, int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (bytes[offset + i] & 0xff);
        }
        return value;
    }
}