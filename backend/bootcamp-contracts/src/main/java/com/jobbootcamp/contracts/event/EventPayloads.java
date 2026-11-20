package com.jobbootcamp.contracts.event;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内部事件 payload 的受控字段读取。只用于本项目已知字段，不替代通用 JSON 解析。
 */
public final class EventPayloads {

    private EventPayloads() {
    }

    public static String stringField(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }
}