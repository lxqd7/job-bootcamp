package com.jobbootcamp.trade.application;

import java.util.Collection;
import java.util.Map;

/**
 * 固定顺序的极简 JSON 写出器，用于计算可复现的报价与快照哈希。
 */
public final class TradeJson {

    private TradeJson() {
    }

    public static String write(Object value) {
        StringBuilder builder = new StringBuilder();
        append(builder, value);
        return builder.toString();
    }

    private static void append(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
            return;
        }
        if (value instanceof String text) {
            appendString(builder, text);
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            builder.append(value);
            return;
        }
        if (value instanceof Enum<?> enumValue) {
            appendString(builder, enumValue.name());
            return;
        }
        if (value instanceof Map<?, ?> map) {
            builder.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                appendString(builder, String.valueOf(entry.getKey()));
                builder.append(':');
                append(builder, entry.getValue());
            }
            builder.append('}');
            return;
        }
        if (value instanceof Collection<?> collection) {
            builder.append('[');
            boolean first = true;
            for (Object item : collection) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                append(builder, item);
            }
            builder.append(']');
            return;
        }
        appendString(builder, String.valueOf(value));
    }

    private static void appendString(StringBuilder builder, String text) {
        builder.append('"');
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        builder.append('"');
    }
}